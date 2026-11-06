package org.crux.ui.presenter.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import kotlinx.coroutines.flow.first
import org.crux.common.Locale
import org.crux.common.decodeJson
import org.crux.data.database.cache.CacheDatabase
import org.crux.data.database.cache.model.DbTranslation
import org.crux.data.database.cache.model.TranslationDisplayMode
import org.crux.data.database.cache.model.TranslationEntityType
import org.crux.data.database.cache.model.TranslationPayload
import org.crux.data.database.cache.model.TranslationStatus
import org.crux.data.database.cache.model.sourceHash
import org.crux.data.datastore.AppDataStore
import org.crux.data.network.ai.AiCompletionService
import org.crux.data.repository.tryRun
import org.crux.data.translation.AiPlaceholderTranslationSupport
import org.crux.data.translation.TranslationPromptFormatter
import org.crux.data.translation.TranslationProvider
import org.crux.data.translation.TranslationResponseSanitizer
import org.crux.data.translation.translationProviderCacheKey
import org.crux.di.koinInject
import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiState
import org.crux.ui.presenter.PresenterBase
import org.crux.ui.render.TranslationDocument
import org.crux.ui.render.UiRichText
import org.crux.ui.render.applyTranslationJson
import org.crux.ui.render.toTranslationJson
import org.crux.ui.render.toUiPlainText
import kotlin.time.Clock

public class TranslatePresenter(
    private val source: UiRichText,
    private val targetLanguage: String = Locale.language,
    private val cacheTarget: TranslateCacheTarget? = null,
) : PresenterBase<UiState<UiRichText>>() {
    private val aiCompletionService by koinInject<AiCompletionService>()
    private val appDataStore: AppDataStore by koinInject()
    private val database: CacheDatabase by koinInject()
    private val sourceJson: String by lazy { source.toTranslationJson(targetLanguage) }
    private val promptTemplate by lazy {
        AiPlaceholderTranslationSupport.buildPromptTemplate(
            sourceJson.decodeJson(TranslationDocument.serializer()),
        )
    }

    @Composable
    override fun body(): UiState<UiRichText> {
        return produceState<UiState<UiRichText>>(initialValue = UiState.Loading()) {
            value =
                tryRun {
                    val settings =
                        appDataStore.appSettingsStore.data
                            .first()
                    val providerCacheKey = settings.translationProviderCacheKey()
                    cachedTranslation(providerCacheKey)?.let {
                        return@tryRun it
                    }
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
                        )
                    if (translatedContent != null) {
                        val translated = toUiRichText(translatedContent)
                        cacheTranslation(translated, providerCacheKey)
                        return@tryRun translated
                    }
                    error("Translation returned empty response")
                }.fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it) },
                )
        }.value
    }

    private fun toUiRichText(translatedContent: String): UiRichText =
        TranslationResponseSanitizer
            .clean(translatedContent)
            .let { cleaned ->
                tryRun {
                    source.applyTranslationJson(cleaned)
                }.getOrElse {
                    cleaned.toUiPlainText(listOf(Locale.language))
                }
            }

    private suspend fun cachedTranslation(providerCacheKey: String): UiRichText? {
        val target = cacheTarget ?: return null
        val translation =
            database
                .translationDao()
                .get(
                    entityType = TranslationEntityType.Status,
                    entityKey = target.entityKey(),
                    targetLanguage = targetLanguage,
                ) ?: return null
        if (translation.sourceHash != target.sourcePayload().sourceHash(providerCacheKey)) {
            return null
        }
        return when (translation.status) {
            TranslationStatus.Completed -> target.readField(translation.payload)

            TranslationStatus.Skipped -> source

            TranslationStatus.Pending,
            TranslationStatus.Translating,
            TranslationStatus.Failed,
            -> null
        }
    }

    private suspend fun cacheTranslation(
        translated: UiRichText,
        providerCacheKey: String,
    ) {
        val target = cacheTarget ?: return
        val sourcePayload = target.sourcePayload()
        val existing =
            database
                .translationDao()
                .get(
                    entityType = TranslationEntityType.Status,
                    entityKey = target.entityKey(),
                    targetLanguage = targetLanguage,
                )
        val mergedPayload =
            target.mergePayload(
                existing = existing?.takeIf { it.sourceHash == sourcePayload.sourceHash(providerCacheKey) }?.payload,
                translated = translated,
            )
        database.translationDao().insert(
            DbTranslation(
                entityType = TranslationEntityType.Status,
                entityKey = target.entityKey(),
                targetLanguage = targetLanguage,
                sourceHash = sourcePayload.sourceHash(providerCacheKey),
                status = TranslationStatus.Completed,
                displayMode = TranslationDisplayMode.Translated,
                payload = mergedPayload,
                statusReason = null,
                attemptCount = existing?.attemptCount ?: 0,
                updatedAt = Clock.System.now().toEpochMilliseconds(),
            ),
        )
    }
}

public data class TranslateCacheTarget(
    val accountType: AccountType,
    val statusKey: MicroBlogKey,
    val payload: StatusTranslationPayload,
    val field: Field,
) {
    public enum class Field {
        Content,
        ContentWarning,
    }
}

public data class StatusTranslationPayload(
    val content: UiRichText,
    val contentWarning: UiRichText?,
)

private fun TranslateCacheTarget.entityKey(): String = "${accountType}_$statusKey"

private fun TranslateCacheTarget.sourcePayload(): TranslationPayload =
    TranslationPayload(
        content = payload.content,
        contentWarning = payload.contentWarning,
    )

private fun TranslateCacheTarget.readField(payload: TranslationPayload?): UiRichText? =
    when (field) {
        TranslateCacheTarget.Field.Content -> payload?.content
        TranslateCacheTarget.Field.ContentWarning -> payload?.contentWarning
    }

private fun TranslateCacheTarget.mergePayload(
    existing: TranslationPayload?,
    translated: UiRichText,
): TranslationPayload =
    when (field) {
        TranslateCacheTarget.Field.Content -> {
            TranslationPayload(
                content = translated,
                contentWarning = existing?.contentWarning,
            )
        }

        TranslateCacheTarget.Field.ContentWarning -> {
            TranslationPayload(
                content = existing?.content,
                contentWarning = translated,
            )
        }
    }
