package dev.dimension.flare.data.ai.scribe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SmartReplyEngineTest {
    @Test
    fun testGeneratesFourDistinctReplyAnglesForTechSecurityPost() {
        val post =
            """
            Good assessment of the on the ground reality for security in most large enterprises right now.
            The fragmented landscape of the risk surface is only going to get more gnarly,
            making it even more complex for enterprises to solve.
            There's a never ending set of libraries to keep up with, tools to leverage, and sprawling infrastructure to secure.
            """.trimIndent()

        val result =
            SmartReplyEngine.generateReplies(
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
        assertTrue(
            counterpoint.replyText.contains("startups") || counterpoint.replyText.contains("twist") ||
                counterpoint.replyText.contains("tradeoff"),
        )

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
        val post =
            "Talk of a critic-audience divide, it turns out, is overblown. " +
                "For most of the past 20 years, reviewers and moviegoers have been in broad agreement."

        val result =
            SmartReplyEngine.generateReplies(
                postText = post,
                authorName = "The Economist",
                authorHandle = "TheEconomist",
            )

        assertEquals(4, result.options.size)
        assertEquals("CRITIC & CULTURE", result.detectedTheme)
        val question = result.options.first { it.angle == SmartReplyAngle.Question }
        assertTrue(
            question.replyText.contains("?") &&
                (question.replyText.contains("Rotten") || question.replyText.contains("divide") || question.replyText.contains("critic")),
        )
    }

    @Test
    fun testGeneratesDevotionalRepliesForHareKrishnaPost() {
        val post = "Hare Krishna"

        val result =
            SmartReplyEngine.generateReplies(
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
        val post =
            "#WATCH | Thoothukudi, Tamil Nadu: Mechanised boat fishermen " +
                "stay ashore following a cyclone warning issued for Thoothukudi district."

        val result =
            SmartReplyEngine.generateReplies(
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
        assertTrue(
            question.replyText.contains("shelter") || question.replyText.contains("relief") || question.replyText.contains("coastal"),
        )
        assertTrue(
            counterpoint.replyText.contains("fishermen") || counterpoint.replyText.contains("daily-wage") ||
                counterpoint.replyText.contains("relief"),
        )
        assertTrue(punchy.replyText.contains("storm") || punchy.replyText.contains("safety") || punchy.replyText.contains("Thoothukudi"))
    }

    @Test
    fun testGeneratesEmbeddedHardwareRepliesForDekiEnginePost() {
        val post =
            "There is a new Open Source Unity-Style Game Engine ... but for ESP32 devices. " +
                "Deki Engine is an Apache 2.0 modular C++ 2D game engine with a visual editor " +
                "for ESP32-S3 embedded devices, hitting 60 FPS on hardware. Available for Linux."

        val result =
            SmartReplyEngine.generateReplies(
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
        assertTrue(
            insight.replyText.contains("60 FPS") || insight.replyText.contains("ESP32") || insight.replyText.contains("microcontroller"),
        )
        assertTrue(
            question.replyText.contains("SRAM") || question.replyText.contains("PSRAM") || question.replyText.contains("engine") ||
                question.replyText.contains("Deki"),
        )
        assertTrue(
            counterpoint.replyText.contains("ESP32") || counterpoint.replyText.contains("microcontroller") ||
                counterpoint.replyText.contains("memory"),
        )
        assertTrue(
            punchy.replyText.contains("60 FPS") || punchy.replyText.contains("embedded") || punchy.replyText.contains("visual editor"),
        )
    }

    @Test
    fun testGeneratesCivilLibertiesRepliesForEffBrazilPost() {
        val post =
            "Learn out. EFF to Brazil’s ANPD: Data Privacy and Free Expression " +
                "Must Underpin Platform Regulations eff.org/deeplinks/2026/10/eff-... " +
                "#tech #digital #privacy #crypto #ai."

        val result =
            SmartReplyEngine.generateReplies(
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
        assertTrue(
            question.replyText.contains("ANPD") || question.replyText.contains("EFF") || question.replyText.contains("civil liberties"),
        )
        assertTrue(
            counterpoint.replyText.contains("platforms") || counterpoint.replyText.contains("federated") ||
                counterpoint.replyText.contains("compliance"),
        )
        assertTrue(punchy.replyText.contains("Privacy") || punchy.replyText.contains("expression") || punchy.replyText.contains("bedrock"))
    }

    @Test
    fun testGemmaPromptAndParsing() {
        val prompt =
            SmartReplyEngine.buildGemmaPrompt(
                postText = "Deki Engine 60 FPS on ESP32-S3",
                authorName = "TechDev",
            )
        assertTrue(prompt.contains("Deki Engine 60 FPS on ESP32-S3"))
        assertTrue(prompt.contains("[INSIGHT]:"))
        assertTrue(prompt.contains("[QUESTION]:"))

        val mockGemmaResponse =
            """
            [INSIGHT]: Running a full 2D engine with a visual editor on an ESP32 is a major achievement for embedded retro dev.
            [QUESTION]: What is the peak SRAM consumption when running multiple particle systems?
            [COUNTERPOINT]: Tooling is great, but debugging memory leaks without JTAG on custom hardware can be brutal.
            [PUNCHY]: 60 FPS on microcontroller silicon is magnificent.
            """.trimIndent()

        val parsed = SmartReplyEngine.parseGemmaReplies(mockGemmaResponse)
        assertNotNull(parsed)
        assertEquals(4, parsed.size)
        assertEquals(
            "Running a full 2D engine with a visual editor on an ESP32 is a major achievement for embedded retro dev.",
            parsed[0].replyText,
        )
        assertEquals(SmartReplyAngle.Insight, parsed[0].angle)
        assertEquals(SmartReplyAngle.Question, parsed[1].angle)
        assertEquals(SmartReplyAngle.Counterpoint, parsed[2].angle)
        assertEquals(SmartReplyAngle.Punchy, parsed[3].angle)
    }

    @Test
    fun testGeneratesCivicPolicyRepliesWithoutVerbEntityOrQuoteParroting() {
        val post =
            "Speaking in the favour of national project is paid PR now WOW MAN. " +
                "They hate BJP so much that now they started hating India."

        val result =
            SmartReplyEngine.generateReplies(
                postText = post,
                authorName = "Rishi",
                authorHandle = "rishi_ind",
            )

        assertEquals(4, result.options.size)
        assertEquals("CIVIC & NATIONAL POLICY", result.detectedTheme)

        // Sentence starter "Speaking" must not be extracted as an entity or title badge
        result.options.forEach { option ->
            assertFalse(option.title.equals("SPEAKING", ignoreCase = true))
            // Must never parrot the raw post inside quotes
            assertFalse(option.replyText.contains("\"Speaking in the favour"))
            assertFalse(option.replyText.contains("operational leverage"))
        }

        val refined =
            SmartReplyEngine.generateGemmaRefinedOutput(
                SmartReplyEngine.buildGemmaPrompt(post, "Rishi"),
            )
        val parsedRefined = SmartReplyEngine.parseGemmaReplies(refined)
        assertNotNull(parsedRefined)
        assertEquals(4, parsedRefined.size)
    }

    @Test
    fun testGemmaPromptEnrichedWithMediaDescriptionAndParentContext() {
        val prompt =
            SmartReplyEngine.buildGemmaPrompt(
                postText = "",
                authorName = "AeroSpace",
                authorHandle = "aero",
                mediaDescription = "Composite carbon wing prototype undergoing vibration fatigue testing in wind tunnel",
                parentDiscussionContext = "Discussing thermal and stress limits on supersonic test articles",
            )

        assertTrue(prompt.contains("ATTACHED MEDIA / VISUAL CONTENT:"))
        assertTrue(prompt.contains("Composite carbon wing prototype undergoing vibration fatigue testing in wind tunnel"))
        assertTrue(prompt.contains("PARENT THREAD / CONTEXT:"))
        assertTrue(prompt.contains("Discussing thermal and stress limits on supersonic test articles"))
        assertTrue(prompt.contains("No warm-up"))
        assertTrue(prompt.contains("Don't compliment the post or summarise it back"))
    }

    @Test
    fun testSanitizeReplyStripsCannedClichés() {
        val rawGemmaResponse =
            """
            [INSIGHT]: Spot on! The modular design significantly reduces compile-time dependency overhead.
            [QUESTION]: Totally agree, what is the maximum throughput under concurrent writes?
            [COUNTERPOINT]: 100% this, but maintaining backwards compatibility might require double buffering.
            [PUNCHY]: Love this! Exceptional architectural rigor.
            """.trimIndent()

        val parsed = SmartReplyEngine.parseGemmaReplies(rawGemmaResponse)
        assertNotNull(parsed)
        assertEquals(4, parsed.size)

        // Canned openers like "Spot on!", "Totally agree,", "100% this,", "Love this!" must be cleanly stripped
        assertEquals("The modular design significantly reduces compile-time dependency overhead.", parsed[0].replyText)
        assertEquals("What is the maximum throughput under concurrent writes?", parsed[1].replyText)
        assertEquals("But maintaining backwards compatibility might require double buffering.", parsed[2].replyText)
        assertEquals("Exceptional architectural rigor.", parsed[3].replyText)
    }

    @Test
    fun testVisualPostWithoutCaptionUsesAltTextForGrounding() {
        val result =
            SmartReplyEngine.generateReplies(
                postText = "",
                authorName = "RoboticsLab",
                authorHandle = "robotics",
                mediaDescription = "Autonomous quadruped robot walking over uneven rocky terrain with LiDAR mapping",
            )

        assertEquals(4, result.options.size)
        // Must identify technology/engineering modality from media description
        assertNotNull(result.detectedTheme)
        assertTrue(result.options.any { it.replyText.isNotBlank() })
    }

    @Test
    fun testTimDenningQuoteClassifiedAsLifeMindsetNotAI() {
        val post =
            "“There is no normal/average. A 'normal person' is what is left after society " +
                "has squeezed out all unconventional opinions and aspirations out of a human being.”"
        val result =
            SmartReplyEngine.generateReplies(
                postText = post,
                authorName = "Tim Denning",
                authorHandle = "timdenning@linkedin.com",
            )

        assertEquals(4, result.options.size)
        // Must NOT falsely match "rag" inside "average" and trigger "APPLIED AI"
        assertEquals("MINDSET & CRAFT", result.detectedTheme)
        val insight = result.options.first { it.angle == SmartReplyAngle.Insight }
        assertFalse(insight.replyText.contains("frontier in AI"))
        assertTrue(
            insight.replyText.contains(
                "unconventional",
            ) || insight.replyText.contains("originality") || insight.replyText.contains("focus"),
        )
    }

    @Test
    fun testBtrBatteryPostClassifiedAsCleanTechEnergy() {
        val post =
            "BTR New Material Group showcases breakthrough solid-state battery cathode " +
                "advancements at the International Battery Seminar. With up to 400 Wh/kg energy density, " +
                "commercial electrification is accelerating. #batterytech #cleantech #EV"
        val result =
            SmartReplyEngine.generateReplies(
                postText = post,
                authorName = "BTR New Material Group",
                authorHandle = "btr_tech",
            )

        assertEquals(4, result.options.size)
        assertEquals("ENERGY & CLEANTECH", result.detectedTheme)

        val insight = result.options.first { it.angle == SmartReplyAngle.Insight }
        val question = result.options.first { it.angle == SmartReplyAngle.Question }
        val counterpoint = result.options.first { it.angle == SmartReplyAngle.Counterpoint }
        val punchy = result.options.first { it.angle == SmartReplyAngle.Punchy }

        assertTrue(
            insight.replyText.contains("battery") || insight.replyText.contains("energy storage") ||
                insight.replyText.contains("electrification"),
        )
        assertTrue(
            question.replyText.contains("thermal") || question.replyText.contains("cycle life") || question.replyText.contains("density"),
        )
        assertTrue(
            counterpoint.replyText.contains("thermal runaway") || counterpoint.replyText.contains("recycling") ||
                counterpoint.replyText.contains("gigawatt"),
        )
        assertTrue(punchy.replyText.contains("batteries") || punchy.replyText.contains("chemistry") || punchy.replyText.contains("storage"))
    }

    @Test
    fun testCivilLibertiesDoesNotHallucinateEffWhenNotMentioned() {
        val post = "European data privacy regulators have issued new guidance on automated algorithmic profiling under GDPR."
        val result =
            SmartReplyEngine.generateReplies(
                postText = post,
                authorName = "PrivacyWatch",
                authorHandle = "privacy_eu",
            )

        assertEquals(4, result.options.size)
        assertEquals("CIVIL LIBERTIES & PRIVACY", result.detectedTheme)

        val insight = result.options.first { it.angle == SmartReplyAngle.Insight }
        val question = result.options.first { it.angle == SmartReplyAngle.Question }

        // Must NOT hallucinate EFF or ANPD since neither is in the post
        assertFalse(insight.replyText.contains("EFF"))
        assertFalse(insight.replyText.contains("ANPD"))
        assertFalse(question.replyText.contains("ANPD"))
    }

    @Test
    fun testHumanResilienceDoesNotHallucinateBlueprintsForHealthRecovery() {
        val post =
            "After six months of grueling physical therapy in the hospital following a catastrophic " +
                "accident, I took my first unassisted steps today. Never give up."
        val result =
            SmartReplyEngine.generateReplies(
                postText = post,
                authorName = "Sarah Jenkins",
                authorHandle = "sarah_j",
            )

        assertEquals(4, result.options.size)
        assertEquals("HUMAN RESILIENCE", result.detectedTheme)

        val insight = result.options.first { it.angle == SmartReplyAngle.Insight }
        val question = result.options.first { it.angle == SmartReplyAngle.Question }

        // Must NOT hallucinate mechanical blueprints, CAD files, or patents
        assertFalse(insight.replyText.contains("blueprints"))
        assertFalse(insight.replyText.contains("patent"))
        assertFalse(question.replyText.contains("patent"))
        assertTrue(
            insight.replyText.contains("resilience") || insight.replyText.contains("recovery") || insight.replyText.contains("adversity"),
        )
    }

    @Test
    fun testCircularEconomyPostClassifiedAsCleanTechEnergy() {
        val post =
            """
            🎙️ Expert Talk Series with Calvin Lakhan, Ph.D | Episode 1
            We're excited to launch our new Expert Talk Series with Calvin Lakhan, Ph.D, Founder and Director
            of the Circular Innovation Hub at York University’s Faculty of Environmental and Urban Change.
            His research focuses on how waste and materials management systems work in practice and how
            circular economy policies can help keep valuable resources in use.
            The real challenge isn't plastics themselves. It's ensuring they stay out of the environment
            through effective end-of-life management and remain part of a circular economy.
            #PlasticIsFantastic #ExpertTalk #CalvinLakhan #CircularEconomy #Recycling #PlasticWaste #Sustainability
            """.trimIndent()

        val result =
            SmartReplyEngine.generateReplies(
                postText = post,
                authorName = "Plastic is Fantastic",
                authorHandle = "plasticisfantastic",
            )

        assertEquals(4, result.options.size)
        // Must NOT match "founder" into startup SaaS marketing
        assertEquals("ENERGY & CLEANTECH", result.detectedTheme)

        val insight = result.options.first { it.angle == SmartReplyAngle.Insight }
        val question = result.options.first { it.angle == SmartReplyAngle.Question }
        val counterpoint = result.options.first { it.angle == SmartReplyAngle.Counterpoint }
        val punchy = result.options.first { it.angle == SmartReplyAngle.Punchy }

        assertFalse(insight.replyText.contains("distribution"))
        assertFalse(insight.replyText.contains("extra features"))
        assertTrue(
            insight.replyText.contains("circular economy") || insight.replyText.contains("materials management") ||
                insight.replyText.contains("sustainability"),
        )
        assertTrue(
            question.replyText.contains("material") || question.replyText.contains("supply chains") ||
                question.replyText.contains("sorting"),
        )
        assertTrue(
            counterpoint.replyText.contains("recyclables") || counterpoint.replyText.contains("producer responsibility") ||
                counterpoint.replyText.contains("circular"),
        )
        assertTrue(punchy.replyText.contains("circular") || punchy.replyText.contains("resources"))
    }

    @Test
    fun testThreemaEnterprisePostIsNotMisclassifiedAsDisasterOrExploitThreat() {
        val post =
            "Threema Work introduces centralized administration for modern enterprise teams. " +
                "Administrators can now configure security policies and deployment profiles " +
                "effortlessly across desktop and mobile devices."
        val result =
            SmartReplyEngine.generateReplies(
                postText = post,
                authorName = "Threema",
                authorHandle = "threemaapp",
            )

        assertEquals(4, result.options.size)
        // Must NOT be misclassified as a coastal weather disaster
        assertFalse(result.detectedTheme.contains("WEATHER"))
        assertFalse(result.detectedTheme.contains("ALERT"))

        val insight = result.options.first { it.angle == SmartReplyAngle.Insight }
        val question = result.options.first { it.angle == SmartReplyAngle.Question }

        // Must not contain irrelevant canned disaster / cyclone or alert dashboard tropes
        assertFalse(insight.replyText.contains("coastal"))
        assertFalse(insight.replyText.contains("cyclone"))
        assertFalse(insight.replyText.contains("front door is propped open with a brick"))

        // Must ground on Threema, centralized administration, or enterprise
        assertTrue(
            insight.replyText.contains("Threema") ||
                insight.replyText.contains("administration") ||
                insight.replyText.contains("enterprise"),
        )
        assertTrue(question.replyText.endsWith("?"))
    }

    @Test
    fun testToneCustomizationAdaptsReplies() {
        val post = "We spent the last 6 months rewriting our core routing engine in Rust, cutting p99 tail latency from 85ms down to 12ms."

        val casualResult =
            SmartReplyEngine.generateReplies(
                postText = post,
                authorName = "FastRoute",
                tone = SmartReplyTone.Casual,
            )
        assertEquals(SmartReplyTone.Casual, casualResult.tone)
        val casualPunchy = casualResult.options.first { it.angle == SmartReplyAngle.Punchy }
        // Casual stays anchored to the post's own numbers instead of a generic cheer.
        assertTrue(casualPunchy.replyText.contains("85ms") || casualPunchy.replyText.contains("12ms"))

        val socraticResult =
            SmartReplyEngine.generateReplies(
                postText = post,
                authorName = "FastRoute",
                tone = SmartReplyTone.Socratic,
            )
        assertEquals(SmartReplyTone.Socratic, socraticResult.tone)
        val socraticQuestion = socraticResult.options.first { it.angle == SmartReplyAngle.Question }
        assertTrue(socraticQuestion.replyText.contains("assumption") || socraticQuestion.replyText.endsWith("?"))

        val executiveResult =
            SmartReplyEngine.generateReplies(
                postText = post,
                authorName = "FastRoute",
                tone = SmartReplyTone.Executive,
            )
        assertEquals(SmartReplyTone.Executive, executiveResult.tone)
        val execInsight = executiveResult.options.first { it.angle == SmartReplyAngle.Insight }
        assertTrue(execInsight.replyText.contains("85ms") || execInsight.replyText.contains("team"))

        val learnerResult =
            SmartReplyEngine.generateReplies(
                postText = post,
                authorName = "FastRoute",
                tone = SmartReplyTone.HumbleLearner,
            )
        assertEquals(SmartReplyTone.HumbleLearner, learnerResult.tone)
        val learnerQuestion = learnerResult.options.first { it.angle == SmartReplyAngle.Question }
        assertTrue(learnerQuestion.replyText.contains("differently") || learnerQuestion.replyText.contains("scratch"))
    }

    @Test
    fun testBuildGemmaPromptIncludesSelectedTone() {
        val post = "Decentralized identity with verifiable credentials is ready for production."
        val prompt =
            SmartReplyEngine.buildGemmaPrompt(
                postText = post,
                authorName = "Alice",
                tone = SmartReplyTone.Executive,
            )
        assertTrue(prompt.contains("Voice: Executive"))
        assertTrue(prompt.contains(SmartReplyTone.Executive.promptDirective))
    }

    @Test
    fun testNonReleaseSoftwarePostLabelledAsSoftwareCraft() {
        val post = "We spent the last 6 months rewriting our core routing engine in Rust, cutting p99 tail latency from 85ms down to 12ms."
        val result = SmartReplyEngine.generateReplies(postText = post)
        assertEquals("SOFTWARE CRAFT", result.detectedTheme)

        val kotlinPost = "Writing some Kotlin multiplatform today, clean architectures feel so good."
        val kotlinResult = SmartReplyEngine.generateReplies(postText = kotlinPost)
        assertEquals("SOFTWARE CRAFT", kotlinResult.detectedTheme)
    }

    @Test
    fun testOpenSourceReleasePostLabelledAsOpenSourceCraft() {
        val post = "Just open sourced our new compiler on github.com under Apache 2.0! Check it out on crates.io."
        val result = SmartReplyEngine.generateReplies(postText = post)
        assertEquals("OPEN SOURCE CRAFT", result.detectedTheme)
    }
}
