package org.crux.data.ai.scribe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Guards the "sounds like a real person" bar for smart replies: no AI preambles, no praise
 * openers, no em dashes, no hashtags, short enough to be a reply, and grounded in the post.
 */
class SmartReplyHumanVoiceTest {
    private val rustPost =
        "We spent the last 6 months rewriting our core routing engine in Rust, " +
            "cutting p99 tail latency from 85ms down to 12ms."

    private val corpus =
        listOf(
            rustPost,
            "Talk of a critic-audience divide, it turns out, is overblown. " +
                "For most of the past 20 years, reviewers and moviegoers have been in broad agreement.",
            "BTR New Material Group showcases breakthrough solid-state battery cathode advancements. " +
                "With up to 400 Wh/kg energy density, commercial electrification is accelerating. #batterytech",
            "After six months of grueling physical therapy following a catastrophic accident, " +
                "I took my first unassisted steps today. Never give up.",
            "Threema Work introduces centralized administration for modern enterprise teams.",
            "Finally finished repainting the kitchen. Took three weekends and way too much coffee.",
            "Hot take: most standups should be a Slack message.",
        )

    private val praiseOpeners =
        listOf(
            "a very",
            "such a",
            "what a",
            "great",
            "love",
            "really appreciate",
            "it's refreshing",
            "spot on",
            "totally agree",
            "absolutely",
            "well said",
            "fascinating",
            "insightful",
            "this is a great",
            "brilliant",
            "excellent",
        )

    private val corporateTells =
        listOf(
            "the real moat",
            "masterclass",
            "high-signal",
            "high signal",
            "zero fluff",
            "respect the craft",
            "ship the signal",
            "cut the vanity",
            "compounds silently",
            "let the results speak",
            "operational friction",
            "real-world operational",
            "in practice is moving past",
        )

    // --- Prompt ---

    @Test
    fun promptDropsHypePersonaAndBannedPhraseDump() {
        val prompt = SmartReplyEngine.buildGemmaPrompt(postText = rustPost, authorName = "FastRoute")
        assertFalse(prompt.contains("elite", ignoreCase = true))
        assertFalse(prompt.contains("top 10%"))
        // Listing banned phrases primes small models to use them.
        assertFalse(prompt.contains("tapestry"))
        assertFalse(prompt.contains("game-changer"))
    }

    @Test
    fun promptGivesConcreteStyleRulesExampleAndLengthLimits() {
        val prompt = SmartReplyEngine.buildGemmaPrompt(postText = rustPost, tone = SmartReplyTone.Casual)
        assertTrue(prompt.contains("Example"))
        assertTrue(prompt.contains("No warm-up"))
        assertTrue(prompt.contains("words"))
        assertTrue(prompt.contains("Voice: ${SmartReplyTone.Casual.displayName}"))
        assertTrue(prompt.contains(SmartReplyTone.Casual.promptDirective))
        // Markers the offline runtime and parser depend on stay in place.
        assertTrue(prompt.contains("SOURCE POST:"))
        assertTrue(prompt.contains("OUTPUT FORMAT:"))
        listOf("[INSIGHT]:", "[QUESTION]:", "[COUNTERPOINT]:", "[PUNCHY]:").forEach {
            assertTrue(prompt.contains(it), "missing $it")
        }
    }

    // --- Cleanup of model output ---

    private fun parseSingleInsight(text: String): String {
        val parsed =
            SmartReplyEngine.parseGemmaReplies(
                """
                [INSIGHT]: $text
                [QUESTION]: Did the rewrite change your on-call load?
                [COUNTERPOINT]: Six months is a lot of roadmap to spend.
                [PUNCHY]: 85 to 12 is wild.
                """.trimIndent(),
            )
        assertNotNull(parsed)
        return parsed.first { it.angle == SmartReplyAngle.Insight }.replyText
    }

    @Test
    fun stripsPraiseOrSummaryPreambleSentence() {
        assertEquals(
            "Most of that p99 win is probably from losing GC pauses.",
            parseSingleInsight("A very sharp perspective on Rust rewrites. Most of that p99 win is probably from losing GC pauses."),
        )
        assertEquals(
            "Curious if the allocator mattered more than the language.",
            parseSingleInsight(
                "It's refreshing to see someone articulate this clearly. Curious if the allocator mattered more than the language.",
            ),
        )
    }

