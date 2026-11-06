package org.crux.data.cache

import androidx.room3.Room
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.crux.RobolectricTest
import org.crux.common.TestFormatter
import org.crux.data.database.cache.CacheDatabase
import org.crux.data.database.cache.mapper.saveToDatabase
import org.crux.data.database.cache.model.DbStatus
import org.crux.data.database.createDatabaseDriver
import org.crux.data.datasource.microblog.paging.TimelinePagingMapper
import org.crux.data.io.InMemoryFileStorage
import org.crux.di.startKoin
import org.crux.di.testSingle
import org.crux.memoryDatabaseBuilder
import org.crux.model.AccountType
import org.crux.model.DbAccountType
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

class CacheEvictionManagerTest : RobolectricTest() {
    private lateinit var db: CacheDatabase
    private lateinit var fileStorage: InMemoryFileStorage
    private lateinit var offlineRepo: OfflineCacheRepository
    private lateinit var evictionManager: CacheEvictionManager

    @BeforeTest
    fun setup() {
        db =
            Room
                .memoryDatabaseBuilder<CacheDatabase>()
                .setDriver(createDatabaseDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()

        fileStorage = InMemoryFileStorage()
        offlineRepo = OfflineCacheRepository(fileStorage)
        evictionManager = CacheEvictionManager(db, offlineRepo)

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
        stopKoin()
    }

    private fun createUser(
        key: MicroBlogKey,
        name: String,
    ): UiProfile =
        UiProfile(
            key = key,
            handle = UiHandle(raw = key.id, host = key.host),
            avatar = "",
            nameInternal = name.toUiPlainText(),
            platformId = "LinkedIn",
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

    private fun createPost(
        accountKey: MicroBlogKey,
        user: UiProfile,
        statusKey: MicroBlogKey,
        text: String,
    ): UiTimelineV2.Post =
        UiTimelineV2.Post(
            platformId = "LinkedIn",
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

    @Test
    fun purgeExpiredCache_evictsOlderEntries_andProtectsRecentAndBookmarked() =
        runTest {
            val now = Clock.System.now().toEpochMilliseconds()
            val twentyDaysAgo = now - (20 * 86_400_000L)
            val fiveDaysAgo = now - (5 * 86_400_000L)

            // Snowflake IDs: (millis - 1700000000000L) shl 22
            val oldSortId = ((twentyDaysAgo - 1700000000000L).coerceAtLeast(0L)) shl 22
            val recentSortId = ((fiveDaysAgo - 1700000000000L).coerceAtLeast(0L)) shl 22

            val accountKey = MicroBlogKey("viewer", "linkedin.com")
            val user = createUser(MicroBlogKey("author", "linkedin.com"), "Author")

            val oldPost = createPost(accountKey, user, MicroBlogKey("post-old", "linkedin.com"), "Old post from 20 days ago")
            val recentPost = createPost(accountKey, user, MicroBlogKey("post-recent", "linkedin.com"), "Recent post from 5 days ago")
            val bookmarkedOldPost = createPost(accountKey, user, MicroBlogKey("post-bookmarked-old", "linkedin.com"), "Old bookmarked post")

            saveToDatabase(
                db,
                listOf(
                    TimelinePagingMapper.toDb(oldPost, pagingKey = "home_timeline", sortId = oldSortId),
                    TimelinePagingMapper.toDb(recentPost, pagingKey = "home_timeline", sortId = recentSortId),
                    TimelinePagingMapper.toDb(bookmarkedOldPost, pagingKey = "bookmark_timeline", sortId = oldSortId),
                ),
            )

            // Set retention to 14 days
            offlineRepo.setTimelineCacheRetentionDays(14)

            // Initial count: 3 posts
            assertEquals(3L, db.statusDao().count().first())

            // Run purge with 14 days retention
            val result = evictionManager.purgeExpiredCache(retentionDaysOverride = 14)

            // 1 timeline entry purged (oldPost on home_timeline)
            assertEquals(1, result.purgedTimelineEntries)
            // 1 orphaned status purged (oldPost)
            assertEquals(1, result.purgedStatuses)

            // Verify remaining statuses count is 2 (recent post and bookmarked post)
            assertEquals(2L, db.statusDao().count().first())

            val accountType = AccountType.Specific(accountKey) as DbAccountType
            val remainingStatuses =
                db.statusDao().getByKeys(
                    listOf(recentPost.statusKey, bookmarkedOldPost.statusKey),
                    accountType,
                )
            assertEquals(2, remainingStatuses.size)

            // Verify oldPost is gone from DbStatus
            val oldStatusResult = db.statusDao().getByKeys(listOf(oldPost.statusKey), accountType)
            assertTrue(oldStatusResult.isEmpty())
        }

    @Test
    fun triggerBackgroundEvictionIfDue_honorsThresholdAndSettings() =
        runTest {
            offlineRepo.setAutoPruneDatabaseCache(true)
            val now = Clock.System.now().toEpochMilliseconds()

            // 1. Initial state: lastDatabasePruneTimestamp = 0 -> should trigger
            evictionManager.triggerBackgroundEvictionIfDue()
            val afterFirstRun = offlineRepo.settings.value.lastDatabasePruneTimestamp
            assertTrue(afterFirstRun > 0L)

            // 2. Immediately running again: < 24 hours elapsed -> should NOT update timestamp
            evictionManager.triggerBackgroundEvictionIfDue()
            assertEquals(afterFirstRun, offlineRepo.settings.value.lastDatabasePruneTimestamp)

            // 3. Simulated 25 hours later: should trigger and update timestamp
            offlineRepo.updateLastDatabasePruneTimestamp(now - (25 * 3600 * 1000L))
            evictionManager.triggerBackgroundEvictionIfDue()
            val afterThirdRun = offlineRepo.settings.value.lastDatabasePruneTimestamp
            assertTrue(afterThirdRun > afterFirstRun)

            // 4. When autoPruneDatabaseCache = false: should NOT trigger
            offlineRepo.setAutoPruneDatabaseCache(false)
            offlineRepo.updateLastDatabasePruneTimestamp(now - (40 * 3600 * 1000L))
            val disabledTimestamp = offlineRepo.settings.value.lastDatabasePruneTimestamp
            evictionManager.triggerBackgroundEvictionIfDue()
            assertEquals(disabledTimestamp, offlineRepo.settings.value.lastDatabasePruneTimestamp)
        }
}
