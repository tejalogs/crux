package dev.dimension.flare.data.ai.scribe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
        assertEquals("ENTERPRISE SECURITY", result.detectedTheme)

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
                "Insight reply contains banned trope: $trope",
            )
            assertFalse(
                question.replyText.lowercase().contains(trope),
                "Question reply contains banned trope: $trope",
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
        assertEquals("CRITIC & CULTURE", result.detectedTheme)
        val question = result.options.first { it.angle == SmartReplyAngle.Question }
        assertTrue(question.replyText.contains("?") && (question.replyText.contains("Rotten") || question.replyText.contains("divide") || question.replyText.contains("critic")))
    }

    @Test
    fun testGeneratesDevotionalRepliesForHareKrishnaPost() {
        val post = "Hare Krishna"

        val result = SmartReplyEngine.generateReplies(
            postText = post,
            authorName = "Anu Satheesh",
            authorHandle = "AnuSatheesh5",
        )

        assertEquals(4, result.options.size)
        assertEquals("SACRED & DEVOTIONAL", result.detectedTheme)

        val insight = result.options.first { it.angle == SmartReplyAngle.Insight }
        val question = result.options.first { it.angle == SmartReplyAngle.Question }
        val counterpoint = result.options.first { it.angle == SmartReplyAngle.Counterpoint }
        val punchy = result.options.first { it.angle == SmartReplyAngle.Punchy }

        // Must not contain tech jargon or generic bot clichés
        assertFalse(insight.replyText.contains("token generation"))
        assertFalse(insight.replyText.contains("moving parts"))
        assertFalse(counterpoint.replyText.contains("startup graveyard"))

        // Must be respectful, serene, and devotional
        assertTrue(insight.replyText.contains("serene") || insight.replyText.contains("darshan") || insight.replyText.contains("peace"))
        assertTrue(question.replyText.endsWith("?"))
        assertTrue(punchy.replyText.contains("Krishna") || punchy.replyText.contains("peace"))
    }

    @Test
    fun testGeneratesWeatherEmergencyRepliesForCyclonesPost() {
        val post = "#WATCH | Thoothukudi, Tamil Nadu: Mechanised boat fishermen stay ashore following a cyclone warning issued for Thoothukudi district."

        val result = SmartReplyEngine.generateReplies(
            postText = post,
            authorName = "ANI",
            authorHandle = "ANI",
        )

        assertEquals(4, result.options.size)
        assertEquals("COASTAL WEATHER ALERT", result.detectedTheme)

        val insight = result.options.first { it.angle == SmartReplyAngle.Insight }
        val question = result.options.first { it.angle == SmartReplyAngle.Question }
        val counterpoint = result.options.first { it.angle == SmartReplyAngle.Counterpoint }
        val punchy = result.options.first { it.angle == SmartReplyAngle.Punchy }

        // Must be empathetic, focused on fishermen and safety
        assertTrue(insight.replyText.contains("Safety") || insight.replyText.contains("cyclone") || insight.replyText.contains("ashore"))
        assertTrue(question.replyText.contains("shelter") || question.replyText.contains("relief") || question.replyText.contains("coastal"))
        assertTrue(counterpoint.replyText.contains("fishermen") || counterpoint.replyText.contains("daily-wage") || counterpoint.replyText.contains("relief"))
        assertTrue(punchy.replyText.contains("storm") || punchy.replyText.contains("safety") || punchy.replyText.contains("Thoothukudi"))
    }

    @Test
    fun testGeneratesEmbeddedHardwareRepliesForDekiEnginePost() {
        val post = "There is a new Open Source Unity-Style Game Engine ... but for ESP32 devices. Deki Engine is an Apache 2.0 modular C++ 2D game engine with a visual editor for ESP32-S3 embedded devices, hitting 60 FPS on hardware. Available for Linux."

        val result = SmartReplyEngine.generateReplies(
            postText = post,
            authorName = "It's FOSS",
            authorHandle = "Itsfoss",
        )

        assertEquals(4, result.options.size)
        assertEquals("EMBEDDED HARDWARE", result.detectedTheme)

        val insight = result.options.first { it.angle == SmartReplyAngle.Insight }
        val question = result.options.first { it.angle == SmartReplyAngle.Question }
        val counterpoint = result.options.first { it.angle == SmartReplyAngle.Counterpoint }
        val punchy = result.options.first { it.angle == SmartReplyAngle.Punchy }

        // Must accurately reference ESP32, 60 FPS, visual editor, or C++
        assertTrue(insight.replyText.contains("60 FPS") || insight.replyText.contains("ESP32") || insight.replyText.contains("microcontroller"))
        assertTrue(question.replyText.contains("SRAM") || question.replyText.contains("PSRAM") || question.replyText.contains("engine") || question.replyText.contains("Deki"))
        assertTrue(counterpoint.replyText.contains("ESP32") || counterpoint.replyText.contains("microcontroller") || counterpoint.replyText.contains("memory"))
        assertTrue(punchy.replyText.contains("60 FPS") || punchy.replyText.contains("embedded") || punchy.replyText.contains("visual editor"))
    }

    @Test
    fun testGeneratesCivilLibertiesRepliesForEffBrazilPost() {
        val post = "Learn out. EFF to Brazil’s ANPD: Data Privacy and Free Expression Must Underpin Platform Regulations eff.org/deeplinks/2026/10/eff-... #tech #digital #privacy #crypto #ai."

        val result = SmartReplyEngine.generateReplies(
            postText = post,
            authorName = "Kohei Kurihara",
            authorHandle = "kurihara",
        )

        assertEquals(4, result.options.size)
        assertEquals("CIVIL LIBERTIES & PRIVACY", result.detectedTheme)

        val insight = result.options.first { it.angle == SmartReplyAngle.Insight }
        val question = result.options.first { it.angle == SmartReplyAngle.Question }
        val counterpoint = result.options.first { it.angle == SmartReplyAngle.Counterpoint }
        val punchy = result.options.first { it.angle == SmartReplyAngle.Punchy }

        // Must not be confused by #ai hashtag into rambling about tokens
        assertFalse(insight.replyText.contains("token generation"))
        assertFalse(insight.replyText.contains("sleepwalking off an operational cliff"))

        // Must talk about EFF, ANPD, privacy, or free expression
        assertTrue(insight.replyText.contains("EFF") || insight.replyText.contains("ANPD") || insight.replyText.contains("encryption"))
        assertTrue(question.replyText.contains("ANPD") || question.replyText.contains("EFF") || question.replyText.contains("civil liberties"))
        assertTrue(counterpoint.replyText.contains("platforms") || counterpoint.replyText.contains("federated") || counterpoint.replyText.contains("compliance"))
        assertTrue(punchy.replyText.contains("Privacy") || punchy.replyText.contains("expression") || punchy.replyText.contains("bedrock"))
    }

    @Test
    fun testGemmaPromptAndParsing() {
        val prompt = SmartReplyEngine.buildGemmaPrompt(
            postText = "Deki Engine 60 FPS on ESP32-S3",
            authorName = "TechDev",
        )
        assertTrue(prompt.contains("Deki Engine 60 FPS on ESP32-S3"))
        assertTrue(prompt.contains("[INSIGHT]:"))
        assertTrue(prompt.contains("[QUESTION]:"))

        val mockGemmaResponse = """
            [INSIGHT]: Running a full 2D engine with a visual editor on an ESP32 is a major achievement for embedded retro dev.
            [QUESTION]: What is the peak SRAM consumption when running multiple particle systems?
            [COUNTERPOINT]: Tooling is great, but debugging memory leaks without JTAG on custom hardware can be brutal.
            [PUNCHY]: 60 FPS on microcontroller silicon is magnificent.
        """.trimIndent()

        val parsed = SmartReplyEngine.parseGemmaReplies(mockGemmaResponse)
        assertNotNull(parsed)
        assertEquals(4, parsed.size)
        assertEquals("Running a full 2D engine with a visual editor on an ESP32 is a major achievement for embedded retro dev.", parsed[0].replyText)
        assertEquals(SmartReplyAngle.Insight, parsed[0].angle)
        assertEquals(SmartReplyAngle.Question, parsed[1].angle)
        assertEquals(SmartReplyAngle.Counterpoint, parsed[2].angle)
        assertEquals(SmartReplyAngle.Punchy, parsed[3].angle)
    }
}
