package dev.dimension.flare.data.network.ai

import dev.dimension.flare.common.OnDeviceAI
import dev.dimension.flare.data.datastore.model.AppSettings
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

@Single
public class AiCompletionService internal constructor(
    private val openAIService: OpenAIService,
    @Provided private val onDeviceAI: OnDeviceAI,
) {
    public suspend fun completePrompt(
        config: AppSettings.AiConfig,
        prompt: String,
    ): String? =
        complete(config.type, prompt) {
            onDeviceAI.generate(prompt)
        }

    public suspend fun translate(
        config: AppSettings.AiConfig,
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String? =
        complete(config.type, prompt) {
            onDeviceAI.translate(source, targetLanguage, prompt)
        }

    public suspend fun tldr(
        config: AppSettings.AiConfig,
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String? =
        complete(config.type, prompt) {
            onDeviceAI.tldr(source, targetLanguage, prompt)
        }

    private suspend fun complete(
        type: AppSettings.AiConfig.Type,
        prompt: String,
        onDeviceCall: suspend () -> String?,
    ): String? =
        when (type) {
            is AppSettings.AiConfig.Type.OnDevice -> {
                val onDeviceResult = if (onDeviceAI.isAvailable()) onDeviceCall() else null
                if (!onDeviceResult.isNullOrBlank()) {
                    onDeviceResult
                } else {
                    null
                }
            }

            is AppSettings.AiConfig.Type.OpenAI -> {
                val cloudResult =
                    openAIService.chatCompletionOrNull(
                        config = type,
                        prompt = prompt,
                    )
                if (!cloudResult.isNullOrBlank()) {
                    cloudResult
                } else if (onDeviceAI.isAvailable()) {
                    // Gracefully fallback to on-device hardware AI if cloud provider is unconfigured or unavailable
                    onDeviceCall()
                } else {
                    null
                }
            }
        }
}
