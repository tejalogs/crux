package dev.dimension.flare.data.ai.scribe

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OmniScribeEngineTest {

    private val samplePost = """
        Building software in public teaches you humility. Most features you think are critical 
        actually don't matter to users, while tiny ergonomics make all the difference. Focus on the core loop.
    """.trimIndent()

    private val multiActStoryPost = """
        JerryRigEverything's wife had a horse riding accident.
        She now required a wheel chair to move about
        All high quality outdoor wheelchairs were expensive
        So he built one and started a company selling them at low cost
    """.trimIndent()

    @Test
    fun testMicroPostLengthAndZeroAuthorTagging() {
        OmniScribeStyle.entries.forEach { style ->
            val input = OmniScribeInput(
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
        val input = OmniScribeInput(
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
        val input = OmniScribeInput(
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
            val input = OmniScribeInput(
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

    private fun assertEqualsOrGreater(actual: Int, minExpected: Int) {
        assertTrue(actual >= minExpected, "Expected at least $minExpected but got $actual")
    }
}
