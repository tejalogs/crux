package org.crux.common

import org.crux.data.readinglist.ReadingListItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class OfflineMediaPrefetcherTest {
    @Test
    fun prefetchProgressCalculatesProgressCorrectly() {
        val initial = PrefetchProgress(isPrefetching = false, completedCount = 0, totalCount = 0)
        assertEquals(0f, initial.progress, 0.001f)
        assertFalse(initial.isPrefetching)

        val half = PrefetchProgress(isPrefetching = true, completedCount = 5, totalCount = 10)
        assertEquals(0.5f, half.progress, 0.001f)
        assertTrue(half.isPrefetching)

        val done = PrefetchProgress(isPrefetching = false, completedCount = 10, totalCount = 10)
        assertEquals(1.0f, done.progress, 0.001f)
        assertFalse(done.isPrefetching)
    }

    @Test
    fun handlesEmptyAndInvalidUrlsSafely() {
        val context = RuntimeEnvironment.getApplication()
        val prefetcher = OfflineMediaPrefetcher(context)

        // Should return early and not crash
        prefetcher.prefetchUrls(emptyList())
        assertEquals(0, prefetcher.status.value.totalCount)

        val itemWithNoMedia =
            ReadingListItem(
                id = "test-empty",
                title = "No media",
                authorName = "Author",
                contentText = "Content",
                sourceUrl = "https://example.com",
                platformId = "test",
                savedAtTimestamp = 1000L,
            )
        prefetcher.prefetchItem(itemWithNoMedia)
        assertEquals(0, prefetcher.status.value.totalCount)
    }
}
