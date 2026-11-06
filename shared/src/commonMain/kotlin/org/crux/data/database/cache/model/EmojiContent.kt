package org.crux.data.database.cache.model

import kotlinx.collections.immutable.persistentMapOf
import kotlinx.serialization.Serializable
import org.crux.common.SerializableImmutableList
import org.crux.common.SerializableImmutableMap
import org.crux.ui.model.UiEmoji

@Serializable
internal data class EmojiContent(
    val data: SerializableImmutableMap<String, SerializableImmutableList<UiEmoji>> = persistentMapOf(),
)
