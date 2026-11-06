package org.crux.common

import android.content.Context
import org.crux.data.ai.SovereignOnDeviceSummarizer
import org.crux.data.ai.SovereignOnDeviceTranslator
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

@Single(binds = [OnDeviceAI::class])
internal class FossOnDeviceAI(
    @Provided private val context: Context,
) : OnDeviceAI {
    override suspend fun isAvailable(): Boolean = true

    override suspend fun translate(
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String? {
        val trimmed = source.trim()
        if (trimmed.isEmpty()) return null
        return SovereignOnDeviceTranslator.translate(trimmed, targetLanguage, prompt)
    }

    override suspend fun tldr(
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String? {
        val trimmed = source.trim()
        if (trimmed.isEmpty()) return null
        return SovereignOnDeviceSummarizer.summarize(trimmed, targetLanguage)
    }
}
