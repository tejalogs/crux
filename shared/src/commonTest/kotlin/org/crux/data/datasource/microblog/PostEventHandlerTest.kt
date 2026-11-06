package org.crux.data.datasource.microblog

import androidx.room3.Room
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.crux.RobolectricTest
import org.crux.common.SerializableImmutableList
import org.crux.common.TestFormatter
import org.crux.data.database.cache.CacheDatabase
import org.crux.data.database.cache.model.DbPagingTimeline
import org.crux.data.database.cache.model.DbStatus
import org.crux.data.database.createDatabaseDriver
import org.crux.data.datasource.microblog.handler.PostEventHandler
import org.crux.di.startKoin
import org.crux.di.testSingle
import org.crux.memoryDatabaseBuilder
import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.humanizer.PlatformFormatter
import org.crux.ui.model.ClickEvent
import org.crux.ui.model.UiHandle
import org.crux.ui.model.UiNumber
import org.crux.ui.model.UiPoll
import org.crux.ui.model.UiProfile
import org.crux.ui.model.UiTimelineV2
import org.crux.ui.model.UiTranslatableText
import org.crux.ui.render.toUi
import org.crux.ui.render.toUiPlainText
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class PostEventHandlerTest : RobolectricTest() {
    private lateinit var db: CacheDatabase
    private lateinit var fakeRemoteHandler: FakeRemoteHandler
    private lateinit var handler: PostEventHandler

    private val accountKey = MicroBlogKey(id = "user-1", host = "test.social")
    private val postKey = MicroBlogKey(id = "post-1", host = "test.social")

    @BeforeTest
    fun setup() {
        db =
            Room
                .memoryDatabaseBuilder<CacheDatabase>()
                .setDriver(createDatabaseDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()

        fakeRemoteHandler = FakeRemoteHandler()
    }

    @AfterTest
    fun tearDown() {
        db.close()
        stopKoin()
    }

    @Test
    fun updateActionMenuEventAppliesOptimisticUpdate() =
        runTest {
            startKoin {
                modules(
                    module {
                        testSingle { db }
                        testSingle<CoroutineScope> { this@runTest }
                        testSingle<PlatformFormatter> { TestFormatter() }
                    },
                )
            }

            val original = createPost(actions = persistentListOf(createMenuItem(updateKey = "like", count = 1)))
            insertPost(original)

            handler = PostEventHandler(accountType = AccountType.Specific(accountKey), handler = fakeRemoteHandler)
            handler.handleEvent(TestUpdateMenuEvent(postKey = postKey, updateKey = "like", nextCount = 2))
            advanceUntilIdle()

            val updated = readPost()
            assertNotNull(updated)
            val updatedLike = updated.actions.filterIsInstance<ActionMenu.Item>().first { it.updateKey == "like" }
            assertEquals(2, updatedLike.count?.value)
            assertEquals(1, fakeRemoteHandler.callCount)
        }

    @Test
    fun updateActionMenuEventUpdatesTimelineIdentityRenderHash() =
        runTest {
            startKoin {
                modules(
                    module {
                        testSingle { db }
                        testSingle<CoroutineScope> { this@runTest }
                        testSingle<PlatformFormatter> { TestFormatter() }
                    },
                )
            }

            val original = createPost(actions = persistentListOf(createMenuItem(updateKey = "like", count = 1)))
            insertPost(original)
            db.pagingTimelineDao().insertAll(
                listOf(
                    DbPagingTimeline(
                        pagingKey = "home",
                        statusId = DbStatus.createId(AccountType.Specific(accountKey), postKey),
                        sortId = 1L,
                    ),
                ),
            )
            val beforeIdentity =
                db
                    .pagingTimelineDao()
                    .getTimelinePageIdentities(pagingKey = "home", offset = 0, limit = 1)
                    .single()

            handler = PostEventHandler(accountType = AccountType.Specific(accountKey), handler = fakeRemoteHandler)
            handler.handleEvent(TestUpdateMenuEvent(postKey = postKey, updateKey = "like", nextCount = 2))
            advanceUntilIdle()

            val saved = readStatus()
            assertNotNull(saved)
            val updatedPost = saved.content as UiTimelineV2.Post
            val afterIdentity =
                db
                    .pagingTimelineDao()
                    .getTimelinePageIdentities(pagingKey = "home", offset = 0, limit = 1)
                    .single()
            assertNotEquals(beforeIdentity.contentRevision, afterIdentity.contentRevision)
            assertEquals(updatedPost.renderHash, saved.renderHash)
        }

    @Test
    fun updateActionMenuEventFailureRevertsOriginalData() =
        runTest {
            startKoin {
                modules(
                    module {
                        testSingle { db }
                        testSingle<CoroutineScope> { this@runTest }
                        testSingle<PlatformFormatter> { TestFormatter() }
                    },
                )
            }

            val original = createPost(actions = persistentListOf(createMenuItem(updateKey = "like", count = 1)))
            insertPost(original)

            fakeRemoteHandler.shouldFail = true
            handler = PostEventHandler(accountType = AccountType.Specific(accountKey), handler = fakeRemoteHandler)
            handler.handleEvent(TestUpdateMenuEvent(postKey = postKey, updateKey = "like", nextCount = 2))
            advanceUntilIdle()

            val reverted = readPost()
            assertNotNull(reverted)
            val like = reverted.actions.filterIsInstance<ActionMenu.Item>().first { it.updateKey == "like" }
            assertEquals(1, like.count?.value)
            assertEquals(1, fakeRemoteHandler.callCount)
        }

    @Test
    fun deleteFromCacheRemovesStatusAndPagingEntry() =
        runTest {
            startKoin {
                modules(
                    module {
                        testSingle { db }
                        testSingle<CoroutineScope> { this@runTest }
                        testSingle<PlatformFormatter> { TestFormatter() }
                    },
                )
            }

            insertPost(createPost())
            db.pagingTimelineDao().insertAll(
                listOf(
                    DbPagingTimeline(
                        pagingKey = "home",
                        statusId = DbStatus.createId(AccountType.Specific(accountKey), postKey),
                        sortId = 1L,
                    ),
                ),
            )

            handler = PostEventHandler(accountType = AccountType.Specific(accountKey), handler = fakeRemoteHandler)
            handler.deleteFromCache(postKey)

            val saved = db.statusDao().get(postKey, AccountType.Specific(accountKey)).first()
            assertNull(saved)
            val exists = db.pagingTimelineDao().existsPaging(AccountType.Specific(accountKey), "home")
            assertTrue(!exists)
        }

    private suspend fun insertPost(post: UiTimelineV2.Post) {
        db.statusDao().insert(
            DbStatus(
                statusKey = postKey,
                accountType = AccountType.Specific(accountKey),
                content = post,
                renderHash = post.renderHash,
                text = post.searchText,
            ),
        )
    }

    private suspend fun readStatus(): DbStatus? =
        db
            .statusDao()
            .get(postKey, AccountType.Specific(accountKey))
            .first()

    private suspend fun readPost(): UiTimelineV2.Post? =
        readStatus()
            ?.content as? UiTimelineV2.Post

    private fun createPost(
        actions: SerializableImmutableList<ActionMenu> = persistentListOf(),
        poll: UiPoll? = null,
    ): UiTimelineV2.Post =
        UiTimelineV2.Post(
            platformId = "Mastodon",
            images = persistentListOf(),
            sensitive = false,
            contentWarning = null,
            user = createProfile(),
            content = UiTranslatableText("post content".toUiPlainText()),
            actions = actions,
            poll = poll,
            statusKey = postKey,
            card = null,
            createdAt = Clock.System.now().toUi(),
            emojiReactions = persistentListOf(),
            sourceChannel = null,
            visibility = null,
            replyToHandle = null,
            references = persistentListOf(),
            clickEvent = ClickEvent.Noop,
            accountType = AccountType.Specific(accountKey),
        )

    private fun createProfile(): UiProfile =
        UiProfile(
            key = MicroBlogKey("author", "test.social"),
            handle =
                UiHandle(
                    raw = "author",
                    host = "test.social",
                ),
            avatar = "https://test.social/author.png",
            nameInternal = "Author".toUiPlainText(),
            platformId = "Mastodon",
            clickEvent = ClickEvent.Noop,
            banner = null,
            description = null,
            matrices = UiProfile.Matrices(fansCount = 0, followsCount = 0, statusesCount = 0, platformFansCount = "0"),
            mark = persistentListOf(),
            bottomContent = null,
        )

    private fun createMenuItem(
        updateKey: String,
        count: Long,
    ) = ActionMenu.Item(
        updateKey = updateKey,
        text = ActionMenu.Item.Text.Raw("like"),
        count = UiNumber(count),
    )

    private class FakeRemoteHandler : PostEventHandler.Handler {
        var shouldFail: Boolean = false
        var callCount: Int = 0

        override suspend fun handle(
            event: PostEvent,
            updater: DatabaseUpdater,
        ) {
            callCount++
            if (shouldFail) {
                error("remote failed")
            }
        }
    }

    private data class TestUpdateMenuEvent(
        override val postKey: MicroBlogKey,
        val updateKey: String,
        val nextCount: Long,
    ) : UpdatePostActionMenuEvent {
        override fun nextActionMenu(): ActionMenu.Item =
            ActionMenu.Item(
                updateKey = updateKey,
                text = ActionMenu.Item.Text.Raw("updated"),
                count = UiNumber(nextCount),
            )
    }
}
