package org.crux.data.datasource.microblog

import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable
import org.crux.common.SerializableImmutableList
import org.crux.model.MicroBlogKey
import org.crux.ui.model.mapper.instagramLike
import org.crux.ui.model.mapper.linkedInLike
import org.crux.ui.model.mapper.xqtBookmark
import org.crux.ui.model.mapper.xqtLike
import org.crux.ui.model.mapper.xqtRetweet
import kotlin.native.HiddenFromObjC

@Serializable
@HiddenFromObjC
public sealed interface PostEvent {
    public val postKey: MicroBlogKey

    @Serializable
    public sealed interface PollEvent : PostEvent {
        public val accountKey: MicroBlogKey
        public val options: SerializableImmutableList<Int>

        public fun copyWithOptions(options: List<Int>): PollEvent
    }

    @Serializable
    public sealed interface XQT : PostEvent {
        @Serializable
        public data class Retweet(
            public override val postKey: MicroBlogKey,
            public val retweeted: Boolean,
            public val count: Long = 0,
            public val accountKey: MicroBlogKey,
        ) : XQT,
            UpdatePostActionMenuEvent {
            public override fun nextActionMenu(): ActionMenu.Item =
                ActionMenu.xqtRetweet(
                    statusKey = postKey,
                    retweeted = !retweeted,
                    count = (count + if (!retweeted) 1 else -1).coerceAtLeast(0),
                    accountKey = accountKey,
                )
        }

        @Serializable
        public data class Like(
            public override val postKey: MicroBlogKey,
            public val liked: Boolean,
            public val count: Long = 0,
            public val accountKey: MicroBlogKey,
        ) : XQT,
            UpdatePostActionMenuEvent {
            public override fun nextActionMenu(): ActionMenu.Item =
                ActionMenu.xqtLike(
                    statusKey = postKey,
                    liked = !liked,
                    count = (count + if (!liked) 1 else -1).coerceAtLeast(0),
                    accountKey = accountKey,
                )
        }

        @Serializable
        public data class Bookmark(
            public override val postKey: MicroBlogKey,
            public val bookmarked: Boolean,
            public val count: Long = 0,
            public val accountKey: MicroBlogKey,
        ) : XQT,
            UpdatePostActionMenuEvent {
            public override fun nextActionMenu(): ActionMenu.Item =
                ActionMenu.xqtBookmark(
                    statusKey = postKey,
                    bookmarked = !bookmarked,
                    count = (count + if (!bookmarked) 1 else -1).coerceAtLeast(0),
                    accountKey = accountKey,
                )
        }

        @Serializable
        public data class NotInterested(
            public override val postKey: MicroBlogKey,
            public val actionMetadata: String,
        ) : XQT
    }

    @Serializable
    public sealed interface Instagram : PostEvent {
        @Serializable
        public data class Like(
            public override val postKey: MicroBlogKey,
            public val liked: Boolean,
            public val count: Long = 0,
            public val accountKey: MicroBlogKey,
        ) : Instagram,
            UpdatePostActionMenuEvent {
            public override fun nextActionMenu(): ActionMenu.Item =
                ActionMenu.instagramLike(
                    statusKey = postKey,
                    liked = !liked,
                    count = (count + if (!liked) 1 else -1).coerceAtLeast(0),
                    accountKey = accountKey,
                )
        }
    }

    @Serializable
    public sealed interface LinkedIn : PostEvent {
        @Serializable
        public data class Like(
            public override val postKey: MicroBlogKey,
            public val liked: Boolean,
            public val count: Long = 0,
            public val accountKey: MicroBlogKey,
        ) : LinkedIn,
            UpdatePostActionMenuEvent {
            public override fun nextActionMenu(): ActionMenu.Item =
                ActionMenu.linkedInLike(
                    statusKey = postKey,
                    liked = !liked,
                    count = (count + if (!liked) 1 else -1).coerceAtLeast(0),
                    accountKey = accountKey,
                )
        }
    }
}

@HiddenFromObjC
public interface UpdatePostActionMenuEvent : PostEvent {
    public fun nextActionMenu(): ActionMenu.Item
}
