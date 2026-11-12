package org.crux.data.ai.scribe

import kotlinx.serialization.Serializable
import kotlin.time.TimeSource

/**
 * Angles for intelligent on-device contextual replies to social posts.
 */
@Serializable
public enum class SmartReplyAngle(
    public val displayName: String,
    public val subtitle: String,
    public val iconEmoji: String,
) {
    Insight(
        displayName = "Add Insight",
        subtitle = "Thoughtful domain perspective that enriches the conversation",
        iconEmoji = "💡",
    ),
    Question(
        displayName = "Curious Question",
        subtitle = "Engaging question that sparks authentic dialogue",
        iconEmoji = "❓",
    ),
    Counterpoint(
        displayName = "Constructive Nuance",
        subtitle = "Respectful alternative angle or subtle edge case",
        iconEmoji = "⚖️",
    ),
    Punchy(
        displayName = "Crisp Takeaway",
        subtitle = "Short, high-signal takeaway that reinforces the core point",
        iconEmoji = "⚡",
    ),
}

/**
 * Persona and tone modifiers for social media response generation.
 */
@Serializable
public enum class SmartReplyTone(
    public val id: String,
    public val displayName: String,
    public val subtitle: String,
    public val iconEmoji: String,
    public val promptDirective: String,
) {
    Practitioner(
        id = "practitioner",
        displayName = "Practitioner",
        subtitle = "Grounded depth & technical precision",
        iconEmoji = "🛠️",
        promptDirective = "You do this kind of work yourself. Talk specifics and trade-offs, not theory.",
    ),
    Casual(
        id = "casual",
        displayName = "Casual & Witty",
        subtitle = "Warm, banter-friendly, conversational",
        iconEmoji = "☕",
        promptDirective = "Relaxed and friendly, like replying to a friend. Light humour is fine if it fits the post.",
    ),
    Socratic(
        id = "socratic",
        displayName = "Socratic / Deep",
        subtitle = "Probing second-order questions & healthy debate",
        iconEmoji = "🧠",
        promptDirective = "Curious and a little challenging. Poke at assumptions and what happens next.",
    ),
    Executive(
        id = "executive",
        displayName = "Executive",
        subtitle = "High-signal, strategic, macro-level impact",
        iconEmoji = "📈",
        promptDirective = "Busy and to the point. You care about cost, people and what this means a year from now.",
    ),
    HumbleLearner(
        id = "learner",
        displayName = "Curious Learner",
        subtitle = "Eager inquiry into methodology & decisions",
        iconEmoji = "🌱",
        promptDirective = "Newer to this and genuinely curious. Ask how and why. Never fawning.",
    ),
}

@Serializable
public data class SmartReplyOption(
    public val angle: SmartReplyAngle,
    public val title: String,
    public val replyText: String,
)

@Serializable
public data class SmartReplyResult(
    public val options: List<SmartReplyOption>,
    public val detectedTheme: String,
    public val latencyMs: Long,
    public val tone: SmartReplyTone = SmartReplyTone.Practitioner,
)

/**
 * Zero-Egress On-Device Contextual Smart Reply Engine.
 * Deeply analyzes the true subject matter, domain, entities, and intent of any post
 * to generate 4 high-signal, top 10% human practitioner replies.
 *
 * Strict Guardrails:
 * - Zero AI tropes & robotic corporate formulas ("Spot on", "Here is the quiet truth underneath", "strip away secondary theater").
 * - True entity extraction (mentions actual tools, locations, projects, metrics, or themes; never extracts verbs or pronouns).
 * - Comprehensive real-world domain classification (civic/infrastructure, devotional, crisis, hardware, security, AI, cinema, etc.).
 * - Context-appropriate register (devotional warmth, disaster empathy, technical depth, policy precision, thoughtful dialogue).
 */
public object SmartReplyEngine {
    public fun generateReplies(
        postText: String,
        authorName: String = "",
        authorHandle: String = "",
        mediaDescription: String = "",
        parentDiscussionContext: String = "",
        tone: SmartReplyTone = SmartReplyTone.Practitioner,
    ): SmartReplyResult {
        val startMark = TimeSource.Monotonic.markNow()
        val cleanText = cleanSourceText(postText).ifBlank { cleanSourceText(mediaDescription) }
        val effectiveContext =
            if (parentDiscussionContext.isNotBlank()) {
                "$parentDiscussionContext\n$cleanText"
            } else {
                cleanText
            }
        val context = analyzeContext(effectiveContext, authorName, authorHandle)

        val baseInsight = buildInsightReply(context)
        val baseQuestion = buildQuestionReply(context)
        val baseCounterpoint = buildCounterpointReply(context)
        val basePunchy = buildPunchyReply(context)

        val insightText = adaptReplyToTone(baseInsight, tone, SmartReplyAngle.Insight, context)
        val questionText = adaptReplyToTone(baseQuestion, tone, SmartReplyAngle.Question, context)
        val counterpointText = adaptReplyToTone(baseCounterpoint, tone, SmartReplyAngle.Counterpoint, context)
        val punchyText = adaptReplyToTone(basePunchy, tone, SmartReplyAngle.Punchy, context)

        val options =
            listOf(
                SmartReplyOption(
                    angle = SmartReplyAngle.Insight,
                    title = "Thoughtful Nuance",
                    replyText = insightText,
                ),
                SmartReplyOption(
                    angle = SmartReplyAngle.Question,
                    title = "Spark Conversation",
                    replyText = questionText,
                ),
                SmartReplyOption(
                    angle = SmartReplyAngle.Counterpoint,
                    title = "Alternative Angle",
                    replyText = counterpointText,
                ),
                SmartReplyOption(
                    angle = SmartReplyAngle.Punchy,
                    title = "Crisp Synthesis",
                    replyText = punchyText,
                ),
            )

        return SmartReplyResult(
            options = options,
            detectedTheme = context.themeLabel,
            latencyMs = startMark.elapsedNow().inWholeMilliseconds.coerceAtLeast(1L),
            tone = tone,
        )
    }

    /**
     * Builds a prompt for on-device Gemma / Ollama / AICore inference when available.
     */
    public fun buildGemmaPrompt(
        postText: String,
        authorName: String = "",
        authorHandle: String = "",
        mediaDescription: String = "",
        parentDiscussionContext: String = "",
        tone: SmartReplyTone = SmartReplyTone.Practitioner,
    ): String =
        buildString {
            // Small on-device models copy what they see, so this prompt shows the target voice with one
            // example and plain rules instead of listing phrases to avoid (which primes them).
            appendLine("Write 4 replies to the social media post below, the way a real person types a quick reply on their phone.")
            appendLine()
            appendLine("Voice: ${tone.displayName}. ${tone.promptDirective}")
            appendLine()
            appendLine("How real replies sound:")
            appendLine("- No warm-up. Start with your actual reaction or point. Don't compliment the post or summarise it back.")
            appendLine("- Respond to ONE specific detail from the post: a name, a number, a claim, or something in the image.")
            appendLine("- Plain everyday words and contractions. Short sentences and fragments are fine.")
            appendLine("- Only say what the post supports. Don't invent facts or numbers.")
            appendLine("- No hashtags, no em dashes, at most one exclamation mark. No emoji unless the post uses them.")
            appendLine("- Don't sound like a press release, a LinkedIn post or a motivational quote.")
            appendLine()
            appendLine("The 4 replies:")
            appendLine("[INSIGHT]: adds one thing the author didn't say. 1-2 sentences, under $MAX_REPLY_WORDS words.")
            appendLine("[QUESTION]: one specific question you'd genuinely want answered. Under 25 words, ends with ?")
            appendLine("[COUNTERPOINT]: a fair pushback or catch. 1-2 sentences, under $MAX_REPLY_WORDS words.")
            appendLine("[PUNCHY]: a quick reaction, under 12 words.")
            appendLine()
            appendLine("Example")
            appendLine("Post: Moved our CI to self-hosted runners. Build time went from 14 min to 3.")
            appendLine("[INSIGHT]: 14 to 3 is huge. I'd bet most of that is warm caches, not the hardware.")
            appendLine("[QUESTION]: Who gets paged when a runner dies at 2am?")
            appendLine("[COUNTERPOINT]: Worth it at your size, but now someone maintains runners instead of shipping.")
            appendLine("[PUNCHY]: 3 minute builds would change my whole day.")
            appendLine()
            appendLine("Now the real post.")
            if (authorName.isNotBlank() || authorHandle.isNotBlank()) {
                appendLine("POST AUTHOR: ${authorName.ifBlank { "@$authorHandle" }}")
            }
            if (parentDiscussionContext.isNotBlank()) {
                appendLine("PARENT THREAD / CONTEXT:")
                appendLine(parentDiscussionContext.trim())
                appendLine()
            }
            if (mediaDescription.isNotBlank()) {
                appendLine("ATTACHED MEDIA / VISUAL CONTENT:")
                appendLine(mediaDescription.trim())
                appendLine()
            }
            appendLine("SOURCE POST:")
            appendLine(postText.trim().ifBlank { "[Visual media post with no caption]" })
            appendLine()
            appendLine("OUTPUT FORMAT: exactly 4 lines, each starting with its tag, nothing else.")
            appendLine("[INSIGHT]:")
            appendLine("[QUESTION]:")
            appendLine("[COUNTERPOINT]:")
            appendLine("[PUNCHY]:")
        }

