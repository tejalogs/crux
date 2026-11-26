package org.crux.data.datasource.microblog

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.room3.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.crux.RobolectricTest
import org.crux.common.TestFormatter
import org.crux.createTestRootPath
import org.crux.data.database.cache.CacheDatabase
import org.crux.data.database.createDatabaseDriver
import org.crux.data.datasource.microblog.paging.CacheableRemoteLoader
import org.crux.data.datasource.microblog.paging.OffsetFromStartPagingKey
import org.crux.data.datasource.microblog.paging.PagingRequest
import org.crux.data.datasource.microblog.paging.PagingResult
import org.crux.data.datasource.microblog.paging.TimelinePageItem
import org.crux.data.datasource.microblog.paging.TimelineRemoteMediator
import org.crux.data.model.tab.TimelineMergePolicy
import org.crux.deleteTestRootPath
import org.crux.di.startKoin
import org.crux.di.testSingle
import org.crux.memoryDatabaseBuilder
import org.crux.model.AccountType
import org.crux.ui.humanizer.PlatformFormatter
import org.crux.ui.model.UiTimelineV2
import org.crux.ui.render.toUi
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/** A source whose refresh fails keeps the items of its last good refresh in a mixed timeline. */
class MixedRemoteMediatorFailedSourceTest : RobolectricTest() {
    private val root = createTestRootPath()

    private lateinit var db: CacheDatabase

