package org.crux.ui.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import org.crux.common.SerializableImmutableList
import org.crux.model.MicroBlogKey
import org.crux.ui.render.UiDateTime
import org.crux.ui.render.UiRichText

@Serializable
@Immutable
public data class UiDMRoom public constructor(
    val key: MicroBlogKey,
    val users: SerializableImmutableList<UiProfile>,
    val lastMessage: UiDMItem?,
    val unreadCount: Long,
) {
    val lastMessageText: String by lazy {
        when (val message = lastMessage?.content) {
            is UiDMItem.Message.Text -> message.text.raw
            UiDMItem.Message.Deleted -> ""
            null -> ""
            is UiDMItem.Message.Media -> ""
            is UiDMItem.Message.Status -> message.status.content.original.raw
        }
    }
    val id: String by lazy {
        key.toString()
    }
    val hasUser: Boolean by lazy {
        users.isNotEmpty()
    }
}

@Serializable
@Immutable
public data class UiDMItem public constructor(
    val key: MicroBlogKey,
    val user: UiProfile,
    val content: Message,
    val timestamp: UiDateTime,
    val isFromMe: Boolean,
    val sendState: SendState?,
    val showSender: Boolean,
    val remoteCursor: String? = null,
) {
    @Serializable
    public sealed interface Message {
        @Serializable
        public data class Text(
            val text: UiRichText,
        ) : Message

        @Serializable
        public data class Media(
            val media: UiMedia,
        ) : Message

        @Serializable
        public data class Status(
            val status: UiTimelineV2.Post,
        ) : Message

        @Serializable
        public data object Deleted : Message
    }

    @Serializable
    public enum class SendState {
        Sending,
        Failed,
    }

    val id: String by lazy {
        key.toString()
    }
}