    /**
     * Parses structured replies from an on-device Gemma completion.
     * Resilient against markdown bolding, list bullets, missing tags, or conversational framing.
     */
    public fun parseGemmaReplies(
        raw: String,
        tone: SmartReplyTone = SmartReplyTone.Practitioner,
        sourcePostText: String = "",
        authorName: String = "",
        mediaDescription: String = "",
        parentDiscussionContext: String = "",
    ): List<SmartReplyOption>? {
        if (raw.isBlank()) return null

        val lines = raw.lines()

        fun extractAngle(patterns: List<String>): String? {
            for (line in lines) {
                val trimmed = line.trim()
                for (p in patterns) {
                    val regex = Regex("""(?i)^(?:[-*•\d.]+\s*)?(?:\*{1,2})?\[?$p\]?(?:\*{1,2})?\s*[:\-]\s*(.+)""")
                    val match = regex.find(trimmed)
                    if (match != null) {
                        val text =
                            match.groupValues[1]
                                .trim()
                                .removeSurrounding("\"")
                                .removeSurrounding("*")
                                .trim()
                        if (text.isNotBlank()) return text
                    }
                }
            }
            return null
        }

        var insight = extractAngle(listOf("INSIGHT", "PERSPECTIVE", "NUANCE"))
        var question = extractAngle(listOf("QUESTION", "INQUIRY", "PROMPT"))
        var counterpoint = extractAngle(listOf("COUNTERPOINT", "ALTERNATIVE", "CRITIQUE", "TRADE-OFF", "TRADEOFF"))
        var punchy = extractAngle(listOf("PUNCHY", "TAKEAWAY", "SYNTHESIS", "SUMMARY", "BRIEF"))

        // If at least one angle was parsed, or extract clean paragraph
        val foundAny = insight != null || question != null || counterpoint != null || punchy != null
        if (!foundAny) {
            val cleanParagraph = lines.firstOrNull { it.trim().length in 10..280 && !it.startsWith("#") }?.trim()
            if (!cleanParagraph.isNullOrBlank()) {
                insight = cleanParagraph
            } else {
                return null
            }
        }

        // Fill in missing angles so we always present a full 4-angle palette
        val fallbackRefined =
            generateGemmaRefinedOutput(
                postText = sourcePostText.ifBlank { raw },
                authorName = authorName,
                mediaDescription = mediaDescription,
                parentDiscussionContext = parentDiscussionContext,
                tone = tone,
            )
        val fallbackLines = fallbackRefined.lines()
        val fallbackInsight =
            fallbackLines.firstOrNull { it.startsWith("[INSIGHT]:") }?.substringAfter(":")?.trim()
                ?: "Interesting, hadn't thought about it that way."
        val fallbackQuestion =
            fallbackLines.firstOrNull { it.startsWith("[QUESTION]:") }?.substringAfter(":")?.trim()
                ?: "What made you go this direction?"
        val fallbackCounterpoint =
            fallbackLines.firstOrNull { it.startsWith("[COUNTERPOINT]:") }?.substringAfter(":")?.trim()
                ?: "Depends a lot on the situation, I think."
        val fallbackPunchy =
            fallbackLines.firstOrNull { it.startsWith("[PUNCHY]:") }?.substringAfter(":")?.trim() ?: "Fair point."

        val finalInsight = humanizeReply(insight ?: fallbackInsight).ifBlank { fallbackInsight }
        val finalQuestion = humanizeReply(question ?: fallbackQuestion).ifBlank { fallbackQuestion }
        val finalCounterpoint = humanizeReply(counterpoint ?: fallbackCounterpoint).ifBlank { fallbackCounterpoint }
        val finalPunchy = humanizeReply(punchy ?: fallbackPunchy).ifBlank { fallbackPunchy }

        return listOf(
            SmartReplyOption(SmartReplyAngle.Insight, "Thoughtful Nuance", finalInsight),
            SmartReplyOption(SmartReplyAngle.Question, "Spark Conversation", finalQuestion),
            SmartReplyOption(SmartReplyAngle.Counterpoint, "Alternative Angle", finalCounterpoint),
            SmartReplyOption(SmartReplyAngle.Punchy, "Crisp Synthesis", finalPunchy),
        )
    }

    /**
     * Generates sovereign on-device refined completion when hardware MLKit model is not present.
     */
    public fun generateGemmaRefinedOutput(
        postText: String,
        authorName: String = "",
        authorHandle: String = "",
        mediaDescription: String = "",
        parentDiscussionContext: String = "",
        tone: SmartReplyTone = SmartReplyTone.Practitioner,
    ): String {
        val cleanText = cleanSourceText(postText).ifBlank { cleanSourceText(mediaDescription) }
        val effectiveContext =
            if (parentDiscussionContext.isNotBlank()) {
                "$parentDiscussionContext\n$cleanText"
            } else {
                cleanText
            }
        val context = analyzeContext(effectiveContext, authorName, authorHandle)
        val baseInsight = buildRefinedInsightReply(context)
        val baseQuestion = buildRefinedQuestionReply(context)
        val baseCounterpoint = buildRefinedCounterpointReply(context)
        val basePunchy = buildRefinedPunchyReply(context)

        val insight = adaptReplyToTone(baseInsight, tone, SmartReplyAngle.Insight, context)
        val question = adaptReplyToTone(baseQuestion, tone, SmartReplyAngle.Question, context)
        val counterpoint = adaptReplyToTone(baseCounterpoint, tone, SmartReplyAngle.Counterpoint, context)
        val punchy = adaptReplyToTone(basePunchy, tone, SmartReplyAngle.Punchy, context)

        return buildString {
            appendLine("[INSIGHT]: $insight")
            appendLine("[QUESTION]: $question")
            appendLine("[COUNTERPOINT]: $counterpoint")
            appendLine("[PUNCHY]: $punchy")
        }
    }

    // --- Context Analysis & Modality Identification ---

    internal enum class PostModality {
        DevotionalSpiritual,
        WeatherEmergency,
        EmbeddedHardware,
        CivilLibertiesPrivacy,
        CybersecurityInfra,
        CivicGeopoliticsPolicy,
        OpenSourceCraft,
        ArtificialIntelligence,
        CleanTechEnergy,
        MediaCultureReview,
        StartupMarket,
        ScienceNature,
        SportsFitness,
        LifeMindset,
        HumorBanter,
        AuthorInquiry,
        HumanResilience,
        GeneralObservation,
    }

    internal data class PostContext(
        val cleanText: String,
        val modality: PostModality,
        val themeLabel: String,
        val primarySubject: String,
        val keyEntities: List<String>,
        val metrics: List<String>,
        val authorName: String,
        val authorHandle: String,
        val isQuestion: Boolean,
        val narrativeBeats: List<String>,
    )

    private fun cleanSourceText(raw: String): String =
        raw
            .replace(Regex("https?://\\S+"), "")
            .replace(Regex("&amp;"), "&")
            .replace(Regex("&lt;"), "<")
            .replace(Regex("&gt;"), ">")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun analyzeContext(
        cleanText: String,
        authorName: String,
        authorHandle: String,
    ): PostContext {
        val lower = cleanText.lowercase()
        val entities = extractEntities(cleanText)
        val metrics = extractMetrics(cleanText)
        val beats = OmniScribeEngine.normalizeAndSegmentBeats(cleanText)
        val isQuestion =
            cleanText.trimEnd().endsWith("?") ||
                lower.startsWith("anyone know") ||
                lower.startsWith("what are you using") ||
                lower.startsWith("how do you handle") ||
                lower.startsWith("thoughts on") ||
                lower.startsWith("any recommendations")

        val (modality, theme) = classifyModality(lower, cleanText, isQuestion, entities)
        val primarySubject = determinePrimarySubject(entities, cleanText, modality, authorName)

        return PostContext(
            cleanText = cleanText,
            modality = modality,
            themeLabel = theme,
            primarySubject = primarySubject,
            keyEntities = entities,
            metrics = metrics,
            authorName = authorName,
            authorHandle = authorHandle,
            isQuestion = isQuestion,
            narrativeBeats = beats,
        )
    }

    private fun classifyModality(
        lower: String,
        cleanText: String,
        isQuestion: Boolean,
        entities: List<String>,
    ): Pair<PostModality, String> {
        // 1. Devotional & Spiritual (e.g. Hare Krishna, Eid, Diwali, blessings, prayers)
        if (isDevotional(lower)) {
            return PostModality.DevotionalSpiritual to "SACRED & DEVOTIONAL"
        }

        // 2. Weather alerts & emergency dispatches (cyclone, flood, IMD, #WATCH, fishermen ashore)
        if (isWeatherAlert(lower)) {
            return PostModality.WeatherEmergency to "COASTAL WEATHER ALERT"
        }

        // 3. Embedded systems & physical hardware (ESP32, microcontroller, 60 FPS, bare-metal C++)
        if (isEmbeddedHardware(lower)) {
            return PostModality.EmbeddedHardware to "EMBEDDED HARDWARE"
        }

        // 4. Civil liberties, privacy, platform regulations & legal rights (EFF, ANPD, GDPR, digital rights)
        if (isCivilLiberties(lower)) {
            return PostModality.CivilLibertiesPrivacy to "CIVIL LIBERTIES & PRIVACY"
        }

        // 5. Civic, national infrastructure, border projects, sovereignty, and geopolitical debate
        if (isCivicGeopolitics(lower)) {
            return PostModality.CivicGeopoliticsPolicy to "CIVIC & NATIONAL POLICY"
        }

        // 6. Enterprise cybersecurity & threat landscape (vulnerabilities, exploits, attack surfaces)
        if (isCybersecurity(lower)) {
            return PostModality.CybersecurityInfra to "ENTERPRISE SECURITY"
        }

        // 7. Applied Artificial Intelligence & LLMs (inference, weights, fine-tuning, RAG, agents)
        if (isArtificialIntelligence(lower)) {
            return PostModality.ArtificialIntelligence to "APPLIED AI"
        }

        // 8. CleanTech, battery chemistry, EV manufacturing, and grid storage
        if (isCleanTechEnergy(lower)) {
            return PostModality.CleanTechEnergy to "ENERGY & CLEANTECH"
        }

        // 9. Media, cinema, critics vs audience consensus (The Economist movie review style)
        if (isMediaCulture(lower)) {
            return PostModality.MediaCultureReview to "CRITIC & CULTURE"
        }

        // 9. Open-source software releases, compilers & dev tools
        if (isOpenSourceSoftware(lower)) {
            val label = if (isOpenSourceRelease(lower)) "OPEN SOURCE CRAFT" else "SOFTWARE CRAFT"
            return PostModality.OpenSourceCraft to label
        }

        // 10. Questions from the author asking for help or opinions
        if (isQuestion) {
            return PostModality.AuthorInquiry to "COMMUNITY INQUIRY"
        }

        // 11. Startup, founders, market economics, pricing, unit economics
        if (isStartupMarket(lower)) {
            return PostModality.StartupMarket to "MARKET & FOUNDERS"
        }

        // 12. Science, astronomy, space exploration, and natural world
        if (isScienceNature(lower)) {
            return PostModality.ScienceNature to "SCIENCE & DISCOVERY"
        }

        // 13. Sports, athletics, marathon, and physical endurance
        if (isSportsFitness(lower)) {
            return PostModality.SportsFitness to "SPORTS & ATHLETICS"
        }

        // 14. Life craft, deep work, habits, discipline
        if (isLifeMindset(lower)) {
            return PostModality.LifeMindset to "MINDSET & CRAFT"
        }

        // 15. Human resilience, recovery, physical accessibility
        if (isHumanResilience(lower)) {
            return PostModality.HumanResilience to "HUMAN RESILIENCE"
        }

        // 16. Humor, banter, irony, and internet satire
        if (isHumorBanter(lower)) {
            return PostModality.HumorBanter to "CULTURE & BANTER"
        }

        return PostModality.GeneralObservation to "PERSPECTIVE"
    }

