package org.crux.ui.presenter.server

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import kotlinx.coroutines.flow.first
import org.crux.common.Locale
import org.crux.data.datastore.AppDataStore
import org.crux.data.datastore.model.AiPromptDefaults
import org.crux.data.network.ai.AiCompletionService
import org.crux.di.koinInject
import org.crux.ui.model.UiState
import org.crux.ui.presenter.PresenterBase

public class AiTLDRPresenter(
    private val source: String,
    private val targetLanguage: String = Locale.language,
) : PresenterBase<UiState<String>>() {
    private val appDataStore: AppDataStore by koinInject()
    private val aiCompletionService: AiCompletionService by koinInject()

    @Composable
    override fun body(): UiState<String> {
        return produceState(UiState.Loading()) {
            value =
                runCatching {
                    val aiConfig =
                        appDataStore.appSettingsStore.data
                            .first()
                            .aiConfig
                    if (!aiConfig.tldr) {
                        return@runCatching legacyFlareTldr()
                    }
                    val promptTemplate =
                        aiConfig.tldrPrompt.ifBlank {
                            AiPromptDefaults.TLDR_PROMPT
                        }
                    val prompt = buildTldrPrompt(promptTemplate, targetLanguage, source)
                    aiCompletionService.tldr(
                        config = aiConfig,
                        source = source,
                        targetLanguage = targetLanguage,
                        prompt = prompt,
                    ) ?: legacyFlareTldr()
                }.fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it) },
                )
        }.value
    }

    private suspend fun legacyFlareTldr(): String {
        return "Sorry, but the TLDR feature is not configured. Please set up your AI configuration in the settings to use this feature."
//        val flareServerUrl = appDataStore.flareDataStore.data.first().serverUrl
//        return CruxDataSource(flareServerUrl).tldr(source, targetLanguage)
    }

    private fun buildTldrPrompt(
        template: String,
        targetLanguage: String,
        sourceText: String,
    ): String =
        template
            .replace("{target_language}", targetLanguage)
            .replace("{source_text}", sourceText)
}
