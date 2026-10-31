package dev.dimension.flare.data.datasource.instagram

import dev.dimension.flare.data.io.InMemoryFileStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

public class InstagramSyncServiceCacheTest {
    private val sampleJson: String =
        """[{"id":"ig_cached_101","caption":{"text":"Hello Instagram offline cache"}}]"""

    @Test
    public fun initDoesNotLoadFromDiskUntilEnsureLoaded(): Unit =
        runTest {
            val storage = InMemoryFileStorage()
            val path = storage.dataStoreFile("instagram_feed_cache.json")
            storage.write(path, sampleJson.encodeToByteArray())

            val service = InstagramSyncService(fileStorage = storage)

            // Before ensureLoaded, cachedItems must be empty (no disk I/O on init)
            assertEquals(emptyList(), service.getLatestCachedItems())

            // ensureLoaded reads from disk lazily
            val loaded = service.ensureLoaded()
            assertEquals(1, loaded.size)
            assertEquals("ig_cached_101", loaded.first().id)
            assertEquals("Hello Instagram offline cache", loaded.first().caption?.text)
            assertEquals(loaded, service.getLatestCachedItems())
        }

    @Test
    public fun corruptCacheFileIsDeletedAndReturnsEmptyWithoutCrashing(): Unit =
        runTest {
            val storage = InMemoryFileStorage()
            val path = storage.dataStoreFile("instagram_feed_cache.json")
            storage.write(path, "Corrupt {[[".encodeToByteArray())
            assertTrue(storage.exists(path))

            val service = InstagramSyncService(fileStorage = storage)
            val loaded = service.ensureLoaded()

            assertEquals(emptyList(), loaded)
            assertFalse(storage.exists(path), "Corrupt cache file must be deleted from storage")
        }

    @Test
    public fun clearCacheDeletesFileAndWipesMemory(): Unit =
        runTest {
            val storage = InMemoryFileStorage()
            val path = storage.dataStoreFile("instagram_feed_cache.json")
            storage.write(path, sampleJson.encodeToByteArray())

            val service = InstagramSyncService(fileStorage = storage)
            val loaded = service.ensureLoaded()
            assertEquals(1, loaded.size)

            service.clearCache()

            assertEquals(emptyList(), service.getLatestCachedItems())
            assertEquals(emptyList(), service.ensureLoaded())
            assertFalse(storage.exists(path), "Cache file should be deleted on disk after clearCache")
        }
}
