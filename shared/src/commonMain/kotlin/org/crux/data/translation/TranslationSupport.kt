package org.crux.data.translation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.crux.data.database.cache.model.TranslationDisplayOptions
import org.crux.data.datastore.AppDataStore
import org.crux.data.datastore.model.AiPromptDefaults
import org.crux.data.datastore.model.AppSettings

internal object TranslationSettingsSupport {
    fun displayOptionsFlow(appDataStore: AppDataStore): Flow<TranslationDisplayOptions> =
        appDataStore.appSettingsStore.data
            .map(::displayOptions)
            .distinctUntilChanged()

    fun displayOptions(settings: AppSettings): TranslationDisplayOptions =
        TranslationDisplayOptions(
            translationEnabled = true,
            autoDisplayEnabled = settings.translateConfig.preTranslate,
            providerCacheKey = settings.translationProviderCacheKey(),
            preferPlatformTranslation =
                settings.translateConfig.preTranslate && settings.translateConfig.preferPlatformTranslation,
        )
}

internal object TranslationPromptFormatter {
    fun buildTranslatePrompt(
        settings: AppSettings,
        targetLanguage: String,
        sourceTemplate: String,
    ): String =
        settings.aiConfig.translatePrompt
            .ifBlank {
                AiPromptDefaults.TRANSLATE_PROMPT
            }.replace("{target_language}", targetLanguage)
            .replace("{source_text}", sourceTemplate)
}

internal object TranslationResponseSanitizer {
    fun clean(content: String): String =
        content
            .removePrefix("```json")
            .removePrefix("```html")
            .removePrefix("```xml")
            .removePrefix("```markup")
            .removePrefix("```text")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
}
