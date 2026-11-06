package org.crux.data.ai.scribe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CarouselDeckGeneratorTest {
    @Test
    fun testMultiparagraphStoryGeneratesFourSlidesWithoutAuthorTagging() {
        val story =
            """
            Jerry built an empire tearing phones apart.
            But when his wife needed an off-road wheelchair, he faced a reality check: standard units cost $20,000+.
            
            Instead of accepting the broken pricing, he took matters into his own hands.
            He welded two electric bicycles together in his garage.
            
            The result? The Not-A-Wheelchair. Fast, durable, and priced at a fraction of the market monopoly.
            
            True innovation doesn't ask for permission. It simplifies the problem until the cost barrier vanishes.
            """.trimIndent()

        val deck =
            CarouselDeckGenerator.generateDeck(
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
        val raw =
            """
            JerryRigEverything's wife had a horse riding accident.
            She now required a wheel chair to move about
            All high quality outdoor wheelchairs were expensive
            So he built one and started a company selling them at low cost
            """.trimIndent()

        val scribeResult =
            OmniScribeEngine.repackage(
                OmniScribeInput(originalText = raw, style = OmniScribeStyle.Punchy),
            )
        val deck =
            CarouselDeckGenerator.generateDeck(
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
        val deck =
            CarouselDeckGenerator.generateDeck(
                text = text,
                template = CarouselTemplate.CyberCyan,
            )

        deck.slides.forEachIndexed { index, slide ->
            assertEquals(index + 1, slide.index)
            assertEquals(deck.slides.size, slide.totalSlides)
        }
    }

    @Test
    fun testNumberedListGeneratesStructuredListDeckWithMetricBadges() {
        val listPost =
            """
            Here are 3 rules for sustainable engineering:
            1. Keep latency under 500ms before adding features.
            2. Automate repetitive builds to save 8 hours a week.
            3. Never ship complex abstractions when simple composition works.
            """.trimIndent()

        val deck =
            CarouselDeckGenerator.generateDeck(
                text = listPost,
                template = CarouselTemplate.GradientAccent,
            )

        // 1 Cover + 3 List items = 4 slides
        assertEquals(4, deck.slides.size)
        assertTrue(deck.slides[0].isCover)
        assertTrue(deck.slides[3].isFinal)

        // Slide 3 has metric "8 hours"
        val slide3 = deck.slides[2]
        assertTrue(slide3.kicker.contains("8 HOURS") || slide3.kicker.contains("STEP 2"))
        assertEquals("Keep latency under 500ms before adding features.", deck.slides[1].body)
    }

    @Test
    fun testBulletedListGeneratesStructuredListDeckWithSplitTitleAndBody() {
        val bulletPost =
            """
            Core product development principles:
            • Distribution First: Products die without distribution channels.
            • Simplicity Over Novelty: Standard primitives beat esoteric designs.
            • Measure Reality: Real production telemetry beats theoretical benchmarks.
            """.trimIndent()

        val deck =
            CarouselDeckGenerator.generateDeck(
                text = bulletPost,
                template = CarouselTemplate.ObsidianGold,
            )

        // 1 Cover + 3 Bullet items = 4 slides
        assertEquals(4, deck.slides.size)
        assertTrue(deck.slides[0].isCover)
        assertTrue(deck.slides[3].isFinal)

        // Slide 2 has split headline "Distribution First" and non-redundant body
        val slide2 = deck.slides[1]
        assertEquals("Distribution First", slide2.headline)
        assertEquals("Products die without distribution channels.", slide2.body)
        assertTrue(slide2.kicker.contains("INSIGHT 1"))
    }

    @Test
    fun testMetricBadgeExtraction() {
        assertEquals("$20,000+", CarouselDeckGenerator.extractMetricBadge("Costs $20,000+ per unit"))
        assertEquals("85%", CarouselDeckGenerator.extractMetricBadge("Up to 85% reduction in latency"))
        assertEquals("10X", CarouselDeckGenerator.extractMetricBadge("Delivers 10x throughput"))
        assertEquals("8 HOURS", CarouselDeckGenerator.extractMetricBadge("Saves 8 hours each week"))
    }

    @Test
    fun testCarouselEligibilityCheck() {
        assertFalse(CarouselDeckGenerator.isEligibleForCarousel("Nice day."))
        assertFalse(CarouselDeckGenerator.isEligibleForCarousel("Just finished my lunch. Time to get back to work!"))

        // Multi-paragraph
        val multiParagraph = "First paragraph highlighting the problem statement.\n\nSecond paragraph delivering the solution."
        assertTrue(CarouselDeckGenerator.isEligibleForCarousel(multiParagraph))

        // Bulleted/Numbered list
        val bulleted = "Core takeaways:\n• Point one about performance.\n• Point two about modularity."
        assertTrue(CarouselDeckGenerator.isEligibleForCarousel(bulleted))

        // Substantive text with multiple sentences
        val substantial =
            "Building real-world on-device AI applications requires rigorous memory profiling. " +
                "If you ignore thermal throttling and batching constraints, user experience deteriorates quickly. " +
                "High signal engineering always wins."
        assertTrue(CarouselDeckGenerator.isEligibleForCarousel(substantial))
    }

    @Test
    fun testSingleSentenceFallbackDoesNotDuplicateSlideBody() {
        val shortThought = "Mastery in engineering requires continuous curiosity and persistence."
        val deck = CarouselDeckGenerator.generateDeck(shortThought)

        assertTrue(deck.slides.size >= 2)
        assertFalse(
            deck.slides[0].body == deck.slides[1].body,
            "Slide 1 and Slide 2 must not have identical duplicated body text",
        )
    }
}
