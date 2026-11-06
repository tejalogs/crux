package org.crux.data.datasource.linkedin

import kotlinx.coroutines.test.runTest
import org.crux.data.io.InMemoryFileStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

public class LinkedInSyncServiceCacheTest {
    private val sampleJson: String =
        """[{"id":"li_cached_1","commentary":{"text":{"text":"Hello world from offline cache"}}}]"""

    @Test
    public fun initDoesNotLoadFromDiskUntilEnsureLoaded(): Unit =
        runTest {
            val storage = InMemoryFileStorage()
            val path = storage.dataStoreFile("linkedin_feed_cache.json")
            storage.write(path, sampleJson.encodeToByteArray())

            val service = LinkedInSyncService(fileStorage = storage)

            // Before ensureLoaded, cachedUpdates must be empty (no disk I/O on init)
            assertEquals(emptyList(), service.getLatestCachedUpdates())

            // ensureLoaded reads from disk lazily
            val loaded = service.ensureLoaded()
            assertEquals(1, loaded.size)
            assertEquals("li_cached_1", loaded.first().id)
            assertEquals(
                "Hello world from offline cache",
                loaded
                    .first()
                    .commentary
                    ?.text
                    ?.text,
            )
            assertEquals(loaded, service.getLatestCachedUpdates())
        }

    @Test
    public fun corruptCacheFileIsDeletedAndReturnsEmptyWithoutCrashing(): Unit =
        runTest {
            val storage = InMemoryFileStorage()
            val path = storage.dataStoreFile("linkedin_feed_cache.json")
            storage.write(path, "Corrupt not-valid-json {[[".encodeToByteArray())
            assertTrue(storage.exists(path))

            val service = LinkedInSyncService(fileStorage = storage)
            val loaded = service.ensureLoaded()

            assertEquals(emptyList(), loaded)
            assertFalse(storage.exists(path), "Corrupt cache file must be deleted from storage")
        }

    @Test
    public fun clearCacheDeletesFileAndWipesMemory(): Unit =
        runTest {
            val storage = InMemoryFileStorage()
            val path = storage.dataStoreFile("linkedin_feed_cache.json")
            storage.write(path, sampleJson.encodeToByteArray())

            val service = LinkedInSyncService(fileStorage = storage)
            val loaded = service.ensureLoaded()
            assertEquals(1, loaded.size)

            service.clearCache()

            assertEquals(emptyList(), service.getLatestCachedUpdates())
            assertFalse(storage.exists(path), "Cache file should be deleted on clearCache")
        }
}
