package org.crux.data.datasource.microblog

import androidx.paging.PagingData
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import org.crux.common.CacheData
import org.crux.data.database.cache.model.DbMessageItem
import org.crux.data.datasource.microblog.handler.DirectMessageHandler
import org.crux.model.MicroBlogKey
import org.crux.ui.model.ClickEvent
import org.crux.ui.model.UiDMItem
import org.crux.ui.model.UiDMRoom
import org.crux.ui.model.UiHandle
import org.crux.ui.model.UiProfile
import org.crux.ui.model.UiState
import org.crux.ui.render.toUi
import org.crux.ui.render.toUiPlainText
import kotlin.native.HiddenFromObjC
import kotlin.time.Clock
import kotlin.uuid.Uuid

@HiddenFromObjC
public interface DirectMessageDataSource : AuthenticatedMicroblogDataSource {
    public val directMessageHandler: DirectMessageHandler

    public fun directMessageList(scope: CoroutineScope): Flow<PagingData<UiDMRoom>> = directMessageHandler.list(scope)

    public fun directMessageConversation(
        roomKey: MicroBlogKey,
        scope: CoroutineScope,
    ): Flow<PagingData<UiDMItem>> = directMessageHandler.conversation(roomKey, scope)

    public fun sendDirectMessage(
        roomKey: MicroBlogKey,
        message: String,
    ) {
        directMessageHandler.send(roomKey, message)
    }

    public fun retrySendDirectMessage(messageKey: MicroBlogKey) {
        directMessageHandler.retry(messageKey)
    }

    public fun deleteDirectMessage(
        roomKey: MicroBlogKey,
        messageKey: MicroBlogKey,
    ) {
        directMessageHandler.delete(roomKey, messageKey)
    }

    public fun getDirectMessageConversationInfo(roomKey: MicroBlogKey): CacheData<UiDMRoom> = directMessageHandler.roomInfo(roomKey)

    public suspend fun fetchNewDirectMessageForConversation(roomKey: MicroBlogKey) {
        directMessageHandler.fetchNew(roomKey)
    }

    public val directMessageBadgeCount: CacheData<Int>
        get() = directMessageHandler.badgeCount

    public fun leaveDirectMessage(roomKey: MicroBlogKey) {
        directMessageHandler.leave(roomKey)
    }

    public fun createDirectMessageRoom(userKey: MicroBlogKey): Flow<UiState<MicroBlogKey>> = directMessageHandler.createRoom(userKey)

    public suspend fun canSendDirectMessage(userKey: MicroBlogKey): Boolean = directMessageHandler.canSend(userKey)
}

internal fun createSendingDirectMessage(
    accountKey: MicroBlogKey,
    roomKey: MicroBlogKey,
    message: String,
    platformId: String,
    user: UiProfile = fallbackDirectMessageUser(accountKey, platformId),
) = MicroBlogKey(Uuid.random().toString(), accountKey.host).let { messageKey ->
    val timestamp = Clock.System.now()
    DbMessageItem(
        userKey = accountKey,
        roomKey = roomKey,
        timestamp = timestamp.toEpochMilliseconds(),
        messageKey = messageKey,
        content =
            UiDMItem(
                key = messageKey,
                user = user,
                content = UiDMItem.Message.Text(message.toUiPlainText()),
                timestamp = timestamp.toUi(),
                isFromMe = true,
                sendState = UiDMItem.SendState.Sending,
                showSender = false,
            ),
        isLocal = true,
        showSender = false,
    )
}

private fun fallbackDirectMessageUser(
    accountKey: MicroBlogKey,
    platformId: String,
): UiProfile =
    UiProfile(
        key = accountKey,
        handle = UiHandle(raw = accountKey.id, host = accountKey.host),
        avatar = null,
        nameInternal = accountKey.id.toUiPlainText(),
        platformId = platformId,
        clickEvent = ClickEvent.Noop,
        banner = null,
        description = null,
        matrices =
            UiProfile.Matrices(
                fansCount = 0,
                followsCount = 0,
                statusesCount = 0,
                platformFansCount = null,
            ),
        mark = persistentListOf(),
        bottomContent = null,
    )
