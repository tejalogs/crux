package org.crux.data.repository

import androidx.room3.Room
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.crux.RobolectricTest
import org.crux.common.TestFormatter
import org.crux.data.database.cache.CacheDatabase
import org.crux.data.database.cache.mapper.saveToDatabase
import org.crux.data.database.cache.model.DbPagingTimeline
import org.crux.data.database.cache.model.DbStatus
import org.crux.data.database.cache.model.DbUserHistory
import org.crux.data.database.createDatabaseDriver
import org.crux.data.datasource.microblog.paging.TimelinePagingMapper
import org.crux.di.startKoin
import org.crux.di.testSingle
import org.crux.memoryDatabaseBuilder
import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.humanizer.PlatformFormatter
import org.crux.ui.model.ClickEvent
import org.crux.ui.model.UiHandle
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
import kotlin.time.Clock

class LocalCacheRepositoryTest : RobolectricTest() {
    private lateinit var db: CacheDatabase
    private lateinit var repository: LocalCacheRepository

    @BeforeTest
    fun setup() {
        db =
            Room
                .memoryDatabaseBuilder<CacheDatabase>()
                .setDriver(createDatabaseDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()
        repository = DatabaseLocalCacheRepository(db)
        startKoin {
            modules(
                module {
                    testSingle { db }
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
    fun searchPostsReturnsMatchingCachedPosts() =
        runTest {
            val accountKey = MicroBlogKey("viewer", "example.social")
            val user = createUser(MicroBlogKey("author", "example.social"), "Author")
            val match =
                createPost(
                    accountKey = accountKey,
                    user = user,
                    statusKey = MicroBlogKey("match", "example.social"),
                    text = "needle local cache result",
                )
            val miss =
                createPost(
                    accountKey = accountKey,
                    user = user,
                    statusKey = MicroBlogKey("miss", "example.social"),
                    text = "other local cache result",
                )
            saveToDatabase(
                db,
                listOf(
                    TimelinePagingMapper.toDb(match, pagingKey = "home", sortId = 1),
                    TimelinePagingMapper.toDb(miss, pagingKey = "home", sortId = 2),
                ),
            )

            val result = repository.searchPosts(query = "needle", limit = 10)

            assertEquals(listOf(match.statusKey), result.map { it.statusKey })
        }

    @Test
    fun listViewedPostsReturnsNewestViewedPostsFirst() =
        runTest {
            val accountKey = MicroBlogKey("viewer", "example.social")
            val user = createUser(MicroBlogKey("author", "example.social"), "Author")
            val older =
                createPost(
                    accountKey = accountKey,
                    user = user,
                    statusKey = MicroBlogKey("older", "example.social"),
                    text = "older viewed post",
                )
            val newer =
                createPost(
                    accountKey = accountKey,
                    user = user,
                    statusKey = MicroBlogKey("newer", "example.social"),
                    text = "newer viewed post",
                )
            saveToDatabase(
                db,
                listOf(
                    TimelinePagingMapper.toDb(older, pagingKey = "home", sortId = 1),
                    TimelinePagingMapper.toDb(newer, pagingKey = "home", sortId = 2),
                ),
            )
            db.pagingTimelineDao().insertAll(
                listOf(
                    DbPagingTimeline(
                        pagingKey = STATUS_HISTORY_PAGING_KEY,
                        statusId = DbStatus.createId(AccountType.Specific(accountKey), older.statusKey),
                        sortId = 100,
                    ),
                    DbPagingTimeline(
                        pagingKey = STATUS_HISTORY_PAGING_KEY,
                        statusId = DbStatus.createId(AccountType.Specific(accountKey), newer.statusKey),
                        sortId = 200,
                    ),
                ),
            )

            val result = repository.listViewedPosts(limit = 10)

            assertEquals(listOf(newer.statusKey, older.statusKey), result.map { it.statusKey })
        }

    @Test
    fun userSearchAndViewedHistoryReturnCachedUsers() =
        runTest {
            val accountKey = MicroBlogKey("viewer", "example.social")
            val user = createUser(MicroBlogKey("cached-user", "example.social"), "Cached User")
            val post =
                createPost(
                    accountKey = accountKey,
                    user = user,
                    statusKey = MicroBlogKey("status", "example.social"),
                    text = "post that saves cached user",
                )
            saveToDatabase(db, listOf(TimelinePagingMapper.toDb(post, pagingKey = "home", sortId = 1)))
            db.userDao().insertHistory(
                DbUserHistory(
                    userKey = user.key,
                    accountType = AccountType.Specific(accountKey),
                    lastVisit = 123,
                ),
            )

            assertEquals(listOf(user.key), repository.searchUsers(query = "Cached", limit = 10).map { it.key })
            assertEquals(listOf(user.key), repository.listViewedUsers(limit = 10).map { it.key })
        }

    private fun createPost(
        accountKey: MicroBlogKey,
        user: UiProfile,
        statusKey: MicroBlogKey,
        text: String,
    ): UiTimelineV2.Post =
        UiTimelineV2.Post(
            platformId = "Mastodon",
            images = persistentListOf(),
            sensitive = false,
            contentWarning = null,
            user = user,
            content = UiTranslatableText(text.toUiPlainText()),
            actions = persistentListOf(),
            poll = null,
            statusKey = statusKey,
            card = null,
            createdAt = Clock.System.now().toUi(),
            emojiReactions = persistentListOf(),
            sourceChannel = null,
            visibility = null,
            replyToHandle = null,
            clickEvent = ClickEvent.Noop,
            accountType = AccountType.Specific(accountKey),
        )

    private fun createUser(
        key: MicroBlogKey,
        name: String,
    ): UiProfile =
        UiProfile(
            key = key,
            handle = UiHandle(raw = key.id, host = key.host),
            avatar = "",
            nameInternal = name.toUiPlainText(),
            platformId = "Mastodon",
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
