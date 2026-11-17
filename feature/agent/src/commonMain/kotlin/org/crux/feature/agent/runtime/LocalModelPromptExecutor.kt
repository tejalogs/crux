package org.crux.feature.agent.runtime

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.crux.common.OnDeviceAI
import org.crux.data.datastore.model.AppSettings
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Wraps [OnDevicePromptExecutor] so Android downloaded `.litertlm` open-weights models power the
 * Crux AI Agent with zero cloud egress, while preserving loopback Ollama and pure-Kotlin fallbacks.
 */
internal class LocalModelPromptExecutor(
    private val config: AppSettings.AiConfig.Type.OnDevice,
    private val delegate: OnDevicePromptExecutor,
    private val onDeviceAI: OnDeviceAI,
) : PromptExecutor() {
    @OptIn(ExperimentalUuidApi::class)
    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Message.Assistant {
        enforceLoopbackPolicy()
        if (onDeviceAI.hasDownloadedModel()) {
            SovereignEdgeEngine.evaluateToolCallIfMatched(prompt, tools)?.let { return it }
            val (userPrompt, systemPrompt) = formatConversation(prompt)
            val generated =
                onDeviceAI
                    .generate(prompt = userPrompt, systemPrompt = systemPrompt)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            if (generated != null) {
                return Message.Assistant(
                    parts = listOf(MessagePart.Text(generated)),
                    metaInfo = ResponseMetaInfo(timestamp = Instant.DISTANT_PAST),
                    finishReason = "stop",
                    rawResponse = null,
                    id = Uuid.random().toString(),
                )
            }
        }
        return delegate.execute(prompt, model, tools)
    }

    @OptIn(ExperimentalUuidApi::class)
    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Flow<StreamFrame> =
        flow {
            enforceLoopbackPolicy()
            if (onDeviceAI.hasDownloadedModel()) {
                val matchedTool = SovereignEdgeEngine.evaluateToolCallIfMatched(prompt, tools)
                if (matchedTool != null) {
                    val toolCall = matchedTool.parts.filterIsInstance<MessagePart.Tool.Call>().firstOrNull()
                    if (toolCall != null) {
                        emit(
                            StreamFrame.ToolCallComplete(
                                id = toolCall.id,
                                name = toolCall.tool,
                                content = toolCall.args,
                                index = 0,
                            ),
                        )
                        emit(StreamFrame.End(finishReason = matchedTool.finishReason))
                        return@flow
                    }
                }
                val (userPrompt, systemPrompt) = formatConversation(prompt)
                var emittedAny = false
                onDeviceAI
                    .generateStreaming(prompt = userPrompt, systemPrompt = systemPrompt)
                    .collect { chunk ->
                        if (chunk.isNotEmpty()) {
                            emittedAny = true
                            emit(StreamFrame.TextDelta(text = chunk))
                        }
                    }
                if (emittedAny) {
                    emit(StreamFrame.End(finishReason = "stop"))
                    return@flow
                }
            }
            delegate.executeStreaming(prompt, model, tools).collect { emit(it) }
        }

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel,
    ): ModerationResult = delegate.moderate(prompt, model)

    override suspend fun models(): List<LLModel> = delegate.models()

    override fun close() {
        delegate.close()
    }

    private fun enforceLoopbackPolicy() {
        if (!OnDevicePromptExecutor.isLoopback(config.localhostEndpoint)) {
            throw SecurityException(
                "Zero Cloud Egress Violation: Remote endpoint '${config.localhostEndpoint}' is forbidden in On-Device AI mode.",
            )
        }
    }

    private fun formatConversation(prompt: Prompt): Pair<String, String?> {
        val systemParts = mutableListOf<String>()
        val transcript = mutableListOf<String>()

        for (message in prompt.messages) {
            when (message) {
                is Message.System -> {
                    val text = message.extractText()
                    if (text.isNotBlank()) systemParts += text
                }

                is Message.User -> {
                    val toolOutputs =
                        message.parts
                            .filterIsInstance<MessagePart.Tool.Result>()
                            .joinToString("\n") { it.output.take(3000) }
                    if (toolOutputs.isNotBlank()) {
                        transcript += "Tool context:\n$toolOutputs"
                    }
                    val text = message.extractText()
                    if (text.isNotBlank()) transcript += "User: $text"
                }

                is Message.Assistant -> {
                    val text = message.extractText()
                    if (text.isNotBlank()) transcript += "Assistant: $text"
                }
            }
        }

        val combinedUserPrompt =
            if (transcript.size == 1 && transcript.first().startsWith("User: ")) {
                transcript.first().removePrefix("User: ").trim()
            } else {
                transcript.joinToString("\n\n").trim()
            }
        val systemPrompt = systemParts.joinToString("\n\n").trim().takeIf { it.isNotBlank() }
        return combinedUserPrompt to systemPrompt
    }

    private fun Message.extractText(): String =
        parts
            .filterIsInstance<MessagePart.Text>()
            .joinToString("") { it.text }
            .trim()
}