    private fun isDevotional(lower: String): Boolean {
        // Whole words only, and words that also have everyday meanings ("temple" is a product, a
        // street, an anatomy term) need a second devotional signal before a post counts.
        val strong =
            listOf(
                "hare krishna",
                "krishna",
                "radhe radhe",
                "har har mahadev",
                "mahadev",
                "ganesha",
                "pranams",
                "darshan",
                "ramadan",
                "eid mubarak",
                "jumma mubarak",
                "merry christmas",
                "happy diwali",
                "navratri",
                "waheguru",
                "divine grace",
                "god bless",
                "peace be upon",
                "om shanti",
                "bhagavad gita",
            )
        val weak = listOf("temple", "shiva", "namaste", "shubh", "blessed day", "prayer", "blessings")
        val strongHits = strong.count { lower.containsWord(it) }
        val weakHits = weak.count { lower.containsWord(it) }
        return strongHits > 0 || weakHits >= 2
    }

    private fun String.containsWord(phrase: String): Boolean =
        Regex("(?<![\\p{L}\\p{N}])${Regex.escape(phrase)}(?![\\p{L}\\p{N}])").containsMatchIn(this)

    private fun isWeatherAlert(lower: String): Boolean {
        val stormTerms = listOf("cyclone", "hurricane", "typhoon", "tsunami", "gale warning", "storm surge")
        if (stormTerms.any { it in lower }) return true

        val coastalDisaster =
            listOf(
                "fishermen stay ashore",
                "coastal district",
                "evacuation order",
                "meteorological department",
                "flood alert",
                "heavy downpour",
            )
        val hasCoastalDisaster = coastalDisaster.any { it in lower }
        val hasWeatherOrg = Regex("""\b(imd|weather department|met department)\b""").containsMatchIn(lower) || "#watch" in lower
        return hasCoastalDisaster && (hasWeatherOrg || "warning" in lower || "rain" in lower)
    }

    private fun isEmbeddedHardware(lower: String): Boolean {
        val hardwareWords =
            listOf(
                "esp32",
                "esp8266",
                "arduino",
                "stm32",
                "microcontroller",
                "embedded devices",
                "60 fps",
                "visual editor for esp",
                "bare metal",
                "sram",
                "psram",
                "firmware",
                "game engine ... but for",
                "game engine with a visual editor",
            )
        return hardwareWords.any { it in lower }
    }

    private fun isCivilLiberties(lower: String): Boolean {
        val hasOrg = Regex("\\b(eff|anpd|gdpr|ftc)\\b").containsMatchIn(lower) || "data privacy" in lower
        return hasOrg && ("regulat" in lower || "privacy" in lower || "expression" in lower || "law" in lower)
    }

    private fun isCivicGeopolitics(lower: String): Boolean {
        val keywords =
            listOf(
                "national project",
                "anti nationalist",
                "nationalist",
                "chinese",
                "arunachali",
                "border",
                "sovereign",
                "infrastructure project",
                "paid pr",
                "foreign policy",
                "geopolitic",
                "territor",
                "statehood",
                "election",
                "parliament",
                "diplomacy",
                "citizen",
                "public policy",
                "bureaucracy",
                "defense",
                "military",
                "patriot",
            )
        val hasPrOrSpeech = "project" in lower || "build" in lower || "speech" in lower || Regex("\\bpr\\b").containsMatchIn(lower)
        return keywords.any { it in lower } ||
            (("india" in lower || "china" in lower || "border" in lower) && hasPrOrSpeech)
    }

    private fun isCybersecurity(lower: String): Boolean {
        val words =
            listOf(
                "vulnerability",
                "exploit",
                "breach",
                "zero-day",
                "ransomware",
                "phishing",
                "threat actor",
                "shadow it",
                "cybersecurity",
                "infosec",
                "endpoint protection",
                "firewall",
                "data protection",
                "security incident",
                "data leak",
                "malware",
            )
        if (words.any { it in lower } || Regex("\\bcve\\b").containsMatchIn(lower)) return true

        val hasRiskSurface = "risk surface" in lower || "sprawling infrastructure to secure" in lower
        val hasSecurity = "security" in lower
        return hasRiskSurface && hasSecurity
    }

    private fun isArtificialIntelligence(lower: String): Boolean {
        val standaloneWords = listOf("llm", "llms", "gpt-4", "gpt", "rag")
        val phrases =
            listOf(
                "large language model",
                "claude",
                "gemma",
                "diffusion model",
                "transformer architecture",
                "inference latency",
                "fine-tuning",
                "embeddings",
                "synthetic data",
                "context window",
                "agentic workflow",
                "prompt engineering",
                "artificial intelligence",
            )
        val hasStandalone = standaloneWords.any { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(lower) }
        return hasStandalone || phrases.any { it in lower }
    }

    private fun isCleanTechEnergy(lower: String): Boolean {
        val standaloneWords = listOf("ev", "evs")
        val keywords =
            listOf(
                "battery",
                "electric vehicle",
                "lithium",
                "anode",
                "cathode",
                "energy storage",
                "cleantech",
                "solar",
                "photovoltaic",
                "gigawatt",
                "megawatt",
                "solid state battery",
                "renewable",
                "grid storage",
                "#batterytech",
                "#evrevolution",
                "bettertalk",
                "clean energy",
                "electrification",
                "circular economy",
                "recycling",
                "sustainability",
                "waste management",
                "materials management",
                "plastic waste",
                "end-of-life management",
                "bioplastics",
                "#circulareconomy",
                "#sustainability",
                "#plasticwaste",
            )
        val hasStandalone = standaloneWords.any { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(lower) }
        return hasStandalone || keywords.any { it in lower }
    }

    private fun isMediaCulture(lower: String): Boolean =
        ("critic" in lower || "audience" in lower || "review" in lower || "movie" in lower || "cinema" in lower) &&
            ("divide" in lower || "agreement" in lower || "moviegoers" in lower || "box office" in lower || "film" in lower)

    private fun isOpenSourceSoftware(lower: String): Boolean {
        val words =
            listOf(
                "open source",
                "apache 2.0",
                "github.com",
                "crates.io",
                "compiler",
                "library release",
                "refactoring",
                "codebase",
                "api design",
                "rust",
                "kotlin",
                "typescript",
                "golang",
                "sqlite",
                "postgres",
                "distributed systems",
                "microservices",
            )
        return words.any { it in lower }
    }

    private fun isStartupMarket(lower: String): Boolean {
        val startupSpecific =
            listOf(
                "startup",
                "pricing",
                "distribution",
                "churn",
                "market dynamics",
                "unit economics",
                "customers are actually",
                "saas",
                "bootstrapped",
                "venture capital",
                "seed round",
                "product market fit",
                "pmf",
            )
        if (startupSpecific.any { it in lower }) return true

        val hasFounder = Regex("\\b(founder|co-founder)\\b").containsMatchIn(lower)
        val isAcademicOrCivic =
            "university" in lower || "institute" in lower || "faculty" in lower || "non-profit" in lower || "director of" in lower
        val hasCommercialSignal = "customer" in lower || "revenue" in lower || "scale" in lower || "product" in lower || "investor" in lower
        return hasFounder && !isAcademicOrCivic && hasCommercialSignal
    }

    private fun isScienceNature(lower: String): Boolean {
        val words =
            listOf(
                "nasa",
                "james webb",
                "telescope",
                "astronomy",
                "physics",
                "quantum",
                "biodiversity",
                "species",
                "exoplanet",
                "climate science",
            )
        return words.any { it in lower }
    }

    private fun isSportsFitness(lower: String): Boolean {
        val words =
            listOf("marathon", "workout", "athlete", "championship", "cricket", "football", "olympic", "tournament", "endurance training")
        return words.any { it in lower }
    }

    private fun isLifeMindset(lower: String): Boolean {
        val words =
            listOf(
                "deep work",
                "discipline",
                "burnout",
                "mental clarity",
                "mindfulness",
                "daily habits",
                "creative craft",
                "focus and craft",
                "normal person",
                "unconventional",
                "conformity",
                "individuality",
                "life lesson",
                "personal growth",
                "society has squeezed",
                "aspirations",
            )
        return words.any { it in lower }
    }

    private fun isHumorBanter(lower: String): Boolean {
        val words = listOf("wow man", "lmao", "rofl", "crying face", "hilarious", "insane take", "timeline is undefeated")
        return words.any { it in lower }
    }

