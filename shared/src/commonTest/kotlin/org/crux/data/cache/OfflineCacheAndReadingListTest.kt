package org.crux.data.cache

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.crux.data.io.InMemoryFileStorage
import org.crux.data.readinglist.ReadingListItem
import org.crux.data.readinglist.ReadingListRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OfflineCacheAndReadingListTest {
    @Test
    fun offlineCacheRepositoryDefaultsAndUpdates() =
        runTest {
            val fileStorage = InMemoryFileStorage()
            val repo = OfflineCacheRepository(fileStorage)

            assertEquals(512, repo.settings.value.maxMediaCacheSizeMb)
            assertTrue(repo.settings.value.prefetchMediaForReadingList)
            assertFalse(repo.settings.value.wifiOnlyPrefetch)

            repo.setMaxMediaCacheSizeMb(1024)
            assertEquals(1024, repo.settings.value.maxMediaCacheSizeMb)

            repo.setWifiOnlyPrefetch(true)
            assertTrue(repo.settings.value.wifiOnlyPrefetch)

            // Reload from storage to verify persistence
            val reloaded = OfflineCacheRepository(fileStorage)
            assertEquals(1024, reloaded.settings.value.maxMediaCacheSizeMb)
            assertTrue(reloaded.settings.value.wifiOnlyPrefetch)
        }

    @Test
    fun readingListRepositorySavesUpdatesAndRemoves() =
        runTest {
            val fileStorage = InMemoryFileStorage()
            val repo = ReadingListRepository(fileStorage)

            assertEquals(0, repo.items.value.size)

            val item1 =
                ReadingListItem(
                    id = "post-1",
                    title = "Decentralized Social Networks",
                    authorName = "Alice",
                    authorHandle = "@alice@mastodon.social",
                    contentText = "ActivityPub and ATProto are thriving in 2026.",
                    sourceUrl = "https://mastodon.social/@alice/123",
                    platformId = "mastodon",
                    savedAtTimestamp = 1700000000000L,
                    mediaUrls = listOf("https://mastodon.social/media/1.jpg"),
                )
            val item2 =
                ReadingListItem(
                    id = "rss-2",
                    title = "Photography Digest",
                    authorName = "National Geographic",
                    authorHandle = "natgeo",
                    contentText = "Stunning wildlife photos from the Serengeti.",
                    sourceUrl = "https://instagram.com/natgeo",
                    platformId = "instagram",
                    savedAtTimestamp = 1700000005000L,
                    mediaUrls = listOf("https://instagram.com/p/1.jpg", "https://instagram.com/p/2.jpg"),
                )

            repo.save(item1)
            repo.save(item2)

            val items = repo.items.value
            assertEquals(2, items.size)
            assertEquals("rss-2", items[0].id) // Most recent first
            assertEquals("post-1", items[1].id)

            assertTrue(repo.isSaved("post-1").first())
            assertTrue(repo.isSaved("rss-2").first())
            assertFalse(repo.isSaved("unknown").first())

            // Mark as read
            repo.markAsRead("post-1", true)
            assertTrue(repo.getItem("post-1")!!.isRead)
            assertFalse(repo.getItem("rss-2")!!.isRead)

            // Remove item
            repo.remove("post-1")
            assertEquals(1, repo.items.value.size)
            assertNull(repo.getItem("post-1"))
            assertNotNull(repo.getItem("rss-2"))

            // Reload from storage to verify persistence
            val reloaded = ReadingListRepository(fileStorage)
            assertEquals(1, reloaded.items.value.size)
            assertEquals("rss-2", reloaded.items.value[0].id)
        }
}
