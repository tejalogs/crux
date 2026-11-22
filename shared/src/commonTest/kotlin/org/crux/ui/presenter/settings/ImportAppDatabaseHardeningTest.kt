package org.crux.ui.presenter.settings

import androidx.room3.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.crux.RobolectricTest
import org.crux.data.database.app.AppDatabase
import org.crux.data.database.app.model.AppDatabaseExport
import org.crux.data.database.app.model.DbKeywordFilter
import org.crux.data.database.app.model.DbLocalFollow
import org.crux.data.database.app.model.DbRssSources
import org.crux.data.database.app.model.RssDisplayMode
import org.crux.data.database.createDatabaseDriver
import org.crux.di.startKoin
import org.crux.di.testSingle
import org.crux.memoryDatabaseBuilder
import org.crux.model.MicroBlogKey
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImportAppDatabaseHardeningTest : RobolectricTest() {
    private lateinit var db: AppDatabase
    private val json = Json { encodeDefaults = true }

    @BeforeTest
    fun setup() {
        val db =
            Room
                .memoryDatabaseBuilder<AppDatabase>()
                .setDriver(createDatabaseDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .build()
        this.db = db
        startKoin {
            modules(
                module {
                    testSingle { db }
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
    fun importIgnoresRowIdsAndDoesNotOverwriteExistingSubscriptions() =
        runTest {
            val existing =
                DbRssSources(
                    url = "https://news.example/feed",
                    title = "Trusted News",
                    icon = null,
                    lastUpdate = 0,
                )
            db.rssSourceDao().insert(existing)
            val existingId = db.rssSourceDao().getAllOnce().single().id

            val export =
                AppDatabaseExport(
                    rssSources =
                        listOf(
                            // Same id as the user's row, attacker feed behind a trusted title.
                            DbRssSources(
                                id = existingId,
                                url = "https://evil.example/feed",
                                title = "Trusted News",
                                icon = null,
                                displayMode = RssDisplayMode.OPEN_IN_BROWSER,
                                lastUpdate = 0,
                            ),
                            // Same feed address as the user's row: must not replace its title.
                            DbRssSources(
                                id = 999,
                                url = "https://NEWS.example/feed/",
                                title = "Renamed",
                                icon = null,
                                lastUpdate = 0,
                            ),
                            // Not an http(s) feed: dropped.
                            DbRssSources(
                                url = "file:///sdcard/feed.xml",
                                title = "Local",
                                icon = null,
                                lastUpdate = 0,
                            ),
                        ),
                )

            ImportAppDatabasePresenter(json.encodeToString(AppDatabaseExport.serializer(), export)).import()

            val rows = db.rssSourceDao().getAllOnce().sortedBy { it.id }
            assertEquals(2, rows.size)
            val kept = rows.first { it.id == existingId }
            assertEquals("https://news.example/feed", kept.url)
            assertEquals("Trusted News", kept.title)
            assertEquals(RssDisplayMode.FULL_CONTENT, kept.displayMode)
            val added = rows.single { it.id != existingId }
            assertEquals("https://evil.example/feed", added.url)
            assertTrue(added.id != 999)
        }

    @Test
    fun importDropsNonPublicIconAndAvatarUrls() =
        runTest {
            val export =
                AppDatabaseExport(
                    rssSources =
                        listOf(
                            DbRssSources(
                                url = "https://a.example/feed",
                                title = "A",
                                icon = "http://127.0.0.1:8080/icon.png",
                                lastUpdate = 0,
                            ),
                            DbRssSources(
                                url = "https://b.example/feed",
                                title = "B",
                                icon = "http://192.168.1.1/admin",
                                lastUpdate = 0,
                            ),
                            DbRssSources(
                                url = "https://c.example/feed",
                                title = "C",
                                icon = "javascript:alert(1)",
                                lastUpdate = 0,
                            ),
                        ),
                    localFollows =
                        listOf(
                            DbLocalFollow(
                                accountKey = MicroBlogKey("me", "x.com"),
                                userKey = MicroBlogKey("them", "x.com"),
                                platformId = "X",
                                handle = "them",
                                displayName = "Them",
                                avatar = "http://169.254.169.254/latest/meta-data",
                                groupId = null,
                                createdAt = 1L,
                            ),
                        ),
                )

            ImportAppDatabasePresenter(json.encodeToString(AppDatabaseExport.serializer(), export)).import()

            val rows = db.rssSourceDao().getAllOnce()
            assertEquals(3, rows.size)
            rows.forEach { assertNull(it.icon, "icon for ${it.url} should have been dropped") }
            val follow = db.localFollowDao().follows().first().single()
            assertNull(follow.avatar)
        }

    @Test
    fun importDoesNotOverwriteExistingKeywordFiltersAndDropsUnsafeRegex() =
        runTest {
            val userFilter =
                DbKeywordFilter(
                    keyword = "scam",
                    for_timeline = 1,
                    for_notification = 1,
                    for_search = 1,
                    expired_at = 0,
                )
            db.keywordFilterDao().insert(userFilter)

            val export =
                AppDatabaseExport(
                    keywordFilters =
                        listOf(
                            // Would quietly expire the user's own filter.
                            userFilter.copy(for_timeline = 0, expired_at = 1),
                            // Catastrophic backtracking.
                            DbKeywordFilter("(\\w+\\s?)*!$", 1, 0, 0, 0, is_regex = 1),
                            DbKeywordFilter(".*.*.*.*.*.*!", 1, 0, 0, 0, is_regex = 1),
                            DbKeywordFilter("\\w*\\w*\\w*!", 1, 0, 0, 0, is_regex = 1),
                            DbKeywordFilter("(\\w|\\d)+!", 1, 0, 0, 0, is_regex = 1),
                            // Hides every post.
                            DbKeywordFilter(".", 1, 0, 0, 0, is_regex = 1),
                            DbKeywordFilter(".*", 1, 0, 0, 0, is_regex = 1),
                            DbKeywordFilter("\\w", 1, 0, 0, 0, is_regex = 1),
                            DbKeywordFilter("[a-z]", 1, 0, 0, 0, is_regex = 1),
                            DbKeywordFilter("e", 1, 0, 0, 0),
                            // Fine.
                            DbKeywordFilter("crypto\\s+giveaway", 1, 0, 0, 0, is_regex = 1),
                            DbKeywordFilter("spoiler", 1, 0, 0, 0),
                        ),
                )

            ImportAppDatabasePresenter(json.encodeToString(AppDatabaseExport.serializer(), export)).import()

            val stored = db.keywordFilterDao().selectAll().first().associateBy { it.keyword }
            assertEquals(setOf("scam", "crypto\\s+giveaway", "spoiler"), stored.keys)
            assertEquals(userFilter, stored.getValue("scam"))
        }
}
