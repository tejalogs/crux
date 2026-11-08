package org.crux.feature.agent.runtime

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import org.crux.data.datastore.model.AppSettings

/**
 * Sovereign On-Device Prompt Executor for Gemma 4 Edge.
 *
 * Enforces a strict Zero-Egress Privacy Policy: only local loopback endpoints
 * (127.0.0.1, localhost) or fully offline sovereign engines are permitted.
 */
public class SecurityException(
    message: String,
) : IllegalStateException(message)

internal class OnDevicePromptExecutor(
    private val config: AppSettings.AiConfig.Type.OnDevice,
    private val httpClientFactory: KoogHttpClient.Factory,
) : PromptExecutor() {
    private val ollamaClient: OllamaClient? by lazy {
        if (isLoopback(config.localhostEndpoint)) {
            runCatching<OllamaClient> {
                OllamaClient(
                    httpClientFactory = httpClientFactory,
                    baseUrl = config.localhostEndpoint.trimEnd('/'),
                )
            }.getOrNull()
        } else {
            null
        }
    }

    private val ollamaExecutor: MultiLLMPromptExecutor? by lazy {
        ollamaClient?.let { MultiLLMPromptExecutor(it) }
    }

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Message.Assistant {
        // Enforce zero cloud egress security policy
        val endpoint = config.localhostEndpoint.trim()
        if (endpoint.isNotBlank() && !isLoopback(endpoint)) {
            throw SecurityException(
                "Zero-egress privacy policy violation: On-device intelligence cannot egress to non-loopback endpoint '$endpoint'",
            )
        }

        // 1. Attempt localhost Ollama execution if available
        val executor = ollamaExecutor
        if (executor != null) {
            val result =
                runCatching {
                    executor.execute(prompt, model, tools)
                }.getOrNull()

            if (result != null) {
                return result
            }
        }

        // 2. Fall back to Sovereign On-Device Edge Engine (100% offline)
        return SovereignEdgeEngine.execute(prompt, tools)
    }

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Flow<StreamFrame> {
        val endpoint = config.localhostEndpoint.trim()
        if (endpoint.isNotBlank() && !isLoopback(endpoint)) {
            throw SecurityException(
                "Zero-egress privacy policy violation: On-device intelligence cannot egress to non-loopback endpoint '$endpoint'",
            )
        }

        val executor = ollamaExecutor
        return if (executor != null) {
            executor
                .executeStreaming(prompt, model, tools)
                .catch {
                    // On streaming failure, fallback seamlessly to sovereign edge engine
                    emitAll(SovereignEdgeEngine.executeStreaming(prompt, tools))
                }
        } else {
            SovereignEdgeEngine.executeStreaming(prompt, tools)
        }
    }

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel,
    ): ModerationResult = ModerationResult(isHarmful = false, categories = emptyMap())

    override suspend fun models(): List<LLModel> =
        listOf(
            LLModel(
                provider = LLMProvider.Ollama,
                id = config.model.ifBlank { AppSettings.AiConfig.Type.OnDevice.DEFAULT_MODEL },
                capabilities =
                    listOf(
                        LLMCapability.Completion,
                        LLMCapability.OpenAIEndpoint.Completions,
                        LLMCapability.Temperature,
                        LLMCapability.Tools,
                        LLMCapability.ToolChoice,
                        LLMCapability.Vision.Image,
                    ),
                contextLength = config.contextLength,
            ),
        )

    override fun close() {
        try {
            ollamaExecutor?.close()
        } catch (_: Throwable) {
        }
        try {
            ollamaClient?.close()
        } catch (_: Throwable) {
        }
    }

    companion object {
        fun isLoopback(endpoint: String): Boolean {
            val clean = endpoint.trim().lowercase()
            if (clean.isBlank()) return false
            val hostAndPort =
                clean
                    .removePrefix("http://")
                    .removePrefix("https://")
                    .substringBefore('/')

            val host =
                if (hostAndPort.startsWith('[')) {
                    hostAndPort.substringAfter('[').substringBefore(']')
                } else {
                    hostAndPort.substringBefore(':')
                }

            return host == "localhost" ||
                host == "127.0.0.1" ||
                host == "::1" ||
                host == "0.0.0.0" ||
                host.startsWith("127.")
        }
    }
}
