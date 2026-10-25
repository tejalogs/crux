package dev.dimension.flare.data.ai.scribe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CarouselDeckGeneratorTest {

    @Test
    fun testMultiparagraphStoryGeneratesFourSlidesWithoutAuthorTagging() {
        val story = """
            Jerry built an empire tearing phones apart. But when his wife needed an off-road wheelchair, he faced a reality check: standard units cost $20,000+.
            
            Instead of accepting the broken pricing, he took matters into his own hands. He welded two electric bicycles together in his garage.
            
            The result? The Not-A-Wheelchair. Fast, durable, and priced at a fraction of the market monopoly.
            
            True innovation doesn't ask for permission. It simplifies the problem until the cost barrier vanishes.
        """.trimIndent()

        val deck = CarouselDeckGenerator.generateDeck(
            text = story,
            template = CarouselTemplate.ObsidianGold,
        )

        assertEquals(4, deck.slides.size)
        assertEquals(CarouselTemplate.ObsidianGold, deck.template)

        // Slide 1 should be cover
        val cover = deck.slides.first()
        assertTrue(cover.isCover)
        assertEquals(1, cover.index)
        assertEquals(deck.slides.size, cover.totalSlides)

        // Final slide should be final CTA and never contain HT @
        val finalSlide = deck.slides.last()
        assertTrue(finalSlide.isFinal)
        assertEquals(deck.slides.size, finalSlide.index)
        deck.slides.forEach { slide ->
            assertFalse(slide.footerText.orEmpty().contains("HT @"), "Slide footer must not contain HT @")
        }
    }

    @Test
    fun testShortPostWithContextArcGeneratesRichDeck() {
        val raw = """
            JerryRigEverything's wife had a horse riding accident.
            She now required a wheel chair to move about
            All high quality outdoor wheelchairs were expensive
            So he built one and started a company selling them at low cost
        """.trimIndent()

        val scribeResult = OmniScribeEngine.repackage(
            OmniScribeInput(originalText = raw, style = OmniScribeStyle.Punchy)
        )
        val deck = CarouselDeckGenerator.generateDeck(
            text = scribeResult.microPost,
            template = CarouselTemplate.WarmLinen,
            contextArc = scribeResult.contextArc,
        )

        assertTrue(deck.slides.size >= 3, "Expected at least 3 slides for multi-beat post, got ${deck.slides.size}")
        assertEquals(CarouselTemplate.WarmLinen, deck.template)
        assertTrue(deck.slides.first().isCover)
        assertTrue(deck.slides.last().isFinal)
    }

    @Test
    fun testSlideNumberingIsSequential() {
        val text = "Sentence one. Sentence two. Sentence three. Sentence four."
        val deck = CarouselDeckGenerator.generateDeck(
            text = text,
            template = CarouselTemplate.CyberCyan,
        )

        deck.slides.forEachIndexed { index, slide ->
            assertEquals(index + 1, slide.index)
            assertEquals(deck.slides.size, slide.totalSlides)
        }
    }
}
