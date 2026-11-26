package org.crux.data.network.ai

import org.crux.common.OnDeviceAI
import org.crux.data.datastore.model.AppSettings
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

/**
 * Runs Crux's AI prompts. They only ever run on this device.
 *
 * A config from an older build or a backup can say [AppSettings.AiConfig.Type.OpenAI]. The settings
 * store reads it back as on-device ([AppSettings.AiConfig.withLocalAiOnly], applied by
 * EncryptedAppSettingsSerializer on read and write), and the agent bridge does the same. This
 * service also ignores the type, so a cloud config that reached it anyway would still never be sent
 * to its server: the settings screen shows every config as "On-device", and sending it anywhere
 * would be a silent egress behind that label.
 */
@Single
public class AiCompletionService internal constructor(
    // Kept so existing wiring and tests compile; never called, because cloud AI is not offered.
    @Suppress("unused") private val openAIService: OpenAIService,
    @Provided private val onDeviceAI: OnDeviceAI,
) {
    // `config` is kept in both signatures for callers; every type, legacy OpenAI included, runs on
    // the on-device engine (see [localOnly]).
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

/**
 * The AI type Crux actually runs. A legacy or imported OpenAI-compatible config becomes the default
 * on-device engine; its server address and key are dropped, never used.
 */
public fun AppSettings.AiConfig.Type.localOnly(): AppSettings.AiConfig.Type.OnDevice =
    when (this) {
        is AppSettings.AiConfig.Type.OnDevice -> this
        is AppSettings.AiConfig.Type.OpenAI -> AppSettings.AiConfig.Type.OnDevice()
    }

/** True when this config came from an older build or a backup that pointed AI at a server. */
public val AppSettings.AiConfig.hasLegacyCloudType: Boolean
    get() = type is AppSettings.AiConfig.Type.OpenAI

/**
 * This config with any cloud AI type migrated to on-device. Only the value of `type` changes; every
 * other field, and the stored model's field order, stay as they are.
 */
public fun AppSettings.AiConfig.withLocalAiOnly(): AppSettings.AiConfig =
    if (hasLegacyCloudType) copy(type = type.localOnly()) else this