    @BeforeTest
    fun setup() {
        startKoin {
            modules(
                module {
                    testSingle<PlatformFormatter> { TestFormatter() }
                },
            )
        }
        db =
            Room
                .memoryDatabaseBuilder<CacheDatabase>()
                .setDriver(createDatabaseDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()
    }

    @AfterTest
    fun tearDown() {
        db.close()
        stopKoin()
        deleteTestRootPath(root)
    }

    @Test
    fun timePolicyKeepsFailedFeedItemsFromItsLastGoodRefresh() =
        runTest {
            val flaky =
                SwitchableLoader(
                    "rss_flaky",
                    listOf(feed("https://flaky.example/a", 3000L), feed("https://flaky.example/b", 1000L)),
                )
            val healthy = SwitchableLoader("rss_healthy", listOf(feed("https://healthy.example/a", 2000L)))
            val errors = mutableListOf<Throwable>()

            fun mixed() =
                MixedRemoteMediator(db, listOf(flaky, healthy), TimelineMergePolicy.Time).also {
                    it.reportError = { e -> errors += e }
                }

            mixed().load(pageSize = 20, request = PagingRequest.Refresh)
            flaky.failing = true
            healthy.items = listOf(feed("https://healthy.example/new", 4000L), feed("https://healthy.example/a", 2000L))
            val result = mixed().load(pageSize = 20, request = PagingRequest.Refresh)

            assertEquals(
                listOf(
                    "https://healthy.example/new",
                    "https://flaky.example/a",
                    "https://healthy.example/a",
                    "https://flaky.example/b",
                ),
                result.data.urls(),
            )
            assertEquals(1, errors.size)
        }

    @Test
    fun timePerPagePolicyKeepsFailedFeedItemsFromItsLastGoodRefresh() =
        runTest {
            val flaky = SwitchableLoader("rss_flaky", listOf(feed("https://flaky.example/a", 3000L)))
            val healthy = SwitchableLoader("rss_healthy", listOf(feed("https://healthy.example/a", 2000L)))

            MixedRemoteMediator(db, listOf(flaky, healthy)).load(pageSize = 20, request = PagingRequest.Refresh)
            flaky.failing = true
            val result = MixedRemoteMediator(db, listOf(flaky, healthy)).load(pageSize = 20, request = PagingRequest.Refresh)

            assertEquals(listOf("https://flaky.example/a", "https://healthy.example/a"), result.data.urls())
        }

    @OptIn(ExperimentalPagingApi::class)
    @Test
    fun failedFeedItemsAreNotDeletedFromTheCombinedTimeline() =
        runTest {
            val flaky = SwitchableLoader("rss_flaky", listOf(feed("https://flaky.example/a", 3000L)))
            val healthy = SwitchableLoader("rss_healthy", listOf(feed("https://healthy.example/a", 2000L)))

            fun timeline() =
                TimelineRemoteMediator(
                    loader = MixedRemoteMediator(db, listOf(flaky, healthy), TimelineMergePolicy.Time),
                    database = db,
                )

            val first = timeline()
            assertTrue(first.load(LoadType.REFRESH, refreshState()) is androidx.paging.RemoteMediator.MediatorResult.Success)
            flaky.failing = true
            assertTrue(timeline().load(LoadType.REFRESH, refreshState()) is androidx.paging.RemoteMediator.MediatorResult.Success)

            assertEquals(
                listOf("https://flaky.example/a", "https://healthy.example/a"),
                db
                    .pagingTimelineDao()
                    .getTimelinePage(first.pagingKey, offset = 0, limit = 20)
                    .mapNotNull { (it.status.status.data.content as? UiTimelineV2.Feed)?.url },
            )
        }

    @Test
    fun aSuccessfulRefreshReplacesTheItemsKeptForLater() =
        runTest {
            val flaky = SwitchableLoader("rss_flaky", listOf(feed("https://flaky.example/old", 1000L)))

            MixedRemoteMediator(db, listOf(flaky), TimelineMergePolicy.Time).load(pageSize = 20, request = PagingRequest.Refresh)
            flaky.items = listOf(feed("https://flaky.example/new", 2000L))
            val healthyRefresh =
                MixedRemoteMediator(db, listOf(flaky), TimelineMergePolicy.Time).load(pageSize = 20, request = PagingRequest.Refresh)
            flaky.failing = true
            val failedRefresh =
                MixedRemoteMediator(db, listOf(flaky), TimelineMergePolicy.Time).load(pageSize = 20, request = PagingRequest.Refresh)

            assertEquals(listOf("https://flaky.example/new"), healthyRefresh.data.urls())
            assertEquals(listOf("https://flaky.example/new"), failedRefresh.data.urls())
        }

    @Test
    fun aFeedThatNeverLoadedStillContributesNothing() =
        runTest {
            val broken = SwitchableLoader("rss_broken", emptyList()).apply { failing = true }
            val healthy = SwitchableLoader("rss_healthy", listOf(feed("https://healthy.example/a", 2000L)))

            val result =
                MixedRemoteMediator(db, listOf(broken, healthy), TimelineMergePolicy.Time)
                    .load(pageSize = 20, request = PagingRequest.Refresh)

            assertEquals(listOf("https://healthy.example/a"), result.data.urls())
        }

    @Test
    fun nonFeedSourcesKeepTheirOldBehaviourAndWriteNoExtraCopy() =
        runTest {
            val social = SwitchableLoader("home_bluesky", listOf(feed("https://social.example/a", 3000L)))
            val healthy = SwitchableLoader("rss_healthy", listOf(feed("https://healthy.example/a", 2000L)))

            MixedRemoteMediator(db, listOf(social, healthy), TimelineMergePolicy.Time)
                .load(pageSize = 20, request = PagingRequest.Refresh)
            assertTrue(
                db.pagingTimelineDao().getTimelinePage("mixed_last_good_home_bluesky", offset = 0, limit = 20).isEmpty(),
            )
            social.failing = true
            val result =
                MixedRemoteMediator(db, listOf(social, healthy), TimelineMergePolicy.Time)
                    .load(pageSize = 20, request = PagingRequest.Refresh)

            assertEquals(listOf("https://healthy.example/a"), result.data.urls())
        }

    @Test
    fun nonFeedSourcesAreNotKeptUnderTimePerPageEither() =
        runTest {
            val social = SwitchableLoader("home_bluesky", listOf(feed("https://social.example/a", 3000L)))
            val healthy = SwitchableLoader("rss_healthy", listOf(feed("https://healthy.example/a", 2000L)))

            MixedRemoteMediator(db, listOf(social, healthy)).load(pageSize = 20, request = PagingRequest.Refresh)
            social.failing = true
            val result = MixedRemoteMediator(db, listOf(social, healthy)).load(pageSize = 20, request = PagingRequest.Refresh)

            assertEquals(listOf("https://healthy.example/a"), result.data.urls())
        }

    private fun refreshState() =
        PagingState<OffsetFromStartPagingKey, TimelinePageItem>(
            pages = emptyList(),
            anchorPosition = null,
            config = PagingConfig(pageSize = 20),
            leadingPlaceholderCount = 0,
        )

    private fun List<UiTimelineV2>.urls() = mapNotNull { (it as? UiTimelineV2.Feed)?.url }

    private class SwitchableLoader(
        override val pagingKey: String,
        var items: List<UiTimelineV2>,
    ) : CacheableRemoteLoader<UiTimelineV2> {
        var failing = false

        override suspend fun load(
            pageSize: Int,
            request: PagingRequest,
        ): PagingResult<UiTimelineV2> {
            if (failing) error("HTTP 503")
            return when (request) {
                PagingRequest.Refresh -> PagingResult(endOfPaginationReached = true, data = items)
                is PagingRequest.Append, is PagingRequest.Prepend -> error("No boundary load expected")
            }
        }
    }

    private fun feed(
        url: String,
        createdAtEpochMs: Long,
    ) = UiTimelineV2.Feed(
        title = url.substringAfterLast('/'),
        description = null,
        url = url,
        createdAt = Instant.fromEpochMilliseconds(createdAtEpochMs).toUi(),
        source =
            UiTimelineV2.Feed.Source(
                name = "test",
                icon = null,
            ),
        accountType = AccountType.Guest,
    )
}