    private fun isHumanResilience(lower: String): Boolean {
        val words = listOf("wheelchair", "patent", "hospital", "recovery", "handcrafted", "endurance", "accident")
        return words.any { it in lower }
    }

    // --- Entity & Detail Extraction ---

    private val CommonSentenceStarters =
        setOf(
            "There",
            "This",
            "That",
            "These",
            "Those",
            "Good",
            "Bad",
            "What",
            "When",
            "Where",
            "Which",
            "Who",
            "Whom",
            "Why",
            "How",
            "Talk",
            "Learn",
            "Available",
            "Most",
            "Some",
            "Many",
            "Much",
            "Here",
            "We",
            "They",
            "You",
            "Our",
            "Your",
            "Their",
            "From",
            "With",
            "About",
            "Into",
            "Over",
            "Under",
            "After",
            "Before",
            "During",
            "Since",
            "Until",
            "Stay",
            "Come",
            "Go",
            "Make",
            "Take",
            "Have",
            "Has",
            "Had",
            "Been",
            "WATCH",
            "RT",
            "Just",
            "Also",
            "Even",
            "Only",
            "Now",
            "Then",
            "So",
            "If",
            "Because",
            "Although",
            "Meanwhile",
            "Every",
            "Another",
            "Both",
            "Either",
            "Neither",
            "All",
            "Any",
            "None",
            "One",
            "Two",
            "First",
            "Last",
            "Next",
            "Today",
            "Yesterday",
            "Tomorrow",
            "Tonight",
            "Well",
            "Yes",
            "No",
            "Not",
            "Maybe",
            "Perhaps",
            "Finally",
            "Actually",
            "Honestly",
            "Literally",
            "Basically",
            "Obviously",
            "Clearly",
            "Personally",
            "Surprisingly",
            "Apparently",
            "Currently",
            "Recently",
            "Suddenly",
            "Unfortunately",
            "Fortunately",
            "Overall",
            "Speaking",
            "Looking",
            "Thinking",
            "Going",
            "Building",
            "Running",
            "Working",
            "Trying",
            "Starting",
            "Making",
            "Taking",
            "Getting",
            "Coming",
            "Seeing",
            "Knowing",
            "Feeling",
            "Wanting",
            "Asking",
            "Telling",
            "Giving",
            "Finding",
            "Leaving",
            "Calling",
            "Keeping",
            "Letting",
            "Putting",
            "Showing",
            "Bringing",
            "Standing",
            "Hearing",
            "Playing",
            "Moving",
            "Living",
            "Believing",
            "Holding",
            "Writing",
            "Providing",
            "Sitting",
            "Losing",
            "Paying",
            "Meeting",
            "Including",
            "Continuing",
            "Setting",
            "Learning",
            "Changing",
            "Leading",
            "Understanding",
            "Watching",
            "Following",
            "Stopping",
            "Creating",
            "Reading",
            "Allowing",
            "Adding",
            "Spending",
            "Growing",
            "Opening",
            "Walking",
            "Winning",
            "Offering",
            "Remembering",
            "Loving",
            "Considering",
            "Appearing",
            "Buying",
            "Waiting",
            "Serving",
            "Dying",
            "Sending",
            "Expecting",
            "Staying",
            "Falling",
            "Cutting",
            "Reaching",
            "Remaining",
        )

    private fun extractEntities(text: String): List<String> {
        val clean = text.replace(Regex("https?://\\S+"), "")
        val words = clean.split(Regex("\\s+"))
        val entities = mutableListOf<String>()

        // 1. Detect multi-word proper nouns (e.g. "Arunachal Pradesh", "Tamil Nadu", "Aaron Levie", "Deki Engine")
        val multiWordRegex = Regex("\\b[A-Z][a-z]+(?:\\s+[A-Z][a-z]+)+\\b")
        for (match in multiWordRegex.findAll(clean)) {
            val candidate = match.value.trim()
            val firstWord = candidate.substringBefore(" ")
            if (!CommonSentenceStarters.contains(firstWord)) {
                entities.add(candidate)
            }
        }

        // 2. Detect all-caps acronyms (2-6 letters, e.g. EFF, ANPD, IMD, PR, ESP32, AI, ML, GDP, NASA, ISRO)
        val acronymRegex = Regex("\\b[A-Z0-9]{2,6}\\b")
        for (match in acronymRegex.findAll(clean)) {
            val candidate = match.value.trim()
            if (candidate !in setOf("RT", "AND", "THE", "FOR", "NOT", "BUT", "ALL", "NEW", "NOW", "MAN", "WOW")) {
                entities.add(candidate)
            }
        }

        // 3. Detect capitalized words that do NOT appear at sentence beginnings
        for (i in 1 until words.size) {
            val prevWord = words[i - 1].trim()
            val currWord = words[i].trim().trimEnd('.', '!', '?', ',', ':', ';', '"', '\'', ')')
            val isSentenceStart = prevWord.endsWith(".") || prevWord.endsWith("!") || prevWord.endsWith("?")
            if (!isSentenceStart && currWord.length >= 3 && currWord[0].isUpperCase()) {
                val alphaOnly = currWord.filter { it.isLetter() }
                if (alphaOnly.isNotEmpty() && !CommonSentenceStarters.contains(alphaOnly)) {
                    entities.add(alphaOnly)
                }
            }
        }

        return entities.distinct()
    }

    private fun extractMetrics(text: String): List<String> {
        val regex =
            Regex("\\b(\\d+(?:\\.\\d+)?\\s*(?:FPS|fps|GB|MB|KB|RAM|SRAM|GHz|MHz|%|years?|hr|min|seconds?|ms|Wh/kg|kWh|GW|MW|kW|x))\\b")
        return regex
            .findAll(text)
            .map { it.value.trim() }
            .distinct()
            .toList()
    }

    private fun determinePrimarySubject(
        entities: List<String>,
        rawText: String,
        modality: PostModality,
        authorName: String,
    ): String {
        val lower = rawText.lowercase()
        return when (modality) {
            PostModality.DevotionalSpiritual -> {
                entities.firstOrNull {
                    it.contains("Krishna", ignoreCase = true) || it.contains("Shiva", ignoreCase = true) ||
                        it.contains("Rama", ignoreCase = true)
                }
                    ?: "Hare Krishna"
            }

            PostModality.WeatherEmergency -> {
                entities.firstOrNull { it.contains("Thoothukudi", ignoreCase = true) || it.contains("Tamil", ignoreCase = true) }
                    ?: entities.firstOrNull { it.length > 3 && !it.equals("WATCH", true) }
                    ?: "the coastal district"
            }

            PostModality.EmbeddedHardware -> {
                entities.firstOrNull { it.contains("Engine", ignoreCase = true) || it.contains("Deki", ignoreCase = true) }
                    ?: entities.firstOrNull {
                        it.contains("ESP", ignoreCase = true) || it.contains("STM", ignoreCase = true) ||
                            it.contains("Arduino", ignoreCase = true)
                    }
                    ?: "embedded microcontroller gaming"
            }

            PostModality.CivilLibertiesPrivacy -> {
                entities.firstOrNull {
                    it.contains("ANPD", ignoreCase = true) || it.contains("EFF", ignoreCase = true) ||
                        it.contains("GDPR", ignoreCase = true)
                }
                    ?: "platform regulation and user privacy"
            }

            PostModality.CivicGeopoliticsPolicy -> {
                entities.firstOrNull {
                    it.equals("India", ignoreCase = true) ||
                        it.equals("Arunachal", ignoreCase = true) ||
                        it.equals("China", ignoreCase = true)
                } ?: "national infrastructure projects"
            }

            PostModality.CybersecurityInfra -> {
                entities.firstOrNull {
                    it.contains("Threema", ignoreCase = true) ||
                        it.contains("WireGuard", ignoreCase = true) ||
                        it.contains("VPN", ignoreCase = true) ||
                        it.contains("Firewall", ignoreCase = true) ||
                        it.contains("ZeroTrust", ignoreCase = true)
                } ?: entities.firstOrNull { !it.equals("PR", true) && !it.equals("WATCH", true) && it.length > 2 }
                    ?: if ("threema" in lower) {
                        "Threema enterprise deployment"
                    } else if ("risk surface" in lower) {
                        "the enterprise risk surface"
                    } else {
                        extractFocalPhrase(rawText).takeIf { it != "this topic" }
                            ?: "enterprise security architecture"
                    }
            }

            PostModality.ArtificialIntelligence -> {
                entities.firstOrNull {
                    it.contains("Whisper", ignoreCase = true) ||
                        it.contains("Gemma", ignoreCase = true) ||
                        it.contains("Llama", ignoreCase = true) ||
                        it.contains("Claude", ignoreCase = true) ||
                        it.contains("GPT", ignoreCase = true) ||
                        it.contains("LLM", ignoreCase = true)
                } ?: if ("whisper" in lower) {
                    "Whisper local transcription"
                } else if ("diffusion" in lower) {
                    "diffusion model pipelines"
                } else if ("agent" in lower) {
                    "autonomous agentic loops"
                } else {
                    extractFocalPhrase(rawText).takeIf { it != "this topic" }
                        ?: "applied AI engineering"
                }
            }

            PostModality.CleanTechEnergy -> {
                entities.firstOrNull {
                    it.contains("Battery", true) || it.contains("BTR", true) ||
                        it.contains("Energy", true) || it.contains("Lithium", true) ||
                        it.contains("Circular", true) || it.contains("Innovation", true) ||
                        it.contains("Calvin", true)
                } ?: if ("circular" in lower || "waste" in lower || "plastic" in lower) {
                    "the circular economy and materials management"
                } else {
                    "battery energy storage systems"
                }
            }

            PostModality.MediaCultureReview -> {
                "the critic-audience relationship"
            }

            PostModality.LifeMindset -> {
                if ("quirk" in lower || "unconventional" in lower || "conform" in lower || "normal" in lower) {
                    "unconventional thinking and individuality"
                } else {
                    extractFocalPhrase(rawText).takeIf { it != "this topic" }
                        ?: authorName.ifBlank { "mindset and focus" }
                }
            }

            PostModality.OpenSourceCraft -> {
                entities.firstOrNull { !it.equals("PR", true) && !it.equals("WATCH", true) }
                    ?: extractFocalPhrase(rawText).takeIf { it != "this topic" }
                    ?: "open-source architecture"
            }

            PostModality.StartupMarket -> {
                entities.firstOrNull { !it.equals("PR", true) && !it.equals("WATCH", true) }
                    ?: extractFocalPhrase(rawText).takeIf { it != "this topic" }
                    ?: "market distribution and economics"
            }

            PostModality.HumanResilience -> {
                if ("patent" in lower || "blueprint" in lower || "open source" in lower) {
                    "open-source assistive hardware"
                } else {
                    "perseverance through recovery"
                }
            }

            else -> {
                entities.firstOrNull { !it.equals("PR", true) && !it.equals("WATCH", true) }
                    ?: extractFocalPhrase(rawText).takeIf { it != "this topic" }
                    ?: authorName.ifBlank { "this topic" }
            }
        }
    }

