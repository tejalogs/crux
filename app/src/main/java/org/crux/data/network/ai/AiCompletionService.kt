package org.crux.data.network.ai

import org.crux.common.OnDeviceAI
import org.crux.data.datastore.model.AppSettings
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

/**
 * Runs Crux's AI prompts strictly on this device via [OnDeviceAI].
 */
@Single
public class AiCompletionService internal constructor(
    @Provided private val onDeviceAI: OnDeviceAI,
) {
    public suspend fun completePrompt(
        @Suppress("UNUSED_PARAMETER") config: AppSettings.AiConfig,
        prompt: String,
    ): String? =
        completeOnDevice {
            onDeviceAI.generate(prompt)
        }

    public suspend fun tldr(
        @Suppress("UNUSED_PARAMETER") config: AppSettings.AiConfig,
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String? =
        completeOnDevice {
            onDeviceAI.tldr(source, targetLanguage, prompt)
        }

    private suspend fun completeOnDevice(onDeviceCall: suspend () -> String?): String? {
        if (!onDeviceAI.isAvailable()) return null
        return onDeviceCall()?.takeIf { it.isNotBlank() }
    }
}

public fun AppSettings.AiConfig.Type.localOnly(): AppSettings.AiConfig.Type.OnDevice =
    when (this) {
        is AppSettings.AiConfig.Type.OnDevice -> this
        is AppSettings.AiConfig.Type.OpenAI -> AppSettings.AiConfig.Type.OnDevice()
    }

public val AppSettings.AiConfig.hasLegacyCloudType: Boolean
    get() = type is AppSettings.AiConfig.Type.OpenAI

public fun AppSettings.AiConfig.withLocalAiOnly(): AppSettings.AiConfig =
    if (hasLegacyCloudType) copy(type = type.localOnly()) else this
