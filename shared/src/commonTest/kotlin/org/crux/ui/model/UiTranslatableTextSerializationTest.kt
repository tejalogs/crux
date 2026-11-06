package org.crux.ui.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import org.crux.ui.render.toUiPlainText
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalSerializationApi::class)
class UiTranslatableTextSerializationTest {
    @Test
    fun protobufRoundTripPreservesOriginalAndTranslation() {
        val value =
            UiTranslatableText(
                original = "original".toUiPlainText(listOf("en")),
                translation = "译文".toUiPlainText(listOf("zh-CN")),
            )

        val decoded =
            ProtoBuf.decodeFromByteArray(
                UiTranslatableText.serializer(),
                ProtoBuf.encodeToByteArray(UiTranslatableText.serializer(), value),
            )

        assertEquals(value, decoded)
    }
}
