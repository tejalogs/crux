package org.crux.data.datasource.microblog.loader

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import org.crux.ui.model.UiEmoji
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public interface EmojiLoader {
    public suspend fun emojis(): ImmutableMap<String, ImmutableList<UiEmoji>>
}
