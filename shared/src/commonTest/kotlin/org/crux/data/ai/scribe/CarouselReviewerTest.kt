package org.crux.data.ai.scribe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CarouselReviewerTest {
    private val source = "Solar installs grew 40% in 2025. Costs fell as panel prices dropped. Grid upgrades now lag behind."
    private val slides =
        listOf(
            CarouselSlide(index = 1, totalSlides = 3, kicker = "", headline = "Solar grew", body = "Solar installs grew 40% in 2025.", isCover = true),
            CarouselSlide(index = 2, totalSlides = 3, kicker = "", headline = "Costs", body = "Costs fell as panel"),
            CarouselSlide(index = 3, totalSlides = 3, kicker = "", headline = "The catch", body = "Grid upgrades now lag behind.", isFinal = true),
        )

    @Test
    fun parsesDirectionNotesAndFixes() {
        val raw =
            """
            DIRECTION: Lead with the 40% jump and end on the grid problem.
            NOTE: Slide 2 is cut off mid-sentence.
            NOTE: Headlines are too vague.
            SLIDE 2 HEADLINE: Cheaper panels drove it
            SLIDE 2 BODY: Costs fell as panel prices dropped.
            """.trimIndent()

        val review = CarouselReviewer.parseReview(raw, slides, source)!!

        assertEquals("Lead with the 40% jump and end on the grid problem.", review.direction)
        assertEquals(2, review.notes.size)
        assertEquals(setOf(1), review.fixes.keys)
        val fixed = review.applyTo(slides)
        assertEquals("Cheaper panels drove it", fixed[1].headline)
        assertEquals("Costs fell as panel prices dropped.", fixed[1].body)
        assertEquals(slides[0], fixed[0])
        assertEquals(slides[2], fixed[2])
    }

    @Test
    fun dropsFixesThatInventNumbersOrRunTooLong() {
        val raw =
            """
            SLIDE 1 BODY: Solar installs grew 75% in 2025.
            SLIDE 3 BODY: ${"x".repeat(CarouselReviewer.MAX_BODY_CHARS + 1)}
            SLIDE 9 HEADLINE: Out of range
            """.trimIndent()

        assertNull(CarouselReviewer.parseReview(raw, slides, source))
    }

    @Test
    fun ignoresFixesIdenticalToTheOriginal() {
        val raw = "SLIDE 3 HEADLINE: The catch\nDIRECTION: Fine as is."
        val review = CarouselReviewer.parseReview(raw, slides, source)!!
        assertTrue(review.fixes.isEmpty())
        assertEquals("Fine as is.", review.direction)
    }

    @Test
    fun blankOrUnstructuredReplyGivesNoReview() {
        assertNull(CarouselReviewer.parseReview("", slides, source))
        assertNull(CarouselReviewer.parseReview("Sure! Here is my feedback about the carousel.", slides, source))
    }

    @Test
    fun promptListsEverySlideAndTheArticle() {
        val prompt = CarouselReviewer.buildReviewPrompt(slides, source)
        assertTrue(prompt.contains("SLIDE 3 HEADLINE: The catch"))
        assertTrue(prompt.contains("ARTICLE:\n$source"))
    }
}
