package org.crux.common

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.crux.data.ai.SovereignOnDeviceSummarizer
import org.koin.core.annotation.Single

@Single(binds = [OnDeviceAI::class])
internal class FossOnDeviceAI(
    private val liteRtLmRuntime: LiteRtLmRuntime,
) : OnDeviceAI {
    override suspend fun isAvailable(): Boolean = true

    override suspend fun hasDownloadedModel(): Boolean = liteRtLmRuntime.hasInstalledModel()

    override suspend fun activeModelDisplayName(): String? = liteRtLmRuntime.activeModelDisplayName()

    override suspend fun generate(prompt: String): String? =
        generate(prompt = prompt, systemPrompt = null)

    override suspend fun generate(
        prompt: String,
        systemPrompt: String?,
    ): String? {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) return null
        if (!liteRtLmRuntime.hasInstalledModel()) return null

        val isSmartReply = trimmed.contains("[INSIGHT]:") || trimmed.contains("OUTPUT FORMAT:")
        val effectiveSystem =
            systemPrompt?.takeIf { it.isNotBlank() }
                ?: if (isSmartReply) SMART_REPLY_SYSTEM_PROMPT else DEFAULT_ASSISTANT_SYSTEM_PROMPT
        val maxTokens = if (isSmartReply) 320 else 1024
        val temperature = if (isSmartReply) 0.75 else 0.65

        return liteRtLmRuntime.generate(
            prompt = trimmed,
            systemPrompt = effectiveSystem,
            maxOutputTokens = maxTokens,
            temperature = temperature,
        )
    }

    override fun generateStreaming(
        prompt: String,
        systemPrompt: String?,
    ): Flow<String> {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty() || !liteRtLmRuntime.hasInstalledModel()) return emptyFlow()
        return liteRtLmRuntime.generateStreaming(
            prompt = trimmed,
            systemPrompt = systemPrompt ?: DEFAULT_ASSISTANT_SYSTEM_PROMPT,
            maxOutputTokens = 1024,
            temperature = 0.65,
        )
    }

    override suspend fun tldr(
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String? {
        val trimmed = source.trim()
        if (trimmed.isEmpty()) return null

        if (liteRtLmRuntime.hasInstalledModel()) {
            val effectivePrompt =
                if (prompt.isNotBlank() && prompt.contains(trimmed.take(32))) {
                    prompt
                } else {
                    "Summarize the following social post or article in $targetLanguage in under 200 characters. " +
                        "Respond in plain text only:\n\n$trimmed"
                }
            liteRtLmRuntime
                .generate(
                    prompt = effectivePrompt,
                    systemPrompt = TLDR_SYSTEM_PROMPT,
                    maxOutputTokens = 192,
                    temperature = 0.3,
                )?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }

        return SovereignOnDeviceSummarizer.summarize(trimmed, targetLanguage)
    }

    companion object {
        private const val DEFAULT_ASSISTANT_SYSTEM_PROMPT =
            "You are Crux's private on-device AI assistant running locally. Be accurate, helpful, and concise."
        private const val SMART_REPLY_SYSTEM_PROMPT =
            "You write short, authentic social media replies in the exact requested output tag format without preamble or commentary."
        private const val TLDR_SYSTEM_PROMPT =
            "You write ultra-concise plain-text summaries under 200 characters with no markdown or preamble."
    }
}
