package org.crux.data.ai.scribe

import kotlin.test.Test
import kotlin.test.assertTrue

class CarouselSlideTitleTest {
    @Test
    fun keepsCurlyApostrophesInHeadlines() {
        val title = CarouselDeckGenerator.extractSlideTitle("Here\u2019s why you can\u2019t stop.", fallback = "Fallback")
        assertTrue(title.contains("Here\u2019s"), title)
    }
}
