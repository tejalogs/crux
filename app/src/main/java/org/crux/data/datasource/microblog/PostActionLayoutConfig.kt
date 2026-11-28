package org.crux.data.datasource.microblog

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.Serializable
import org.crux.common.SerializableImmutableList

/** Kept for ProtoBuf deserialization of stored [org.crux.data.model.appearance.AppearanceBag]. */
@Serializable
@Immutable
public enum class PostActionFamily {
    Reply,
    Comment,
    Repost,
    Quote,
    Like,
    React,
    Translate,
    Bookmark,
    Favorite,
    Share,
    FxShare,
    Delete,
    Report,
    MuteUser,
    BlockUser,
}

public enum class PostActionPlacement {
    ButtonRow,
    MoreMenu,
    Hidden,
}

@Serializable
@Immutable
public data class PostActionLayoutConfig(
    val enabled: Boolean = false,
    val primary: SerializableImmutableList<PostActionFamily> = DefaultPrimary,
    val overflow: SerializableImmutableList<PostActionFamily> = DefaultOverflow,
    val hidden: SerializableImmutableList<PostActionFamily> = persistentListOf(),
) {
    public companion object {
        public val DefaultPrimary: SerializableImmutableList<PostActionFamily> =
            persistentListOf(
                PostActionFamily.Reply,
                PostActionFamily.Comment,
                PostActionFamily.Repost,
                PostActionFamily.Like,
                PostActionFamily.React,
            )

        public val DefaultOverflow: SerializableImmutableList<PostActionFamily> =
            persistentListOf(
                PostActionFamily.Bookmark,
                PostActionFamily.Favorite,
                PostActionFamily.Share,
                PostActionFamily.Delete,
                PostActionFamily.Report,
                PostActionFamily.MuteUser,
                PostActionFamily.BlockUser,
            )

        public val Default: PostActionLayoutConfig = PostActionLayoutConfig()
    }
}