    @Test
    fun keepsSinglePraiseSentenceRatherThanReturningNothing() {
        assertEquals("Great result.", parseSingleInsight("Great result."))
    }

    @Test
    fun replacesEmDashesButKeepsNumberRanges() {
        assertEquals(
            "Big win, most of it is the allocator.",
            parseSingleInsight("Big win—most of it is the allocator."),
        )
        assertEquals("Took us 6-8 weeks too.", parseSingleInsight("Took us 6–8 weeks too."))
    }

    @Test
    fun removesHashtagsAndExtraExclamations() {
        assertEquals(
            "85 to 12ms is wild. Nice work.",
            parseSingleInsight("85 to 12ms is wild!!! Nice work! #rustlang #performance"),
        )
    }

    @Test
    fun dropsSentencesBuiltAroundAiTropes() {
        assertEquals(
            "How are you handling retries now?",
            parseSingleInsight("This is a total game-changer for the ecosystem. How are you handling retries now?"),
        )
    }

    @Test
    fun capsRambleAtASentenceBoundary() {
        val ramble = (1..12).joinToString(" ") { "Sentence number $it adds one more thought here." }
        val result = parseSingleInsight(ramble)
        assertTrue(result.split(" ").size <= SmartReplyEngine.MAX_REPLY_WORDS, "too long: $result")
        assertTrue(result.endsWith("."))
    }

    @Test
    fun missingAnglesAreFilledFromTheSourcePostNotTheModelOutput() {
        val battery = corpus[2]
        val parsed =
            SmartReplyEngine.parseGemmaReplies(
                raw = "[INSIGHT]: 400 Wh/kg is the number everyone will quote, cycle life is the one to watch.",
                sourcePostText = battery,
            )
        assertNotNull(parsed)
        val question = parsed.first { it.angle == SmartReplyAngle.Question }.replyText
        assertTrue(
            listOf("thermal", "cycle", "density", "battery", "charging").any { question.contains(it, ignoreCase = true) },
            "question not grounded in the battery post: $question",
        )
    }

    // --- Offline fallback quality ---

    @Test
    fun offlineRepliesReadLikePeopleAcrossPostsAndTones() {
        for (post in corpus) {
            for (tone in SmartReplyTone.entries) {
                val outputs =
                    SmartReplyEngine.generateReplies(postText = post, tone = tone).options +
                        SmartReplyEngine.parseGemmaReplies(
                            SmartReplyEngine.generateGemmaRefinedOutput(postText = post, tone = tone),
                            tone = tone,
                        )!!
                for (option in outputs) {
                    val text = option.replyText
                    val lower = text.lowercase()
                    val where = "[$tone/${option.angle}] \"$text\" for post \"${post.take(40)}\""
                    assertFalse(text.contains('—') || text.contains('–'), "em dash $where")
                    assertFalse(text.contains('#'), "hashtag $where")
                    assertTrue(praiseOpeners.none { Regex("^${Regex.escape(it)}\\b").containsMatchIn(lower) }, "praise opener $where")
                    assertTrue(corporateTells.none { lower.contains(it) }, "corporate tell $where")
                    OmniScribeEngine.BannedAiTropes.forEach { assertFalse(lower.contains(it), "trope '$it' $where") }
                    assertTrue(text.split(" ").size <= SmartReplyEngine.MAX_REPLY_WORDS, "too long $where")
                    if (option.angle == SmartReplyAngle.Question) assertTrue(text.endsWith("?"), "question without ? $where")
                    if (option.angle == SmartReplyAngle.Punchy) assertTrue(text.split(" ").size <= 16, "punchy too long $where")
                }
            }
        }
    }

