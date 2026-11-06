package org.crux.ui.screen.rss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class ReaderPositionsTest {
    private fun positions() = ReaderPositions(RuntimeEnvironment.getApplication())

    @Test
    fun fractionSurvivesAChangeOfScrollRange() {
        val fraction = scrollFractionOf(value = 500, maxValue = 2000)
        assertEquals(0.25f, fraction, 0.0001f)
        // Images finish loading and the article grows to twice the height.
        assertEquals(1000, scrollPositionFor(fraction, maxValue = 4000))
    }

    @Test
    fun fractionAndPositionAreClamped() {
        assertEquals(0f, scrollFractionOf(10, 0), 0f)
        assertEquals(1f, scrollFractionOf(5000, 1000), 0f)
        assertEquals(0, scrollPositionFor(-1f, 1000))
        assertEquals(1000, scrollPositionFor(3f, 1000))
        assertEquals(0, scrollPositionFor(0.5f, 0))
    }

    @Test
    fun savedFractionIsReadBack() {
        val store = positions()
        store.save("https://example.com/a", 0.4f)
        assertEquals(0.4f, store.get("https://example.com/a"), 0.0001f)
        assertEquals(0f, store.get("https://example.com/missing"), 0f)
    }

    @Test
    fun evictionDropsTheOldestEntriesOnly() {
        val saved = mapOf("a" to 1L, "b" to 5L, "c" to 3L, "d" to 9L)
        assertEquals(emptyList<String>(), oldestToEvict(saved, limit = 5))
        assertEquals(listOf("a"), oldestToEvict(saved, limit = 4))
        assertEquals(listOf("a", "c"), oldestToEvict(saved, limit = 3))
    }

    @Test
    fun aFullStoreForgetsOnlyTheOldestPositionNotEverything() {
        val store = positions()
        repeat(200) { index ->
            store.save("https://example.com/$index", 0.5f)
            // Distinct timestamps keep the order stable.
            Thread.sleep(1)
        }
        store.save("https://example.com/new", 0.9f)

        assertEquals(0f, store.get("https://example.com/0"), 0f)
        assertEquals(0.5f, store.get("https://example.com/1"), 0.0001f)
        assertEquals(0.5f, store.get("https://example.com/199"), 0.0001f)
        assertEquals(0.9f, store.get("https://example.com/new"), 0.0001f)
        assertTrue(store.get("https://example.com/100") > 0f)
    }

    @Test
    fun oldPixelPositionsAreIgnoredAndCleanedUp() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("reader_positions", 0)
        prefs.edit().putInt("https://example.com/legacy", 12345).commit()
        val store = positions()

        assertEquals(0f, store.get("https://example.com/legacy"), 0f)
        store.save("https://example.com/b", 0.1f)

        assertTrue(!prefs.contains("https://example.com/legacy"))
    }
}
