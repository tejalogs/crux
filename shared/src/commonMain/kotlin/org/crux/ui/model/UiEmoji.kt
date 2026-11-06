package org.crux.ui.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import org.crux.common.SerializableImmutableList
import org.crux.common.SerializableImmutableMap
import org.crux.model.AccountType

@Serializable
@Immutable
public data class UiEmoji public constructor(
    val shortcode: String,
    val url: String,
    val category: String,
    val searchKeywords: SerializableImmutableList<String>,
    val insertText: String,
)

// compatibility class for Kotlin native
@Immutable
public data class EmojiData internal constructor(
    val data: SerializableImmutableMap<String, SerializableImmutableList<UiEmoji>>,
    val accountType: AccountType,
) {
    private val list = data.toList()
    public val size: Int get() = data.size

    public fun getKey(index: Int): String = list[index].first

    public fun getValue(index: Int): SerializableImmutableList<UiEmoji> = list[index].second
}
