package dev.dimension.flare.data.ai.scribe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SmartReplyEngineTest {

    @Test
    fun testGeneratesFourDistinctReplyAnglesForTechSecurityPost() {
        val post = """
            Good assessment of the on the ground reality for security in most large enterprises right now.
            The fragmented landscape of the risk surface is only going to get more gnarly, making it even more complex for enterprises to solve.
            There's a never ending set of libraries to keep up with, tools to leverage, and sprawling infrastructure to secure.
        """.trimIndent()

        val result = SmartReplyEngine.generateReplies(
            postText = post,
            authorName = "Aaron Levie",
            authorHandle = "levie",
        )

        assertEquals(4, result.options.size)

        val insight = result.options.first { it.angle == SmartReplyAngle.Insight }
        val question = result.options.first { it.angle == SmartReplyAngle.Question }
        val counterpoint = result.options.first { it.angle == SmartReplyAngle.Counterpoint }
        val punchy = result.options.first { it.angle == SmartReplyAngle.Punchy }

        assertTrue(insight.replyText.isNotBlank())
        assertTrue(question.replyText.isNotBlank())
        assertTrue(counterpoint.replyText.isNotBlank())
        assertTrue(punchy.replyText.isNotBlank())

        // Verify question angle ends with question mark
        assertTrue(question.replyText.trim().endsWith("?"))

        // Verify counterpoint offers nuance
        assertTrue(counterpoint.replyText.contains("startups") || counterpoint.replyText.contains("twist") || counterpoint.replyText.contains("tradeoff"))

        // Verify zero banned buzzwords
        OmniScribeEngine.BannedAiTropes.forEach { trope ->
            assertFalse(
                insight.replyText.lowercase().contains(trope),
                "Insight reply contains banned trope: $trope"
            )
            assertFalse(
                question.replyText.lowercase().contains(trope),
                "Question reply contains banned trope: $trope"
            )
        }
    }

    @Test
    fun testGeneratesRepliesForMovieCriticsPost() {
        val post = "Talk of a critic-audience divide, it turns out, is overblown. For most of the past 20 years, reviewers and moviegoers have been in broad agreement."

        val result = SmartReplyEngine.generateReplies(
            postText = post,
            authorName = "The Economist",
            authorHandle = "TheEconomist",
        )

        assertEquals(4, result.options.size)
        val question = result.options.first { it.angle == SmartReplyAngle.Question }
        assertTrue(question.replyText.contains("?") && (question.replyText.contains("Rotten") || question.replyText.contains("divide") || question.replyText.contains("critic")))
    }
}
