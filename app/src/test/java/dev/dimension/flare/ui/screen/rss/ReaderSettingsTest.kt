package dev.dimension.flare.ui.screen.rss

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderSettingsTest {
    @Test
    fun shortTextStillTakesOneMinute() {
        assertEquals(1, readingMinutes(""))
        assertEquals(1, readingMinutes("just a few words"))
    }

    @Test
    fun roundsUpAtTheReadingSpeed() {
        assertEquals(1, readingMinutes(List(220) { "word" }.joinToString(" ")))
        assertEquals(2, readingMinutes(List(221) { "word" }.joinToString(" ")))
    }

    @Test
    fun extraWhitespaceDoesNotCountAsWords() {
        assertEquals(1, readingMinutes("  one\n\n two\t three  "))
    }

    @Test
    fun pageTurnKeepsTheLastLineVisibleAndLandsOnWholeLines() {
        assertEquals(900, pageStep(viewportPx = 1000, lineHeightPx = 100f))
        assertEquals(800, pageStep(viewportPx = 950, lineHeightPx = 100f))
    }

    @Test
    fun pageTurnAlwaysMoves() {
        assertEquals(100, pageStep(viewportPx = 100, lineHeightPx = 100f))
        assertEquals(500, pageStep(viewportPx = 500, lineHeightPx = 0f))
    }
}
