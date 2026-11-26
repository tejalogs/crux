package org.crux.feature.agent.runtime

import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import org.crux.common.OnDeviceAI
import org.crux.data.datastore.model.AppSettings
import org.crux.data.network.ai.localOnly

/**
 * Builds the agent runtime from the stored AI config. The agent only ever runs on the device: a
 * legacy or imported OpenAI-compatible config is read as the default on-device engine through
 * [localOnly], so its server address and API key are never used and no cloud client is built.
 * The settings screen labels every config "On-device", and this keeps that label true.
 */
internal class AiConfigKoogBridge {
    fun availability(
        @Suppress("UNUSED_PARAMETER") aiConfig: AppSettings.AiConfig,
    ): AgentAvailability = AgentAvailability.Available

    fun createRuntime(
        aiConfig: AppSettings.AiConfig,
        httpClientFactory: KoogHttpClient.Factory,
        onDeviceAI: OnDeviceAI? = null,
    ): CruxAgentRuntime? {
        val type = aiConfig.type.localOnly()
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
        val delegate =
            OnDevicePromptExecutor(
                config = type,
                httpClientFactory = httpClientFactory,
            )
        return CruxAgentRuntime(
            promptExecutor =
                if (onDeviceAI != null) {
                    LocalModelPromptExecutor(
                        config = type,
                        delegate = delegate,
                        onDeviceAI = onDeviceAI,
                    )
                } else {
                    delegate
                },
            model = model,
            // The runtime carries the config it actually runs, so nothing downstream can read the
            // legacy server address or key.
            aiConfig = aiConfig.copy(type = type),
        )
    }
}
