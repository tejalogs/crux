package org.crux.data.translation

import org.crux.common.encodeJson
import org.crux.data.database.cache.model.TranslationPayload
import org.crux.ui.render.TranslationDocument
import org.crux.ui.render.UiRichText
import org.crux.ui.render.applyTranslationDocument
import org.crux.ui.render.toTranslationDocument

internal object PreTranslationPayloadSupport {
    fun toBatchPayload(
        payload: TranslationPayload,
        targetLanguage: String,
    ): PreTranslationBatchPayload =
        PreTranslationBatchPayload(
            content = toTranslationDocumentOrNull(payload.content, targetLanguage),
            contentWarning = toTranslationDocumentOrNull(payload.contentWarning, targetLanguage),
            title = toTranslationDocumentOrNull(payload.title, targetLanguage),
            description = toTranslationDocumentOrNull(payload.description, targetLanguage),
        )

    fun applyBatchPayload(
        sourcePayload: TranslationPayload,
        sourceDocument: PreTranslationBatchPayload,
        translatedDocument: PreTranslationBatchPayload,
    ): TranslationPayload =
        TranslationPayload(
            content = applyTranslatedField(sourcePayload.content, sourceDocument.content, translatedDocument.content),
            contentWarning =
                applyTranslatedField(
                    sourcePayload.contentWarning,
                    sourceDocument.contentWarning,
                    translatedDocument.contentWarning,
                ),
            title = applyTranslatedField(sourcePayload.title, sourceDocument.title, translatedDocument.title),
            description = applyTranslatedField(sourcePayload.description, sourceDocument.description, translatedDocument.description),
        )

    fun estimatedTokens(payload: PreTranslationBatchPayload): Int =
        payload.encodeJson(PreTranslationBatchPayload.serializer()).length / 4 + 1

    fun isEmpty(payload: PreTranslationBatchPayload): Boolean =
        payload.content == null &&
            payload.contentWarning == null &&
            payload.title == null &&
            payload.description == null

    private fun toTranslationDocumentOrNull(
        richText: UiRichText?,
        targetLanguage: String,
    ): TranslationDocument? = richText?.toTranslationDocument(targetLanguage)?.takeUnless { it.blocks.isEmpty() }

    private fun applyTranslatedField(
        original: UiRichText?,
        sourceDocument: TranslationDocument?,
        translatedDocument: TranslationDocument?,
    ): UiRichText? =
        when {
            original == null -> null
            sourceDocument == null -> original
            translatedDocument == null -> throw IllegalArgumentException("Missing translated field")
            else -> original.applyTranslationDocument(translatedDocument)
        }
}