    private val CommonStopWords =
        setOf(
            "the",
            "a",
            "an",
            "is",
            "are",
            "was",
            "were",
            "this",
            "that",
            "these",
            "those",
            "there",
            "and",
            "or",
            "but",
            "in",
            "on",
            "at",
            "to",
            "for",
            "with",
            "from",
            "by",
            "about",
            "into",
            "it",
            "its",
            "we",
            "they",
            "you",
            "my",
            "our",
            "their",
            "just",
            "also",
            "even",
            "out",
            "now",
            "here",
            "can",
            "will",
            "would",
            "should",
            "could",
            "all",
            "some",
            "most",
            "what",
            "when",
            "where",
            "why",
            "how",
            "have",
            "has",
            "had",
            "been",
            "being",
            "like",
            "more",
            "than",
            "over",
            "after",
        )

    private fun extractFocalPhrase(rawText: String): String {
        val clean =
            rawText
                .replace(Regex("https?://\\S+"), "")
                .replace(Regex("#\\S+"), "")
                .replace(Regex("@[a-zA-Z0-9_.]+"), "")
                .trim()

        // 1. Check for quoted key phrases first (e.g. "normal person", "risk surface")
        val quoteRegex = Regex("""["“]([^"”]{3,40})["”]""")
        val quoteMatch =
            quoteRegex
                .find(clean)
                ?.groupValues
                ?.get(1)
                ?.trim()
        if (!quoteMatch.isNullOrBlank()) {
            return quoteMatch
        }

        val sentences = clean.split(Regex("[.!?\\n]+")).map { it.trim() }.filter { it.length > 8 }
        val firstSentence = sentences.firstOrNull() ?: clean

        // Strip conversational lead-ins
        val stripped =
            firstSentence
                .replace(
                    Regex(
                        "^(?i)(good assessment of|interesting to see|here is (?:why|the reality of)|reminder that|it turns out that|people often forget that|unpopular opinion:?|hot take:?|so glad to see|excited to announce|excited to share)\\s*",
                    ),
                    "",
                ).trim()

        val words = stripped.split(Regex("\\s+")).filter { it.isNotBlank() }
        val substantiveWords =
            words.filter { word ->
                val lower = word.lowercase().trim('.', ',', '"', '\'', ':', ';', '(', ')')
                lower.length >= 3 && lower !in CommonStopWords
            }

        return if (substantiveWords.size >= 2) {
            substantiveWords.take(4).joinToString(" ")
        } else {
            "this topic"
        }
    }

    // --- Offline Human Reply Generators ---
    //
    // Used when no model is loaded. Voice rules: react to the post's own details, plain words and
    // contractions, no praise openers, no em dashes, no LinkedIn aphorisms, nothing the post
    // doesn't support. Every line still passes through humanizeReply().

    /** The post's own proper noun (tool, place, org) when we found one; never a guessed phrase. */
    private val PostContext.namedSubject: String?
        get() = primarySubject.takeIf { subject -> keyEntities.any { it.equals(subject, ignoreCase = true) } }

    private val durationMetricRegex =
        Regex("^\\d+(?:\\.\\d+)?\\s*(?:years?|hr|min|seconds?)$", RegexOption.IGNORE_CASE)

    /** Only technical/scale numbers from the post: safe to frame as benchmarks ("85ms to 12ms"). */
    private val PostContext.metricHook: String?
        get() {
            val nonDuration = metrics.filterNot { durationMetricRegex.matches(it) }
            return when {
                nonDuration.size >= 2 -> "${nonDuration[0]} to ${nonDuration[1]}"
                nonDuration.isNotEmpty() -> nonDuration[0]
                else -> null
            }
        }

    // "Open source craft" also catches any software post (Rust, Postgres…); only talk about
    // open-sourcing when the post is actually a release.
    private fun isOpenSourceRelease(lower: String): Boolean =
        listOf(
            "open source",
            "open-source",
            "opensource",
            "github.com",
            "apache 2.0",
            "mit license",
            "crates.io",
            "released",
            "release",
        ).any {
            it in
                lower
        }

    private fun PostContext.isOpenSourceRelease(): Boolean = isOpenSourceRelease(cleanText.lowercase())

    private fun String.decapitalized(): String =
        when {
            isEmpty() -> this
            this == "I" || startsWith("I'") -> this
            length > 1 && this[0].isUpperCase() && this[1].isLowerCase() -> this[0].lowercase() + substring(1)
            else -> this
        }

    private fun PostContext.chip(fallback: String): String =
        keyEntities.firstOrNull { it.startsWith("ESP", true) || it.startsWith("STM", true) || it.equals("Arduino", true) } ?: fallback

    private fun PostContext.fps(): String = metrics.firstOrNull { it.contains("FPS", true) } ?: "60 FPS"

    private fun PostContext.isCircular(): Boolean {
        val lower = cleanText.lowercase()
        return "circular" in lower || "waste" in lower || "recycl" in lower || "plastic" in lower
    }

    private fun PostContext.isOpenHardware(): Boolean {
        val lower = cleanText.lowercase()
        return "blueprint" in lower || "patent" in lower || "open source" in lower || "design" in lower
    }

    private fun PostContext.isUnconventional(): Boolean {
        val lower = cleanText.lowercase()
        return "quirk" in lower || "unconventional" in lower || "conform" in lower || "normal" in lower || "individuality" in lower
    }

    private fun PostContext.isLocalAi(): Boolean {
        val lower = cleanText.lowercase()
        return "whisper" in lower || "local" in lower || "edge" in lower || "on-device" in lower
    }

    private fun buildInsightReply(ctx: PostContext): String {
        val lower = ctx.cleanText.lowercase()
        return when (ctx.modality) {
            PostModality.DevotionalSpiritual -> {
                "This darshan is so peaceful to look at. Needed this today."
            }

            PostModality.WeatherEmergency -> {
                val loc = ctx.keyEntities.firstOrNull { !it.equals("WATCH", true) } ?: "the coast"
                "Staying ashore is the right call with a cyclone this close to $loc. Hope everyone stays safe."
            }

            PostModality.EmbeddedHardware -> {
                "${ctx.fps()} on ${ctx.chip(
                    "a microcontroller",
                )} with a visual editor is no joke. That's a lot squeezed out of a tiny chip."
            }

            PostModality.CivilLibertiesPrivacy -> {
                val advocate = ctx.keyEntities.firstOrNull { it.equals("EFF", true) || it.equals("EDRi", true) } ?: "digital rights groups"
                "Good to see $advocate pushing for encryption to be written in from the start. Once it's carved out, it rarely comes back."
            }

            PostModality.CivicGeopoliticsPolicy -> {
                "Projects like this get lost in the online shouting. What matters is whether it gets built and actually helps people there."
            }

            PostModality.CybersecurityInfra -> {
                when {
                    "threema" in lower || "administration" in lower || "enterprise" in lower -> {
                        "Centralized administration in ${ctx.primarySubject} should save IT teams a lot of pain. Setting up desktop and mobile one by one is miserable."
                    }

                    "risk surface" in lower -> {
                        "The risk surface keeps growing faster than teams can map it. Secure defaults probably beat adding another dashboard."
                    }

                    else -> {
                        "For ${ctx.primarySubject}, fewer moving parts beats another alerting tool. Most teams are already drowning in alerts."
                    }
                }
            }

            PostModality.ArtificialIntelligence -> {
                if (ctx.isLocalAi()) {
                    "Running ${ctx.primarySubject} locally is underrated. No API bill, and no data leaving the device."
                } else {
                    "The hard part with ${ctx.primarySubject} isn't the model anymore. It's getting it to behave the same way twice."
                }
            }

            PostModality.CleanTechEnergy -> {
                if (ctx.isCircular()) {
                    "The circular economy only works if materials management is treated like real infrastructure. Good recycling is mostly logistics."
                } else {
                    "${ctx.metricHook ?: "That density"} is a big claim for a battery. Getting it out of the lab and into production is the hard part."
                }
            }

            PostModality.MediaCultureReview -> {
                "Makes sense. The divide mostly shows up in loud online fights, not in what people actually go and watch."
            }

            PostModality.OpenSourceCraft -> {
                when {
                    ctx.isOpenSourceRelease() -> {
                        "Open-sourcing ${ctx.primarySubject} is a good call. Clean docs and a small API are what'll make people stick around."
                    }

                    ctx.metricHook != null -> {
                        "${ctx.metricHook} is a big jump. Curious how much came from ${ctx.namedSubject ?: "the new stack"} itself versus the design changes along the way."
                    }

                    else -> {
                        "Curious how much of the win is ${ctx.namedSubject ?: "the tool"} itself versus the design changes around it."
                    }
                }
            }

