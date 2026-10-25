package dev.dimension.flare.feature.agent.runtime

import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import dev.dimension.flare.data.datastore.model.AppSettings

internal class AiConfigKoogBridge {
    fun availability(aiConfig: AppSettings.AiConfig): AgentAvailability =
        when (val type = aiConfig.type) {
            is AppSettings.AiConfig.Type.OnDevice -> {
                AgentAvailability.Available
            }

            is AppSettings.AiConfig.Type.OpenAI -> {
                when {
                    type.serverUrl.isBlank() -> {
                        AgentAvailability.Unavailable(AgentAvailability.Reason.MissingOpenAIEndpoint)
                    }

                    type.apiKey.isBlank() -> {
                        AgentAvailability.Unavailable(AgentAvailability.Reason.MissingOpenAIApiKey)
                    }

                    type.model.isBlank() -> {
                        AgentAvailability.Unavailable(AgentAvailability.Reason.MissingOpenAIModel)
                    }

                    else -> {
                        AgentAvailability.Available
                    }
                }
            }
        }

    fun createRuntime(
        aiConfig: AppSettings.AiConfig,
        httpClientFactory: KoogHttpClient.Factory,
    ): FlareAgentRuntime? {
        if (availability(aiConfig) != AgentAvailability.Available) {
            return null
        }

        return when (val type = aiConfig.type) {
            is AppSettings.AiConfig.Type.OnDevice -> {
                val model =
                    LLModel(
                        provider = LLMProvider.Ollama,
                        id = type.model.ifBlank { AppSettings.AiConfig.Type.OnDevice.DEFAULT_MODEL },
                        capabilities =
                            listOf(
                                LLMCapability.Completion,
                                LLMCapability.OpenAIEndpoint.Completions,
                                LLMCapability.Temperature,
                                LLMCapability.Tools,
                                LLMCapability.ToolChoice,
                                LLMCapability.Vision.Image,
                            ),
                        contextLength = type.contextLength,
                    )
                FlareAgentRuntime(
                    promptExecutor =
                        OnDevicePromptExecutor(
                            config = type,
                            httpClientFactory = httpClientFactory,
                        ),
                    model = model,
                    aiConfig = aiConfig,
                )
            }

            is AppSettings.AiConfig.Type.OpenAI -> {
                val client =
                    OpenAILLMClient(
                        apiKey = type.apiKey,
                        settings =
                            OpenAIClientSettings(
                                baseUrl = type.serverUrl.trimEnd('/'),
                                chatCompletionsPath = "chat/completions",
                                responsesAPIPath = "responses",
                                embeddingsPath = "embeddings",
                                moderationsPath = "moderations",
                                modelsPath = "models",
                            ),
                        httpClientFactory = httpClientFactory,
                    )
                val model =
                    LLModel(
                        provider = LLMProvider.OpenAI,
                        id = type.model,
                        capabilities =
                            listOf(
                                LLMCapability.Completion,
                                LLMCapability.OpenAIEndpoint.Completions,
                                LLMCapability.Temperature,
                                LLMCapability.Tools,
                                LLMCapability.ToolChoice,
                                LLMCapability.Vision.Image,
                            ),
                        contextLength = DEFAULT_CONTEXT_LENGTH,
                    )
                FlareAgentRuntime(
                    promptExecutor = MultiLLMPromptExecutor(client),
                    model = model,
                    aiConfig = aiConfig,
                )
            }
        }
    }

    private companion object {
        const val DEFAULT_CONTEXT_LENGTH = 128_000L
    }
}
