package org.crux.data.database.app

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.crux.data.database.app.model.DbRssSources
import org.crux.data.database.app.model.SubscriptionType
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class RssSourceDaoTest {
    private fun source(
        url: String,
        id: Int = 0,
        type: SubscriptionType = SubscriptionType.RSS,
    ) = DbRssSources(id = id, url = url, title = url, icon = null, lastUpdate = 0, type = type)

    private fun <T> withDatabase(block: suspend (AppDatabase) -> T): T {
        val file = Files.createTempFile("crux-rss-dao", ".db")
        val database =
            Room
                .databaseBuilder<AppDatabase>(name = file.toAbsolutePath().toString())
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        return try {
            runBlocking { block(database) }
        } finally {
            database.close()
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun aFeedAlreadyHeldUnderTheSameNormalizedAddressIsNotInsertedAgain() =
        withDatabase { database ->
            val dao = database.rssSourceDao()

            dao.insertIfNew(source("https://example.com/feed"))
            val existing = dao.insertIfNew(source("HTTPS://EXAMPLE.com/feed/"))

            assertEquals("https://example.com/feed", existing.url)
            assertEquals(1, dao.getAllOnce().size)
        }

    @Test
    fun duplicatesInsideOneBatchCollapse() =
        withDatabase { database ->
            val dao = database.rssSourceDao()

            dao.insertAllIfNew(
                listOf(
                    source("https://a.example.com/feed"),
                    source("https://a.example.com/feed/"),
                    source("https://b.example.com/feed"),
                ),
            )

            assertEquals(2, dao.getAllOnce().size)
        }

    @Test
    fun concurrentSavesOfTheSameFeedInsertOnce() =
        withDatabase { database ->
            val dao = database.rssSourceDao()

            kotlinx.coroutines.coroutineScope {
                (1..8).map { async(Dispatchers.IO) { dao.insertIfNew(source("https://race.example.com/feed")) } }.awaitAll()
            }

            assertEquals(1, dao.getAllOnce().size)
        }

    @Test
    fun editingAnExistingRowKeepsItsId() =
        withDatabase { database ->
            val dao = database.rssSourceDao()
            dao.insertIfNew(source("https://example.com/feed"))
            val saved = dao.getAllOnce().single()

            dao.insertIfNew(saved.copy(title = "Renamed"))

            val rows = dao.getAllOnce()
            assertEquals(1, rows.size)
            assertEquals(saved.id, rows.single().id)
            assertEquals("Renamed", rows.single().title)
        }

    @Test
    fun legacyMastodonRowsAreHiddenFromRssListingsAndCanBeRemoved() =
        withDatabase { database ->
            val dao = database.rssSourceDao()
            dao.insertIfNew(source("https://example.com/feed"))
            dao.insertIfNew(source("mastodon.social", type = SubscriptionType.MASTODON_TRENDS))
            dao.insertIfNew(source("mastodon.social", type = SubscriptionType.MASTODON_LOCAL))

            assertEquals(listOf("https://example.com/feed"), dao.getAllByType(SubscriptionType.RSS).first().map { it.url })

            dao.deleteAllExceptType(SubscriptionType.RSS)

            assertEquals(listOf("https://example.com/feed"), dao.getAllOnce().map { it.url })
        }
}