            PostModality.AuthorInquiry -> {
                "I'd start with the simplest option and only add pieces when something actually breaks."
            }

            PostModality.StartupMarket -> {
                "A lot of extra features are a way of avoiding the harder distribution problem. Keeping it simple leaves room to listen to customers."
            }

            PostModality.ScienceNature -> {
                "So much real discovery happens right at the edge of what the instruments can measure."
            }

            PostModality.SportsFitness -> {
                "Showing up consistently for months beats one heroic session every time."
            }

            PostModality.LifeMindset -> {
                if (ctx.isUnconventional()) {
                    "Being normal is easy. Keeping the unconventional parts of yourself takes real effort."
                } else {
                    "Protecting your focus gets harder every year. Cutting the noise does more than any productivity trick."
                }
            }

            PostModality.HumorBanter -> {
                "At this point you can predict the replies before anyone even posts them."
            }

            PostModality.HumanResilience -> {
                if (ctx.isOpenHardware()) {
                    "Open designs mean more people can build and improve them. A patent sitting in a drawer helps nobody."
                } else {
                    "That kind of recovery takes so much quiet, daily grit. Those first steps must feel huge."
                }
            }

            PostModality.GeneralObservation -> {
                ctx.metricHook?.let { "$it is a big jump. Curious what made the biggest difference." }
                    ?: ctx.namedSubject?.let { "Honestly the $it part is what stuck with me." }
                    ?: "This is more true than people like to admit."
            }
        }.let { humanizeReply(it) }
    }

    private fun buildQuestionReply(ctx: PostContext): String {
        val lower = ctx.cleanText.lowercase()
        return when (ctx.modality) {
            PostModality.DevotionalSpiritual -> {
                "The decorations are beautiful. Which temple is this from?"
            }

            PostModality.WeatherEmergency -> {
                "Are the relief shelters along the ${ctx.primarySubject} coast open yet?"
            }

            PostModality.EmbeddedHardware -> {
                "How are you fitting the assets into SRAM on ${ctx.chip("the chip")}? Or does it lean on PSRAM?"
            }

            PostModality.CivilLibertiesPrivacy -> {
                val advocate = ctx.keyEntities.firstOrNull { it.equals("EFF", true) || it.equals("EDRi", true) }
                val regulator =
                    ctx.keyEntities.firstOrNull { it.equals("ANPD", true) || it.equals("GDPR", true) || it.equals("FTC", true) }
                        ?: "the regulator"
                if (advocate != null) {
                    "Is $regulator actually likely to go with what $advocate is proposing, or with stricter takedown rules?"
                } else {
                    "Does this give people any real way to challenge a decision, or is it mostly paperwork for civil liberties?"
                }
            }

            PostModality.CivicGeopoliticsPolicy -> {
                "What do the people who live near the project actually think of it?"
            }

            PostModality.CybersecurityInfra -> {
                "Do you think companies consolidate onto one platform, or keep patching the gaps with more tools?"
            }

            PostModality.ArtificialIntelligence -> {
                "How do you keep latency and context in check when the loops run for a long time?"
            }

            PostModality.CleanTechEnergy -> {
                if (ctx.isCircular()) {
                    "Where's the bottleneck right now, sorting capacity or demand for recycled material?"
                } else {
                    "What does cycle life look like at that density, especially with fast charging?"
                }
            }

            PostModality.MediaCultureReview -> {
                "Do you think Rotten Tomatoes style scores made the divide look bigger than it is?"
            }

            PostModality.OpenSourceCraft -> {
                if (ctx.isOpenSourceRelease()) {
                    "What's been harder with ${ctx.primarySubject}, keeping the API small or supporting every platform?"
                } else {
                    "What surprised you most working with ${ctx.namedSubject ?: "it"}?"
                }
            }

            PostModality.AuthorInquiry -> {
                "What's the main constraint for you, memory, write throughput or ease of use?"
            }

            PostModality.StartupMarket -> {
                "Does this help the big players who own distribution, or the small teams with no legacy to carry?"
            }

            PostModality.ScienceNature -> {
                "What would it take to confirm this? Is anyone trying to replicate it?"
            }

            PostModality.SportsFitness -> {
                "How are you handling recovery between the big training blocks?"
            }

            PostModality.LifeMindset -> {
                if (ctx.isUnconventional() || "opinion" in lower) {
                    "What helps you keep thinking for yourself when everyone around you pushes the other way?"
                } else {
                    "What made you start guarding your focus like this?"
                }
            }

            PostModality.HumorBanter -> {
                "Is anyone actually surprised by this anymore?"
            }

            PostModality.HumanResilience -> {
                if (ctx.isOpenHardware()) {
                    "What's stopping more hardware makers from opening up their designs?"
                } else {
                    "What kept you going on the days progress felt slow?"
                }
            }

            PostModality.GeneralObservation -> {
                ctx.namedSubject?.let { "What's the part people usually get wrong about $it?" }
                    ?: "What's the part people usually get wrong here?"
            }
        }.let { humanizeReply(it) }
    }

    private fun buildCounterpointReply(ctx: PostContext): String =
        when (ctx.modality) {
            PostModality.DevotionalSpiritual -> {
                "Moments like this are a good reminder to slow down a bit."
            }

            PostModality.WeatherEmergency -> {
                "Right call, but days without fishing hit daily-wage fishermen hard. Hope the relief money reaches them fast."
            }

            PostModality.EmbeddedHardware -> {
                "Fun for small games, but I'd worry about memory once audio and wireless are running on ${ctx.chip(
                    "the microcontroller",
                )} too."
            }

            PostModality.CivilLibertiesPrivacy -> {
                "Platforms do need rules, but vague ones hit small and federated services hardest. The big players just hire more compliance lawyers."
            }

            PostModality.CivicGeopoliticsPolicy -> {
                "Some local concerns are legit though, especially about land and environment. Worth separating those from the political noise."
            }

            PostModality.CybersecurityInfra -> {
                "The twist is that startups have it easier here. No legacy stack means secure defaults from day one, while big companies carry the real risk."
            }

            PostModality.ArtificialIntelligence -> {
                "Works in a demo, but I'd still want a human checking anything high stakes."
            }

            PostModality.CleanTechEnergy -> {
                if (ctx.isCircular()) {
                    "Right idea, but without producer responsibility rules, mixed recyclables usually aren't worth reprocessing."
                } else {
                    "Density gets the headlines. Thermal runaway and recycling costs decide whether it ships at scale."
                }
            }

            PostModality.MediaCultureReview -> {
                "Still feels real for weirder indie films though. Critics reward the risk, audiences often don't."
            }

            PostModality.OpenSourceCraft -> {
                if (ctx.isOpenSourceRelease()) {
                    "The catch with open source is the feature requests. They grow faster than the time anyone has to maintain it."
                } else {
                    "Big wins like this are great, but the real test is how easy it is for the next person to maintain."
                }
            }

            PostModality.AuthorInquiry -> {
                "The usual answer works until concurrent writes spike, then latency gets ugly."
            }

            PostModality.StartupMarket -> {
                "Cheap pricing wins goodwill fast, but plenty of startups gave away too much before the numbers worked."
            }

            PostModality.ScienceNature -> {
                "Cool result, but I'd wait for someone to replicate it."
            }

            PostModality.SportsFitness -> {
                "Mileage builds the engine, but skipping strength work is how injuries sneak in."
            }

            PostModality.LifeMindset -> {
                "Strict schedules work in bursts, but some of the best ideas come from unplanned time."
            }

            PostModality.HumorBanter -> {
                "Honestly the best move is to grab popcorn and log off."
            }

            PostModality.HumanResilience -> {
                if (ctx.isOpenHardware()) {
                    "The open approach is admirable, but without a patent, cheap clones with worse safety standards are a real risk."
                } else {
                    "Willpower gets the credit, but good therapists and support people matter just as much."
                }
            }

            PostModality.GeneralObservation -> {
                "Mostly agree, though I think it depends a lot on the situation."
            }
        }.let { humanizeReply(it) }

    private fun buildPunchyReply(ctx: PostContext): String =
        when (ctx.modality) {
            PostModality.DevotionalSpiritual -> {
                "Hare Krishna 🙏 Peace to everyone."
            }

            PostModality.WeatherEmergency -> {
                "Hoping the storm weakens before it reaches ${ctx.primarySubject}."
            }

            PostModality.EmbeddedHardware -> {
                "${ctx.fps()} on a microcontroller. Wild."
            }

            PostModality.CivilLibertiesPrivacy -> {
                "Privacy and free expression shouldn't be up for negotiation."
            }

            PostModality.CivicGeopoliticsPolicy -> {
                "Build it well and the results will settle the argument."
            }

            PostModality.CybersecurityInfra -> {
                "Most security pain is too much noise, not too few tools."
            }

            PostModality.ArtificialIntelligence -> {
                "Less prompt fiddling, more reliable workflows."
            }

            PostModality.CleanTechEnergy -> {
                if (ctx.isCircular()) {
                    "Keep the resources in use. That's the whole point."
                } else {
                    "Show me the cycle life and I'm sold on the chemistry."
                }
            }

            PostModality.MediaCultureReview -> {
                "The divide was always louder online than in theaters."
            }

            PostModality.OpenSourceCraft -> {
                when {
                    ctx.isOpenSourceRelease() -> "Small, focused and open. Nice."
                    ctx.metricHook != null -> "${ctx.metricHook} is a serious jump."
                    else -> "Nice, clean work."
                }
            }

            PostModality.AuthorInquiry -> {
                "Boring and reliable first. Optimize when it hurts."
            }

            PostModality.StartupMarket -> {
                "Simple products are easier to sell and easier to fix."
            }

            PostModality.ScienceNature -> {
                "Can't wait to see if this holds up."
            }

            PostModality.SportsFitness -> {
                "The boring days are where it's built."
            }

            PostModality.LifeMindset -> {
                "Guard your attention. Most of it gets wasted on noise."
            }

            PostModality.HumorBanter -> {
                "You couldn't script this if you tried."
            }

            PostModality.HumanResilience -> {
                if (ctx.isOpenHardware()) "Tools that change lives shouldn't be luxury goods." else "So happy for you. Keep going."
            }

            PostModality.GeneralObservation -> {
                ctx.metricHook?.let { "$it is a serious jump." } ?: ctx.namedSubject?.let { "Fair point on $it." } ?: "Fair point."
            }
        }.let { humanizeReply(it) }

    /**
     * Re-voices a reply for the chosen tone. Tone variants stay anchored to the post's own
     * details (numbers, named tools) so a tone switch never swaps in a reply about nothing.
     */
    private fun adaptReplyToTone(
        originalReply: String,
        tone: SmartReplyTone,
        angle: SmartReplyAngle,
        ctx: PostContext,
    ): String {
        if (tone == SmartReplyTone.Practitioner) return originalReply
        // Devotional and emergency posts keep their respectful register in every tone.
        if (ctx.modality == PostModality.DevotionalSpiritual || ctx.modality == PostModality.WeatherEmergency) {
            return originalReply
        }
        val metric = ctx.metricHook
        val base = originalReply.trimEnd()

        return when (tone) {
            SmartReplyTone.Practitioner -> {
                originalReply
            }

            SmartReplyTone.Casual -> {
                when (angle) {
                    SmartReplyAngle.Insight -> {
                        metric?.let { "Okay, $it is wild. Nice work." } ?: base
                    }

                    SmartReplyAngle.Question -> {
                        if (base.startsWith("Genuinely curious", ignoreCase = true)) {
                            base
                        } else {
                            "Genuinely curious, ${base.decapitalized()}"
                        }
                    }

                    SmartReplyAngle.Counterpoint -> {
                        if (base.startsWith("Honestly", ignoreCase = true)) {
                            base
                        } else {
                            "Honestly, ${base.decapitalized()}"
                        }
                    }

                    SmartReplyAngle.Punchy -> {
                        metric?.let { "$it. Nice." } ?: base
                    }
                }
            }

            SmartReplyTone.Socratic -> {
                when (angle) {
                    SmartReplyAngle.Insight -> {
                        base
                    }

                    SmartReplyAngle.Question -> {
                        "What would have to be true for this to turn out wrong?"
                    }

                    SmartReplyAngle.Counterpoint -> {
                        val stripped = base.replace(Regex("(?i)^honestly[,\\s]+"), "").trimStart()
                        "Playing devil's advocate, ${stripped.decapitalized()}"
                    }

                    SmartReplyAngle.Punchy -> {
                        metric?.let { "$it is great. What did it cost?" } ?: "Every choice like this trades something. Worth knowing what."
                    }
                }
            }

            SmartReplyTone.Executive -> {
                when (angle) {
                    SmartReplyAngle.Insight -> {
                        metric?.let { "$it is the kind of result that changes what a team can promise customers." } ?: base
                    }

                    SmartReplyAngle.Question -> {
                        "What's the cost of getting this wrong, in money or team time?"
                    }

                    SmartReplyAngle.Counterpoint -> {
                        "The rollout is the easy part. Keeping costs sane as requirements grow is harder."
                    }

                    SmartReplyAngle.Punchy -> {
                        metric?.let { "$it pays for itself fast." } ?: base
                    }
                }
            }

            SmartReplyTone.HumbleLearner -> {
                when (angle) {
                    SmartReplyAngle.Insight -> base
                    SmartReplyAngle.Question -> "If you were starting from scratch, what would you do differently?"
                    SmartReplyAngle.Counterpoint -> "What should someone new to this watch out for before trying it?"
                    SmartReplyAngle.Punchy -> metric?.let { "$it, taking notes." } ?: "Taking notes on this."
                }
            }
        }.let { humanizeReply(it) }
    }

    // --- On-device refinement variants (alternate phrasings for the "refine" action) ---

    private fun buildRefinedInsightReply(ctx: PostContext): String {
        val lower = ctx.cleanText.lowercase()
        return when (ctx.modality) {
            PostModality.CivicGeopoliticsPolicy -> {
                "Progress on the ground matters more than how the project trends online."
            }

            PostModality.DevotionalSpiritual -> {
                "A beautiful darshan of ${ctx.primarySubject}. Even a few seconds looking at this brings some peace."
            }

            PostModality.WeatherEmergency -> {
                "Early warnings like this save lives. Keeping boats ashore in ${ctx.primarySubject} is the right call."
            }

            PostModality.EmbeddedHardware -> {
                "Tight memory budgets force cleaner code. Getting ${ctx.fps()} out of ${ctx.chip("a microcontroller")} proves it."
            }

            PostModality.CivilLibertiesPrivacy -> {
                "Once liability rules push platforms into over-removing content, it's very hard to undo. Getting encryption and user rights in from day one matters."
            }

            PostModality.CybersecurityInfra -> {
                when {
                    "threema" in lower || "administration" in lower || "enterprise" in lower -> {
                        "Centralized administration across desktop and mobile is what makes ${ctx.primarySubject} workable for bigger teams."
                    }

                    "risk surface" in lower -> {
                        "With a risk surface this sprawling, secure defaults beat another monitoring tool."
                    }

                    else -> {
                        "For ${ctx.primarySubject}, a smaller attack surface beats another alerting layer."
                    }
                }
            }

            PostModality.ArtificialIntelligence -> {
                if (ctx.isLocalAi()) {
                    "Running ${ctx.primarySubject} locally means no cloud latency and no data leaving the device. That's a real advantage."
                } else {
                    "The teams getting real value from ${ctx.primarySubject} obsess over caching and reliability, not model size."
                }
            }

            PostModality.CleanTechEnergy -> {
                if (ctx.isCircular()) {
                    "Recycling only works when the economics work. That means treating collection and sorting as real infrastructure."
                } else {
                    "Scaling ${ctx.primarySubject} is mostly a yield and supply chain problem now, not a chemistry one."
                }
            }

            PostModality.LifeMindset -> {
                if (ctx.isUnconventional()) {
                    "Most people slowly sand off their odd edges to fit in. Keeping them is a choice you make over and over."
                } else {
                    "Guarding your attention is underrated. Fewer inputs, better thinking."
                }
            }

            PostModality.OpenSourceCraft -> {
                if (ctx.isOpenSourceRelease()) {
                    "Open-sourcing ${ctx.primarySubject} gets people building with it fast. Good docs will keep them around."
                } else {
                    "The best part of work like this is usually the stuff nobody sees, like fewer weird bugs at 2am."
                }
            }

            PostModality.StartupMarket -> {
                "Keeping ${ctx.primarySubject} simple is what gives you room to hear what customers actually need."
            }

            PostModality.MediaCultureReview -> {
                ctx.namedSubject?.let {
                    "$it is one of those releases where the small craft choices stick with you afterward."
                } ?: "When a piece lands like this, it's usually the small craft details doing the heavy lifting."
            }

            PostModality.ScienceNature -> {
                ctx.namedSubject?.let {
                    "Seeing $it play out in real data makes the theory click in a different way."
                } ?: "Real-world measurements like this always turn up details clean models miss."
            }

            PostModality.SportsFitness -> {
                "Consistency in the quiet sessions shows up way more than one heroic workout."
            }

            PostModality.HumorBanter -> {
                "Pretty much everyone reading this has lived through a version of it this week."
            }

            PostModality.AuthorInquiry -> {
                "Putting ${ctx.primarySubject} into words cleanly shows every gap in what you thought you knew."
            }

            PostModality.HumanResilience -> {
                buildInsightReply(ctx)
            }

            else -> {
                ctx.namedSubject?.let { "Curious to see where $it goes from here." } ?: "Curious to see where this goes."
            }
        }.let { humanizeReply(it) }
    }

    private fun buildRefinedQuestionReply(ctx: PostContext): String {
        val lower = ctx.cleanText.lowercase()
        return when (ctx.modality) {
            PostModality.CivicGeopoliticsPolicy -> {
                "What's actually slowing these projects down, approvals or logistics?"
            }

            PostModality.DevotionalSpiritual -> {
                "Is there a story behind this altar or celebration?"
            }

            PostModality.WeatherEmergency -> {
                "Are backup power and phone networks ready in ${ctx.primarySubject} if the lines go down?"
            }

            PostModality.EmbeddedHardware -> {
                "Are you double-buffering on ${ctx.chip("the chip")}, or would that eat too much RAM?"
            }

            PostModality.CivilLibertiesPrivacy -> {
                "Will civil society groups get any say in how these rules are enforced?"
            }

            PostModality.CybersecurityInfra -> {
                if ("threema" in lower || "administration" in lower || "enterprise" in lower) {
                    "How does key distribution work when you roll ${ctx.primarySubject} out across mixed devices?"
                } else {
                    "How do you keep track of identity when services spin up and down across regions?"
                }
            }

            PostModality.ArtificialIntelligence -> {
                if (ctx.isLocalAi()) {
                    "Which quantization level gave you the best balance of speed and quality for ${ctx.primarySubject}?"
                } else {
                    "What happens when the model hits a case it can't handle? Is there a fallback?"
                }
            }

            PostModality.CleanTechEnergy -> {
                if (ctx.isCircular()) {
                    "Can recycled plastic compete with virgin resin on price right now?"
                } else {
                    "How are you handling heat during fast charging without hurting cycle life?"
                }
            }

            PostModality.LifeMindset -> {
                if (ctx.isUnconventional()) {
                    "What habit helps you keep your own opinions when there's pressure to fit in?"
                } else {
                    "What's one habit that keeps you clear-headed when everything feels urgent?"
                }
            }

            PostModality.OpenSourceCraft, PostModality.MediaCultureReview -> {
                buildQuestionReply(ctx)
            }

            PostModality.StartupMarket -> {
                "What did customers push back on most before you narrowed down ${ctx.primarySubject}?"
            }

            PostModality.ScienceNature -> {
                val target = ctx.namedSubject ?: "the observations"
                "Did $target match what earlier models predicted, or was there a surprise?"
            }

            PostModality.SportsFitness -> {
                "What changed most in your pacing or recovery routine leading up to this?"
            }

            PostModality.HumorBanter -> {
                "Wait, is this based on something that actually happened today?"
            }

            PostModality.AuthorInquiry -> {
                "What triggered this question while you were working through ${ctx.primarySubject}?"
            }

            PostModality.HumanResilience -> {
                "What helped you stay steady through the hardest stretch of that?"
            }

            else -> {
                ctx.namedSubject?.let { "What part of $it surprised you most once you dug in?" }
                    ?: "What was the hardest part to get right here?"
            }
        }.let { humanizeReply(it) }
    }

    private fun buildRefinedCounterpointReply(ctx: PostContext): String {
        val lower = ctx.cleanText.lowercase()
        return when (ctx.modality) {
            PostModality.CivicGeopoliticsPolicy -> {
                "Fair to push ahead, but being open about the local impact is what keeps critics from having a point."
            }

            PostModality.DevotionalSpiritual -> {
                "The quiet devotion inside matters as much as the celebration outside."
            }

            PostModality.WeatherEmergency -> {
                "The fishing ban is right, but compensation needs to reach families quickly, not weeks later."
            }

            PostModality.EmbeddedHardware -> {
                "Visual builders lower the bar, but multi-voice audio will probably still need hand-tuned code."
            }

            PostModality.CivilLibertiesPrivacy -> {
                "Strong encryption matters, though police will keep pushing for targeted access, and that fight isn't going away."
            }

            PostModality.CybersecurityInfra -> {
                if ("threema" in lower || "administration" in lower || "enterprise" in lower) {
                    "Central control helps with compliance, but if onboarding is clunky people drift back to WhatsApp."
                } else {
                    "Tooling helps, but most breaches still start with someone sharing a password."
                }
            }

            PostModality.ArtificialIntelligence -> {
                if (ctx.isLocalAi()) {
                    "Local is great for privacy, but noisy audio and thermal throttling on phones are still rough."
                } else {
                    "Small models are fast and private, but they still struggle once reasoning gets multi-step."
                }
            }

            PostModality.CleanTechEnergy -> {
                if (ctx.isCircular()) {
                    "Awareness helps, but recycled feedstock has to match virgin on purity and cost or it won't scale."
                } else {
                    "Lab densities don't mean much until yields hold up on a real production line."
                }
            }

            PostModality.LifeMindset -> {
                "Going your own way sounds romantic, but it usually means years of unglamorous groundwork first."
            }

            PostModality.OpenSourceCraft -> {
                buildCounterpointReply(ctx)
            }

            PostModality.MediaCultureReview -> {
                "High production polish doesn't guarantee a story stays with you once the credits roll."
            }

            PostModality.StartupMarket -> {
                "Shipping fast gets you started, though distribution usually decides whether ${ctx.primarySubject} survives."
            }

            PostModality.ScienceNature -> {
                "The early result is encouraging, though replication under messier conditions will tell the real story."
            }

            PostModality.SportsFitness -> {
                "Big numbers on race day are fun, though staying healthy across the whole training block is the real win."
            }

            PostModality.HumorBanter -> {
                "Funny because it's true, even if nobody wants to admit it out loud."
            }

            PostModality.AuthorInquiry -> {
                "Hard to give a clean answer without seeing the constraints you're working around."
            }

            PostModality.HumanResilience -> {
                "Grit matters, though having even one reliable person in your corner changes the whole math."
            }

            else -> {
                "Mostly agree, but there are cases where the opposite works better."
            }
        }.let { humanizeReply(it) }
    }

    private fun buildRefinedPunchyReply(ctx: PostContext): String {
        val lower = ctx.cleanText.lowercase()
        return when (ctx.modality) {
            PostModality.CivicGeopoliticsPolicy -> {
                "Deliver the work and the noise fades."
            }

            PostModality.DevotionalSpiritual -> {
                "Peace and gratitude to all 🙏"
            }

            PostModality.WeatherEmergency -> {
                "Stay safe and stay indoors, everyone."
            }

            PostModality.EmbeddedHardware -> {
                "${ctx.fps()} on ${ctx.chip("a microcontroller")}. Seriously impressive."
            }

            PostModality.CivilLibertiesPrivacy -> {
                "Online rights shouldn't be negotiable."
            }

            PostModality.CybersecurityInfra -> {
                if ("threema" in lower ||
                    "enterprise" in lower
                ) {
                    "Admin control without giving up privacy. Nice."
                } else {
                    "Smaller attack surface, fewer 2am pages."
                }
            }

            PostModality.ArtificialIntelligence -> {
                "Reliability over benchmarks, every time."
            }

            PostModality.CleanTechEnergy -> {
                if (ctx.isCircular()) "Recycling is infrastructure. Treat it like it." else "Show me the production yields."
            }

            PostModality.LifeMindset -> {
                "Protect your attention."
            }

            PostModality.OpenSourceCraft -> {
                if (ctx.isOpenSourceRelease()) {
                    "Small, focused, open. Love that."
                } else {
                    ctx.metricHook?.let { "$it. Seriously nice work." }
                        ?: "Seriously nice work."
                }
            }

            PostModality.MediaCultureReview -> {
                ctx.namedSubject?.let { "Now I need to check out $it." } ?: "Now I want to check this out."
            }

            PostModality.StartupMarket -> {
                "Ship simple, listen closely, adjust fast."
            }

            PostModality.ScienceNature -> {
                "Nature still finds ways to surprise the models."
            }

            PostModality.SportsFitness -> {
                "The quiet training days always show up later."
            }

            PostModality.HumorBanter -> {
                "Too real for a weekday."
            }

            PostModality.AuthorInquiry -> {
                "Good question. Curious what comes back."
            }

            PostModality.HumanResilience -> {
                "Showing up anyway is half the battle."
            }

            else -> {
                ctx.namedSubject?.let { "Fair take on $it." } ?: "Fair take."
            }
        }.let { humanizeReply(it) }
    }

    // --- Human-voice cleanup, applied to every reply (model output and offline lines) ---

    internal const val MAX_REPLY_WORDS: Int = 45

    private val CannedClichés: List<String> =
        listOf(
            "spot on",
            "totally agree",
            "couldn't agree more",
            "could not agree more",
            "great post",
            "love this",
            "thanks for sharing",
            "thank you for sharing",
            "100% this",
            "this is spot on",
            "so true",
            "here is the quiet truth",
            "here's the quiet truth",
        )

    // A first sentence that only praises or summarises the post ("A very sharp take on X.").
    private val PraisePreamble =
        Regex(
            "(?i)^(?:(?:a|an|such a|what a|this is a|this is an|that's a|that is a)\\s+)?" +
                "(?:(?:very|really|truly|so|super|incredibly)\\s+)?" +
                "(?:great|sharp|thoughtful|fantastic|brilliant|insightful|excellent|powerful|fascinating|" +
                "interesting|amazing|important|crucial|refreshing|solid|wonderful|compelling)\\b",
        )
    private val PraisePhrasePreamble =
        Regex(
            "(?i)^(?:it'?s refreshing|really appreciate|i really appreciate|love (?:this|how|that|the)|" +
                "great to see|well said|absolutely|so true|this resonates|what a)\\b",
        )
    private val SentenceBreak = Regex("(?<=[.!?])\\s+(?=[\\p{Lu}0-9\"'])")

    internal fun humanizeReply(text: String): String {
        var result =
            text
                .trim()
                .removeSurrounding("\"")
                .replace("**", "")
                .replace(Regex("#\\w+"), "")
                // Number ranges keep a plain hyphen; dashes between words become commas.
                .replace(Regex("(\\d)\\s*[—–]\\s*(\\d)"), "$1-$2")
                .replace(Regex("\\s*[—–]+\\s*"), ", ")
                .replace(Regex("\\s+--\\s+"), ", ")
                .replace(Regex("\\s+"), " ")
                .trim()

        for (cliche in CannedClichés) {
            result = result.replace(Regex("(?i)^" + Regex.escape(cliche) + "[!.,\\s-]*"), "")
        }

        var sentences = result.split(SentenceBreak).map { it.trim() }.filter { it.isNotEmpty() }

        // Drop a praise/summary opener when there's something real after it.
        if (sentences.size > 1 &&
            sentences.first().let { PraisePreamble.containsMatchIn(it) || PraisePhrasePreamble.containsMatchIn(it) }
        ) {
            sentences = sentences.drop(1)
        }

        // Drop whole sentences built around AI tropes; fall back to word removal if that empties it.
        val tropeRegexes = OmniScribeEngine.BannedAiTropes.map { Regex("(?i)\\b" + Regex.escape(it) + "\\b") }
        val withoutTropes = sentences.filter { sentence -> tropeRegexes.none { it.containsMatchIn(sentence) } }
        sentences =
            withoutTropes.ifEmpty {
                sentences.map { sentence -> tropeRegexes.fold(sentence) { acc, regex -> acc.replace(regex, "") } }
            }

        // Keep it reply-sized: whole sentences up to the word cap (always at least one).
        val kept = mutableListOf<String>()
        var words = 0
        for (sentence in sentences) {
            val count = sentence.split(" ").size
            if (kept.isNotEmpty() && words + count > MAX_REPLY_WORDS) break
            kept += sentence
            words += count
        }
        result = kept.joinToString(" ")

        // One exclamation mark reads human; a string of them reads like marketing.
        result = result.replace(Regex("!{2,}"), "!")
        if (result.count { it == '!' } > 1) result = result.replace('!', '.')

        return result
            .replace(Regex("\\s+([,.!?])"), "$1")
            .replace(Regex(",\\s*,"), ",")
            .replace(Regex("\\s+"), " ")
            .trim()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
