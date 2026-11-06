package org.crux.data.datasource.microblog.loader

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.crux.data.datasource.microblog.paging.PagingRequest
import org.crux.data.datasource.microblog.paging.PagingResult
import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiDMItem
import org.crux.ui.model.UiDMRoom
import org.crux.ui.model.UiState
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public interface DirectMessageLoader {
    public val platformId: String

    public val runtimeTransformer: Flow<DirectMessageRuntimeTransformer>
        get() = flowOf(DirectMessageRuntimeTransformer())

    public suspend fun loadRooms(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiDMRoom>

    public suspend fun loadMessages(
        roomKey: MicroBlogKey,
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiDMItem>

    public suspend fun fetchRoomInfo(roomKey: MicroBlogKey): UiDMRoom

    public suspend fun sendMessage(
        roomKey: MicroBlogKey,
        message: String,
    ): UiDMItem

    public suspend fun deleteMessage(
        roomKey: MicroBlogKey,
        messageKey: MicroBlogKey,
    )

    public suspend fun fetchNewMessages(
        roomKey: MicroBlogKey,
        cursor: String?,
    ): DirectMessageDelta

    public suspend fun leaveRoom(roomKey: MicroBlogKey)

    public fun createRoom(userKey: MicroBlogKey): Flow<UiState<UiDMRoom>>

    public suspend fun canSend(userKey: MicroBlogKey): Boolean

    public suspend fun loadBadgeCount(): Int
}

@HiddenFromObjC
public data class DirectMessageDelta(
    val messages: List<UiDMItem> = emptyList(),
    val deletedMessageKeys: List<MicroBlogKey> = emptyList(),
)

@HiddenFromObjC
public data class DirectMessageRuntimeTransformer(
    val room: (UiDMRoom) -> UiDMRoom = { it },
    val item: (UiDMItem) -> UiDMItem = { it },
)
