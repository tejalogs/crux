package org.crux.ui.presenter.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.crux.common.Locale
import org.crux.common.decodeJson
import org.crux.data.datastore.AppDataStore
import org.crux.data.network.ai.AiCompletionService
import org.crux.data.repository.tryRun
import org.crux.data.translation.AiPlaceholderTranslationSupport
import org.crux.data.translation.TranslationPromptFormatter
import org.crux.data.translation.TranslationProvider
import org.crux.data.translation.TranslationResponseSanitizer
import org.crux.di.koinInject
import org.crux.ui.presenter.PresenterBase
import org.crux.ui.render.RenderContent
import org.crux.ui.render.RenderRun
import org.crux.ui.render.RenderTextStyle
import org.crux.ui.render.TranslationDocument
import org.crux.ui.render.UiRichText
import org.crux.ui.render.applyTranslationJson
import org.crux.ui.render.toTranslationJson
import org.crux.ui.render.toUiPlainText
import org.crux.ui.render.uiRichTextOf
import org.crux.web.shared.WebPresenter

@WebPresenter("aiTranslationTest")
public class AiTranslationTestPresenter : PresenterBase<AiTranslationTestPresenter.State>() {
    private val appDataStore by koinInject<AppDataStore>()
    private val aiCompletionService by koinInject<AiCompletionService>()

    @Immutable
    public interface State {
        public val sampleText: UiRichText
        public val translatedText: UiRichText?
        public val isLoading: Boolean
        public val errorMessage: String?

        public fun runTest()
    }

    @Composable
    override fun body(): State {
        val scope = rememberCoroutineScope()
        val sampleText = remember { aiTranslationTestSampleText() }
        var translatedText by remember { mutableStateOf<UiRichText?>(null) }
        var isLoading by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        return object : State {
            override val sampleText: UiRichText = sampleText
            override val translatedText: UiRichText? = translatedText
            override val isLoading: Boolean = isLoading
            override val errorMessage: String? = errorMessage

            override fun runTest() {
                scope.launch {
                    isLoading = true
                    errorMessage = null
                    translatedText = null
                    val result =
                        tryRun {
                            val settings = appDataStore.appSettingsStore.data.first()
                            val targetLanguage = Locale.language
                            val sourceJson = sampleText.toTranslationJson(targetLanguage)
                            val promptTemplate =
                                AiPlaceholderTranslationSupport.buildPromptTemplate(
                                    sourceJson.decodeJson(TranslationDocument.serializer()),
                                )
                            val prompt =
                                TranslationPromptFormatter.buildTranslatePrompt(
                                    settings = settings,
                                    targetLanguage = targetLanguage,
                                    sourceTemplate = promptTemplate,
                                )
                            val translatedContent =
                                TranslationProvider.translateDocumentJson(
                                    settings = settings,
                                    aiCompletionService = aiCompletionService,
                                    sourceTemplate = promptTemplate,
                                    sourceJson = sourceJson,
                                    targetLanguage = targetLanguage,
                                    prompt = prompt,
                                    // The settings test sends a fixed built-in sample, never the person's content.
                                    enforceDisclosure = false,
                                )
                            require(!translatedContent.isNullOrBlank()) { "Translation returned empty response" }
                            toUiRichText(
                                source = sampleText,
                                translatedContent = translatedContent,
                            )
                        }
                    result
                        .onSuccess {
                            translatedText = it
                        }.onFailure {
                            errorMessage = it.message ?: "Translation failed"
                        }
                    isLoading = false
                }
            }
        }
    }
}

private fun aiTranslationTestSampleText(): UiRichText =
    uiRichTextOf(
        renderRuns =
            listOf(
                RenderContent.Text(
                    runs =
                        persistentListOf(
                            RenderRun.Text("Hello "),
                            RenderRun.Text("@alice", style = RenderTextStyle(link = "https://example.com")),
                            RenderRun.Text(" and welcome to "),
                            RenderRun.Text("#SocialHub", style = RenderTextStyle(link = "https://example.com")),
                            RenderRun.Text(". Check "),
                            RenderRun.Text("https://example.com", style = RenderTextStyle(link = "https://example.com")),
                        ),
                ),
            ),
    )

private fun toUiRichText(
    source: UiRichText,
    translatedContent: String,
): UiRichText =
    TranslationResponseSanitizer
        .clean(translatedContent)
        .let { cleaned ->
            tryRun {
                source.applyTranslationJson(cleaned)
            }.getOrElse {
                cleaned.toUiPlainText(listOf(Locale.language))
            }
        }
