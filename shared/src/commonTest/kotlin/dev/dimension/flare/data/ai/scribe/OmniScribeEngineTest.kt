package dev.dimension.flare.data.ai.scribe

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OmniScribeEngineTest {
    private val samplePost =
        """
        Building software in public teaches you humility. Most features you think are critical 
        actually don't matter to users, while tiny ergonomics make all the difference. Focus on the core loop.
        """.trimIndent()

    private val multiActStoryPost =
        """
        JerryRigEverything's wife had a horse riding accident.
        She now required a wheel chair to move about
        All high quality outdoor wheelchairs were expensive
        So he built one and started a company selling them at low cost
        """.trimIndent()

    @Test
    fun testMicroPostLengthAndZeroAuthorTagging() {
        OmniScribeStyle.entries.forEach { style ->
            val input =
                OmniScribeInput(
                    originalText = samplePost,
                    authorName = "Guillermo Rauch",
                    authorHandle = "rauchg",
                    style = style,
                )
            val result = OmniScribeEngine.repackage(input)

            assertTrue(
                result.microPost.length <= 280,
                "Micro post for style $style exceeded 280 characters: ${result.microPost.length}",
            )
            assertTrue(result.microPost.isNotBlank(), "Micro post should not be blank")
            assertNull(result.hatTip, "Hat-tip attribution must be null for standalone posts")
            assertFalse(result.microPost.contains("HT @"), "Micro post must not contain HT @ creator tag")
            assertFalse(result.microPost.contains("@rauchg"), "Micro post must not tag original creator")
        }
    }

    @Test
    fun testDeepContextMultiActStoryPreservation() {
        val input =
            OmniScribeInput(
                originalText = multiActStoryPost,
                authorName = "The Kaipullai",
                authorHandle = "thekaipullai",
                style = OmniScribeStyle.Punchy,
            )
        val result = OmniScribeEngine.repackage(input)

        // Verify all narrative acts are preserved in both micro and story formats
        val arc = assertNotNull(result.contextArc)
        assertEqualsOrGreater(arc.narrativeBeats.size, 4)

        assertTrue(result.microPost.contains("JerryRigEverything"), "Micro post should preserve protagonist")
        assertTrue(result.microPost.contains("wheelchair"), "Micro post should normalize and preserve problem")
        assertTrue(result.microPost.contains("built one"), "Micro post should preserve resolution/action")
        assertFalse(result.microPost.contains("HT @"), "Micro post must never include HT @ tag")

        assertTrue(result.storyPost.contains("JerryRigEverything"), "Story post should preserve protagonist")
        assertTrue(result.storyPost.contains("expensive"), "Story post should preserve market conflict")
        assertTrue(result.storyPost.contains("built one"), "Story post should preserve turning point")
        assertFalse(result.storyPost.contains("The Kaipullai"), "Story post must not quote original author name")
        assertFalse(result.storyPost.contains("HT @"), "Story post must not include HT @ tag")
    }

    @Test
    fun testStoryPostFormattingForLinkedIn() {
        val input =
            OmniScribeInput(
                originalText = samplePost,
                authorName = "Guillermo Rauch",
                authorHandle = "rauchg",
                style = OmniScribeStyle.Narrative,
            )
        val result = OmniScribeEngine.repackage(input)

        assertTrue(result.storyPost.contains("\n\n"), "Story post should contain paragraph whitespace")
        assertTrue(result.storyPost.length > result.microPost.length, "Story post should be longer than micro post")
        assertFalse(result.storyPost.contains("HT @rauchg"), "Story post should be standalone without HT tag")
    }

    @Test
    fun testBannedAiTropesAbsence() {
        OmniScribeStyle.entries.forEach { style ->
            val input =
                OmniScribeInput(
                    originalText = "In today's fast-paced world, this game-changer will delve into the paradigm shift.",
                    authorName = "Tech Observer",
                    authorHandle = "tech",
                    style = style,
                )
            val result = OmniScribeEngine.repackage(input)

            OmniScribeEngine.BannedAiTropes.forEach { trope ->
                assertFalse(
                    result.microPost.lowercase().contains(trope),
                    "Micro post for $style contains banned trope: '$trope'",
                )
                assertFalse(
                    result.storyPost.lowercase().contains(trope),
                    "Story post for $style contains banned trope: '$trope'",
                )
            }
        }
    }

    @Test
    fun testSystemPromptConstruction() {
        val prompt = OmniScribeEngine.buildSystemPrompt(OmniScribeStyle.Philosophical)
        assertTrue(prompt.contains("FIRST PRINCIPLES"), "Prompt should contain uppercase style name")
        assertTrue(prompt.contains("ZERO AI SLOP"), "Prompt should contain anti-slop rules")
        assertTrue(prompt.contains("280 characters"), "Prompt should specify character limits")
    }

    @Test
    fun testRssLongformArticleDistillationIntoStandalonePostAndCarousels() {
        val articleTitle = "Why Postgres Is Winning The Modern Database War"
        val articleHtml =
            """
            <article>
                <p>The developer ecosystem is witnessing a fundamental shift away from specialized niche databases.</p>
                <p>However, running seven separate distributed engines introduced catastrophic operational complexity and fragility across engineering teams.</p>
                <p>Instead of managing fragmented datastores, teams benchmarked extensions: pgvector delivered 95% of specialized vector DB performance while slashing infrastructure costs by $40k/month.</p>
                <p>Ultimately, developers will always trade 5% peak synthetic performance for boring reliability and unified ACID guarantees.</p>
                <p>Subscribe to our newsletter for more database engineering deep dives. Originally published on TechBlog.</p>
            </article>
            """.trimIndent()

        OmniScribeStyle.entries.forEach { style ->
            val result =
                OmniScribeEngine.synthesizeArticle(
                    title = articleTitle,
                    content = articleHtml,
                    sourceUrl = "https://techblog.dev/postgres-war",
                    authorName = "Martin Kleppmann",
                    style = style,
                )

            assertTrue(result.microPost.length <= 280, "Article micro post for $style exceeded 280: ${result.microPost.length}")
            assertFalse(result.microPost.contains("HT @"), "Article micro post must not contain HT @")
            assertFalse(result.microPost.contains("Subscribe to our newsletter"), "Must strip RSS boilerplate")

            assertTrue(result.storyPost.contains("\n\n"), "Story post should have paragraph breaks")
            assertTrue(result.storyPost.contains("Postgres"), "Story post must preserve core subject")
            assertFalse(result.storyPost.contains("HT @"), "Story post must not contain HT @")

            val deck =
                CarouselDeckGenerator.generateDeck(
                    text = result.storyPost,
                    authorName = "Tech Synthesis",
                    authorHandle = "rss",
                    template = CarouselTemplate.ObsidianGold,
                    contextArc = result.contextArc,
                )

            assertEqualsOrGreater(deck.slides.size, 3)
            assertTrue(deck.slides.first().isCover)
            assertTrue(deck.slides.last().isFinal)
            deck.slides.forEach { slide ->
                assertFalse(slide.headline.isBlank(), "Slide headline should not be blank")
                assertFalse(slide.body.isBlank(), "Slide body should not be blank")
                assertFalse(slide.body.contains("Subscribe to"), "Slide body must not contain boilerplate")
            }
        }
    }

    @Test
    fun testShortRssArticleWithMinimalDescription() {
        val result =
            OmniScribeEngine.synthesizeArticle(
                title = "A Tiny Microchip That Runs on Ambient Radio Waves",
                content = "Researchers built an ultra-low power sensor that harvests energy directly from Wi-Fi signals.",
                sourceUrl = "https://mit.edu/ambient-chip",
                authorName = "MIT News",
                style = OmniScribeStyle.Punchy,
            )

        assertTrue(result.microPost.length <= 280, "Minimal RSS micro post exceeded 280: ${result.microPost.length}")
        assertTrue(result.microPost.isNotBlank())
        assertTrue(result.storyPost.isNotBlank())
        assertFalse(result.microPost.contains("HT @"))
    }

    private fun assertEqualsOrGreater(
        actual: Int,
        minExpected: Int,
    ) {
        assertTrue(actual >= minExpected, "Expected at least $minExpected but got $actual")
    }
}