    @Test
    fun everyToneStaysGroundedInThePost() {
        val postTerms = listOf("rust", "p99", "latency", "85", "12ms", "routing", "rewrite", "six months", "6 months")
        for (tone in SmartReplyTone.entries) {
            val replies = SmartReplyEngine.generateReplies(postText = rustPost, tone = tone).options.map { it.replyText }
            val grounded = replies.count { r -> postTerms.any { r.contains(it, ignoreCase = true) } }
            assertTrue(grounded >= 2, "$tone replies ignore the post: $replies")
            assertTrue(replies.none { it.contains("compared to the previous setup") }, "$tone uses a generic canned line")
        }
    }

    @Test
    fun toneAdaptationPreservesPronounCaseAndAvoidsDuplicateLeadIns() {
        val lowercaseFirstPerson = Regex("\\bi(?:'[a-z]+)?\\b")
        val duplicateHonestly = Regex("honestly[,\\s]+honestly|playing devil's advocate[,\\s]+honestly", RegexOption.IGNORE_CASE)
        for (post in corpus) {
            for (tone in SmartReplyTone.entries) {
                val outputs =
                    SmartReplyEngine.generateReplies(postText = post, tone = tone).options +
                        SmartReplyEngine.parseGemmaReplies(
                            SmartReplyEngine.generateGemmaRefinedOutput(postText = post, tone = tone),
                            tone = tone,
                            sourcePostText = post,
                        )!!
                for (option in outputs) {
                    val text = option.replyText
                    val where = "[$tone/${option.angle}] \"$text\""
                    assertTrue(text.isNotBlank(), "blank reply $where")
                    assertFalse(lowercaseFirstPerson.containsMatchIn(text), "lowercase 'i' $where")
                    assertFalse(duplicateHonestly.containsMatchIn(text), "duplicate lead-in $where")
                }
            }
        }
    }

    @Test
    fun parseGemmaRepliesRecoversWhenHumanizerStripsAllPraise() {
        val parsed =
            SmartReplyEngine.parseGemmaReplies(
                raw = "[INSIGHT]: Spot on!\n[QUESTION]: Totally agree?\n[COUNTERPOINT]: Great post.\n[PUNCHY]: Brilliant!",
                tone = SmartReplyTone.Casual,
                sourcePostText = rustPost,
            )
        assertNotNull(parsed)
        assertEquals(4, parsed.size)
        parsed.forEach { option ->
            assertTrue(option.replyText.isNotBlank(), "Expected fallback instead of blank for ${option.angle}")
        }
    }

    @Test
    fun refinedOutputsCoverAllModalitiesWithoutGenericYearsFallback() {
        val domainPosts =
            listOf(
                "Finally finished repainting the kitchen. Took three weekends and way too much coffee.",
                "Hot take: most standups should be a Slack message.",
                "JWST spectroscopy revealed water vapor signatures in a rocky exoplanet atmosphere.",
                "Finished my marathon training block with 70 miles this week and steady aerobic splits.",
                "Saw the director's cut of Dune last night in IMAX and the sound mix hit different.",
                "Anyone here migrated from Postgres 14 to 17 on a live box? Curious about extension gotchas.",
                "My dog just barked at a reflection of a spoon for ten straight minutes.",
            )
        for (post in domainPosts) {
            val raw = SmartReplyEngine.generateGemmaRefinedOutput(postText = post)
            val parsed = SmartReplyEngine.parseGemmaReplies(raw, sourcePostText = post)!!
            for (option in parsed) {
                assertFalse(
                    option.replyText.contains("next couple of years", ignoreCase = true),
                    "Used generic 'next couple of years' fallback for \"$post\": ${option.replyText}",
                )
            }
        }
    }

    @Test
    fun durationsLikeEveryThreeYearsNeverBecomeBenchmarkJump() {
        val cngPost =
            "Why the 1,900-km rule means most private CNG buyers are actually losing money\n" +
                "Every 3 years, government rules mandate cylinder hydrostatic testing."
        for (tone in SmartReplyTone.entries) {
            val replies = SmartReplyEngine.generateReplies(postText = cngPost, tone = tone).options.map { it.replyText }
            for (reply in replies) {
                assertFalse(
                    reply.contains("3 years", ignoreCase = true) &&
                        (reply.contains("jump", ignoreCase = true) || reply.contains("wild", ignoreCase = true)),
                    "Misframed '3 years' as a jump in $tone: $reply",
                )
            }
        }
    }
}
