package org.crux.data.database.cache.dao

import androidx.room3.Room
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.crux.RobolectricTest
import org.crux.common.TestFormatter
import org.crux.data.database.cache.CacheDatabase
import org.crux.data.database.cache.model.DbDirectMessageTimeline
import org.crux.data.database.cache.model.DbMessageItem
import org.crux.data.database.createDatabaseDriver
import org.crux.di.startKoin
import org.crux.di.testSingle
import org.crux.memoryDatabaseBuilder
import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.humanizer.PlatformFormatter
import org.crux.ui.model.ClickEvent
import org.crux.ui.model.UiDMItem
import org.crux.ui.model.UiDMRoom
import org.crux.ui.model.UiHandle
import org.crux.ui.model.UiProfile
import org.crux.ui.render.toUi
import org.crux.ui.render.toUiPlainText
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Instant

class MessageDaoTest : RobolectricTest() {
    private lateinit var db: CacheDatabase

    @BeforeTest
    fun setup() {
        db =
            Room
                .memoryDatabaseBuilder<CacheDatabase>()
                .setDriver(createDatabaseDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()
        startKoin {
            modules(
                module {
                    testSingle<PlatformFormatter> { TestFormatter() }
                },
            )
        }
    }

    @AfterTest
    fun tearDown() {
        db.close()
        stopKoin()
    }

    @Test
    fun directMessageTimeline_roundTripsUiModelContent() =
        runTest {
            val accountKey = MicroBlogKey("did:me", "bsky.social")
            val userKey = MicroBlogKey("did:other", "bsky.social")
            val roomKey = MicroBlogKey("room-1", "bsky.social")
            val messageKey = MicroBlogKey("message-1", "bsky.social")
            val user = user(userKey)
            val message =
                UiDMItem(
                    key = messageKey,
                    user = user,
                    content = UiDMItem.Message.Text("hello dm".toUiPlainText()),
                    timestamp = Instant.fromEpochMilliseconds(123).toUi(),
                    isFromMe = false,
                    sendState = null,
                    showSender = false,
                )
            val room =
                UiDMRoom(
                    key = roomKey,
                    users = listOf(user).toImmutableList(),
                    lastMessage = message,
                    unreadCount = 2,
                )

            db.messageDao().insertMessages(
                listOf(
                    DbMessageItem(
                        messageKey = messageKey,
                        roomKey = roomKey,
                        userKey = userKey,
                        timestamp = 123,
                        content = message,
                        showSender = false,
                        remoteCursor = "cursor-1",
                    ),
                ),
            )
            db.messageDao().insertTimeline(
                listOf(
                    DbDirectMessageTimeline(
                        accountType = AccountType.Specific(accountKey),
                        roomKey = roomKey,
                        sortId = 123,
                        unreadCount = 2,
                        content = room,
                    ),
                ),
            )

            val savedRoom =
                assertNotNull(
                    db
                        .messageDao()
                        .getRoomInfo(roomKey, AccountType.Specific(accountKey))
                        .first(),
                )
            val savedLastMessage = assertNotNull(savedRoom.content.lastMessage)
            val savedText = assertIs<UiDMItem.Message.Text>(savedLastMessage.content)
            assertEquals("hello dm", savedText.text.raw)
            assertEquals("cursor-1", db.messageDao().getLatestMessage(roomKey)?.remoteCursor)
        }

    @Test
    fun localDirectMessage_updatesSendStateInsideUiModel() =
        runTest {
            val accountKey = MicroBlogKey("did:me", "bsky.social")
            val roomKey = MicroBlogKey("room-1", "bsky.social")
            val messageKey = MicroBlogKey("local-message", "bsky.social")
            val sending =
                UiDMItem(
                    key = messageKey,
                    user = user(accountKey),
                    content = UiDMItem.Message.Text("pending".toUiPlainText()),
                    timestamp = Instant.fromEpochMilliseconds(456).toUi(),
                    isFromMe = true,
                    sendState = UiDMItem.SendState.Sending,
                    showSender = false,
                )

            db.messageDao().insertMessages(
                listOf(
                    DbMessageItem(
                        messageKey = messageKey,
                        roomKey = roomKey,
                        userKey = accountKey,
                        timestamp = 456,
                        content = sending,
                        showSender = false,
                        isLocal = true,
                    ),
                ),
            )
            db.messageDao().insertMessages(
                listOf(
                    assertNotNull(db.messageDao().getMessage(messageKey)).copy(
                        content = sending.copy(sendState = UiDMItem.SendState.Failed),
                    ),
                ),
            )

            assertEquals(
                UiDMItem.SendState.Failed,
                db
                    .messageDao()
                    .getMessage(messageKey)
                    ?.content
                    ?.sendState,
            )
        }

    private fun user(key: MicroBlogKey): UiProfile =
        UiProfile(
            key = key,
            handle = UiHandle(raw = key.id, host = key.host),
            avatar = "",
            nameInternal = key.id.toUiPlainText(),
            platformId = "Bluesky",
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
}
