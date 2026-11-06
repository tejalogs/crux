package org.crux.data.translation

import org.crux.common.encodeJson
import org.crux.data.database.cache.model.TranslationPayload
import org.crux.data.database.cache.model.sourceHash
import org.crux.data.datastore.model.AppSettings

internal fun aiPreTranslateConfig(preTranslate: Boolean = true): AppSettings.TranslateConfig =
    AppSettings.TranslateConfig(
        preTranslate = preTranslate,
        provider = AppSettings.TranslateConfig.Provider.AI,
    )

internal fun TranslationPayload.legacyPlatformSourceHash(
    platformId: String,
    translated: TranslationPayload,
): String {
    val payloadHash =
        translated.encodeJson(TranslationPayload.serializer()).encodeToByteArray().fold(-0x340d631b8c4674c3L) { hash, byte ->
            (hash xor (byte.toLong() and 0xffL)) * 0x100000001b3L
        }
    return sourceHash("platform:$platformId:${payloadHash.toULong().toString(16)}")
}
