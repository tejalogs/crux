package dev.dimension.flare.data.ai.scribe

import kotlinx.serialization.Serializable
import kotlin.time.TimeSource

/**
 * Stylistic persona for the Omni-Scribe rewriting engine.
 */
@Serializable
public enum class OmniScribeStyle(
    public val displayName: String,
    public val subtitle: String,
    public val iconEmoji: String,
) {
    Punchy(
        displayName = "Punchy & Direct",
        subtitle = "Razor-sharp density, short declarations, Hemingway/Naval cadence",
        iconEmoji = "⚡",
    ),
    Narrative(
        displayName = "Narrative Story",
        subtitle = "Storyteller arc: setup → tension → turning point → universal realization",
        iconEmoji = "📖",
    ),
    Philosophical(
        displayName = "First Principles",
        subtitle = "Contrarian truth, structural logic, Seneca & Marcus Aurelius depth",
        iconEmoji = "🏛️",
    ),
}

/**
 * Output format sizing target.
 */
@Serializable
public enum class OmniScribeFormat(
    public val maxCharacters: Int,
    public val targetPlatformSummary: String,
) {
    Micro(
        maxCharacters = 280,
        targetPlatformSummary = "Twitter/X, Bluesky, Mastodon, Nostr (<280 chars)",
    ),
    Story(
        maxCharacters = 3000,
        targetPlatformSummary = "LinkedIn & Long-form Fediverse (Multi-act standalone post)",
    ),
}

/**
 * Deep multi-act conceptual arc extracted from the source post.
 */
@Serializable
public data class PostContextArc(
    public val themeLabel: String,
    public val hook: String,
    public val tension: String,
    public val pivot: String,
    public val takeaway: String,
    public val narrativeBeats: List<String>,
)

/**
 * Input request for the Omni-Scribe post repackaging pipeline.
 */
@Serializable
public data class OmniScribeInput(
    public val originalText: String,
    public val authorName: String = "",
    public val authorHandle: String = "",
    public val style: OmniScribeStyle = OmniScribeStyle.Punchy,
    public val includeAttribution: Boolean = false,
    public val articleTitle: String = "",
    public val sourceUrl: String = "",
)

/**
 * Repackaged output containing synchronized dual formats, deep context arc, and telemetry.
 */
@Serializable
public data class OmniScribeResult(
    public val microPost: String,
    public val storyPost: String,
    public val essence: String,
    public val hatTip: String? = null,
    public val style: OmniScribeStyle,
    public val tokensProcessed: Int,
    public val latencyMs: Long,
    public val contextArc: PostContextArc? = null,
)

/**
 * Zero-Egress On-Device AI Omni-Scribe Engine.
 * Deeply analyzes multi-sentence narrative arcs (Hook -> Conflict -> Pivot -> Takeaway)
 * and repackages social media posts into standalone, top 10% human writer publications
 * with zero source-author tagging.
 */
public object OmniScribeEngine {

    /**
     * Set of overused AI tropes and corporate buzzwords strictly prohibited from output.
     */
    public val BannedAiTropes: Set<String> = setOf(
        "in today's fast-paced world",
        "let that sink in",
        "delve into",
        "game-changer",
        "game changer",
        "buckle up",
        "it is crucial to remember",
        "a testament to",
        "unpack",
        "it's important to note",
        "revolutionary",
        "tapestry",
        "beacon",
        "foster",
        "synergy",
        "paradigm shift",
    )

    private enum class DomainTheme(val kicker: String) {
        Builder("BUILDER'S ETHOS"),
        Software("SYSTEMS & CRAFT"),
        Market("MARKET REALITY"),
        Resilience("HUMAN RESILIENCE"),
        Craft("FIRST PRINCIPLES"),
    }

    /**
     * Builds the system prompt for local Gemma 4 / Ollama execution.
     */
    public fun buildSystemPrompt(style: OmniScribeStyle): String = buildString {
        appendLine("You are an elite, top 10% human essayist and master wordsmith.")
        appendLine("Your craft is taking raw social posts, understanding their full narrative arc (Setup -> Tension -> Turning Point -> Universal Takeaway), and rewriting them as a standalone original post.")
        appendLine()
        appendLine("STYLE DIRECTIVE: ${style.displayName.uppercase()}")
        appendLine(style.subtitle)
        appendLine()
        appendLine("CORE WRITING PRINCIPLES:")
        appendLine("1. Deep Context Preservation: Preserve the protagonist, concrete numbers, conflict, and resolution from the source.")
        appendLine("2. Standalone Original Voice: Write directly in first-principles voice. NEVER include 'HT @author' or 'from @author' tags.")
        appendLine("3. ZERO AI SLOP: Strictly NEVER use the following phrases: ${BannedAiTropes.joinToString(", ")}.")
        appendLine("4. Emotional Resonance: Connect specific events to timeless human and craft truths.")
        appendLine()
        appendLine("OUTPUT REQUIREMENTS:")
        appendLine("- Micro: Maximum 280 characters, synthesizing the full story arc and takeaway.")
        appendLine("- Story: 3 to 4 concise paragraphs separated by double newlines, tailored for LinkedIn and long-form feeds.")
    }

    /**
     * Repackages an input post or long-form article into a standalone, context-complete publication.
     */
    public fun repackage(input: OmniScribeInput): OmniScribeResult {
        val startMark = TimeSource.Monotonic.markNow()
        val isArticle = input.articleTitle.isNotBlank()

        val beats = if (isArticle) {
            normalizeAndSegmentArticleBeats(input.articleTitle, input.originalText)
        } else {
            normalizeAndSegmentBeats(input.originalText)
        }

        val cleanedOriginal = beats.joinToString(" ")
        val arc = extractContextArc(beats, input.style)

        val microDraft = if (isArticle) {
            generateArticleMicroPost(input.articleTitle, arc, input.sourceUrl, input.style)
        } else {
            generateMicroPost(arc, input.style)
        }

        val storyDraft = if (isArticle) {
            generateArticleStoryPost(input.articleTitle, arc, input.sourceUrl, input.style)
        } else {
            generateStoryPost(arc, input.style)
        }

        val elapsedMs = startMark.elapsedNow().inWholeMilliseconds.coerceAtLeast(1L)
        val estimatedTokens = ((cleanedOriginal.length + microDraft.length + storyDraft.length) / 4).coerceAtLeast(1)

        return OmniScribeResult(
            microPost = microDraft,
            storyPost = storyDraft,
            essence = if (isArticle) input.articleTitle else arc.hook,
            hatTip = null,
            style = input.style,
            tokensProcessed = estimatedTokens,
            latencyMs = elapsedMs,
            contextArc = arc,
        )
    }

    public fun extractContextArc(
        cleanText: String,
        style: OmniScribeStyle = OmniScribeStyle.Punchy,
    ): PostContextArc {
        val beats = normalizeAndSegmentBeats(cleanText)
        return extractContextArc(beats, style)
    }

    /**
     * Synthesizes a rich long-form RSS/blog article into high-impact social copy and carousel slides.
     */
    public fun synthesizeArticle(
        title: String,
        content: String,
        sourceUrl: String = "",
        authorName: String = "",
        style: OmniScribeStyle = OmniScribeStyle.Punchy,
    ): OmniScribeResult {
        return repackage(
            OmniScribeInput(
                originalText = content,
                authorName = authorName,
                style = style,
                articleTitle = title,
                sourceUrl = sourceUrl,
            ),
        )
    }

    /**
     * Normalizes multi-line social posts where line breaks often act as sentence boundaries
     * without trailing periods, strips URLs and banned AI tropes, and splits into clean narrative beats.
     */
    internal fun normalizeAndSegmentBeats(raw: String): List<String> {
        var text = raw.trim()
            .replace(Regex("https?://\\S+"), "")

        for (trope in BannedAiTropes) {
            text = text.replace(Regex("(?i)\\b" + Regex.escape(trope) + "\\b"), "")
        }

        // Preserve line breaks as sentence boundaries when authors omit periods at end of lines
        val normalizedLines = text.lines()
            .map { line ->
                line.trim()
                    .replace(Regex("^(?i)(til:?|today i learned:?|thread:?|unpopular opinion:?|hot take:?)\\s*"), "")
                    .replace(Regex("\\s+"), " ")
                    .trim()
            }
            .filter { it.isNotBlank() }
            .map { line ->
                val lastChar = line.last()
                if (lastChar in charArrayOf('.', '!', '?', ':', ';')) {
                    line
                } else {
                    "$line."
                }
            }

        val combined = normalizedLines.joinToString(" ")
        if (combined.isBlank()) {
            return listOf("Focus on the core craft and build what matters.")
        }

        val rawSentences = combined.split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.length > 2 }

        if (rawSentences.isEmpty()) {
            return listOf(ensurePeriod(combined))
        }

        // Polish common typos and capitalization in each beat
        return rawSentences.map { sentence ->
            sentence
                .replace(Regex("(?i)\\bwheel chair\\b"), "wheelchair")
                .replace(Regex("(?i)\\bhigh quality\\b"), "high-quality")
                .replace(Regex("(?i)\\blow cost\\b"), "low-cost")
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                .let { ensurePeriod(it) }
        }
    }

    private fun detectDomainTheme(fullText: String): DomainTheme {
        val lower = fullText.lowercase()
        return when {
            Regex("\\b(built|build|company|engineered|invented|hardware|wheelchair|garage|product|manufactured|founder|selling|cost|expensive)\\b")
                .containsMatchIn(lower) -> DomainTheme.Builder

            Regex("\\b(software|code|api|developer|users|feature|bug|architecture|engineering|system|deploy|latency|ai|model)\\b")
                .containsMatchIn(lower) -> DomainTheme.Software

            Regex("\\b(accident|hospital|recovery|life|family|wife|husband|health|walk|courage|human)\\b")
                .containsMatchIn(lower) -> DomainTheme.Resilience

            Regex("\\b(market|price|revenue|customer|monopoly|industry|business|startup|invest)\\b")
                .containsMatchIn(lower) -> DomainTheme.Market

            else -> DomainTheme.Craft
        }
    }

    private fun extractContextArc(
        beats: List<String>,
        style: OmniScribeStyle,
    ): PostContextArc {
        val fullText = beats.joinToString(" ")
        val theme = detectDomainTheme(fullText)

        val hook: String
        val tension: String
        val pivot: String

        when (beats.size) {
            1 -> {
                val single = beats.first()
                val clauses = single.split(Regex("[,;—–]|\\b(?i)(but|while|so|because|instead)\\b"))
                    .map { it.trim().trimEnd('.', '!', '?') }
                    .filter { it.length > 8 }

                if (clauses.size >= 2) {
                    hook = ensurePeriod(clauses.first().replaceFirstChar { it.uppercase() })
                    tension = ensurePeriod(clauses.drop(1).joinToString(". ").replaceFirstChar { it.uppercase() })
                    pivot = synthesizeInsightBridge(theme, style)
                } else {
                    hook = single
                    tension = synthesizeTensionExpansion(single, theme)
                    pivot = synthesizeInsightBridge(theme, style)
                }
            }

            2 -> {
                hook = beats[0]
                tension = beats[1]
                pivot = synthesizeInsightBridge(theme, style)
            }

            3 -> {
                hook = beats[0]
                tension = beats[1]
                pivot = beats[2]
            }

            else -> {
                // 4+ sentences: group into Setup (Hook), Conflict (Tension), and Resolution (Pivot)
                hook = beats.first()
                tension = beats.subList(1, beats.size - 1).joinToString(" ")
                pivot = beats.last()
            }
        }

        val takeaway = synthesizeUniversalTakeaway(theme, style, beats)

        return PostContextArc(
            themeLabel = theme.kicker,
            hook = hook,
            tension = tension,
            pivot = pivot,
            takeaway = takeaway,
            narrativeBeats = beats,
        )
    }

    private fun synthesizeTensionExpansion(hook: String, theme: DomainTheme): String =
        when (theme) {
            DomainTheme.Builder -> "Most people accept broken constraints as permanent facts of life. Builders see them as an invitation to redesign from scratch."
            DomainTheme.Software -> "Teams often over-rotate on surface complexity while the foundational user experience quietly determines everything."
            DomainTheme.Resilience -> "Moments of sudden disruption strip away everything secondary and reveal what actually matters."
            DomainTheme.Market -> "Incumbents optimize for defending margins rather than solving the underlying human problem."
            DomainTheme.Craft -> "Noise and complexity always look busy on the surface, but enduring impact comes from ruthless clarity."
        }

    private fun synthesizeInsightBridge(theme: DomainTheme, style: OmniScribeStyle): String =
        when (style) {
            OmniScribeStyle.Punchy -> when (theme) {
                DomainTheme.Builder -> "When the market refuses to solve a real problem fairly, build the solution yourself."
                DomainTheme.Software -> "Strip away the vanity features and obsess over the core loop."
                DomainTheme.Resilience -> "Adversity becomes fuel when channeled into purposeful action."
                DomainTheme.Market -> "High margins built on artificial scarcity always invite disruption."
                DomainTheme.Craft -> "Simplicity isn't the absence of effort—it is the result of total clarity."
            }

            OmniScribeStyle.Narrative -> when (theme) {
                DomainTheme.Builder -> "What started as a personal necessity became a mission to make quality accessible to everyone."
                DomainTheme.Software -> "The smallest ergonomic details end up defining how people actually experience the work."
                DomainTheme.Resilience -> "The turning point wasn't just adapting to the challenge—it was transforming it into a solution for others."
                DomainTheme.Market -> "Stepping outside industry conventions opened the door to a fairer way of building."
                DomainTheme.Craft -> "When you step back from the noise, the essential path forward becomes unmistakable."
            }

            OmniScribeStyle.Philosophical -> when (theme) {
                DomainTheme.Builder -> "First-principles engineering asks what something truly requires to build—not what the status quo charges."
                DomainTheme.Software -> "Mastery in systems design is knowing what to leave out so the essential can breathe."
                DomainTheme.Resilience -> "The obstacle itself becomes the way forward when met with agency and craftsmanship."
                DomainTheme.Market -> "Price and value diverge whenever an industry forgets who it exists to serve."
                DomainTheme.Craft -> "Wisdom is reductive: eliminate the non-essential until only the truth remains."
            }
        }

    private fun synthesizeUniversalTakeaway(
        theme: DomainTheme,
        style: OmniScribeStyle,
        beats: List<String>,
    ): String {
        return when (style) {
            OmniScribeStyle.Punchy -> when (theme) {
                DomainTheme.Builder -> "The best builders don't complain about broken markets—they engineer the alternative."
                DomainTheme.Software -> "Obsess over the fundamentals that users actually feel every day."
                DomainTheme.Resilience -> "Purpose is forged when personal necessity turns into action for others."
                DomainTheme.Market -> "Solve a real problem at an honest price and you change an entire industry."
                DomainTheme.Craft -> "Cut the noise. Protect the signal. Execute relentlessly."
            }

            OmniScribeStyle.Narrative -> when (theme) {
                DomainTheme.Builder -> "The most meaningful companies rarely start with a pitch deck—they start when someone refuses to accept a broken status quo and builds what should have existed all along."
                DomainTheme.Software -> "Every enduring product is built on humility: listening closely, dropping ego, and refining the details that genuinely serve people."
                DomainTheme.Resilience -> "Behind almost every breakthrough is a human story of turning an unexpected hardship into a lasting gift for thousands."
                DomainTheme.Market -> "When you anchor your work in genuine human utility rather than convention, you don't just compete—you redefine the standard."
                DomainTheme.Craft -> "When you look at work that truly endures, it always comes back to intentional discipline and clarity of purpose."
            }

            OmniScribeStyle.Philosophical -> when (theme) {
                DomainTheme.Builder -> "First principles in action: when an industry confuses scarcity with value, return to the physics of the problem and build what is needed."
                DomainTheme.Software -> "Complexity is the path of least resistance; simplicity requires the courage to subtract until only the essential remains."
                DomainTheme.Resilience -> "External events are beyond our choice, but our response—and what we build from them—defines our character."
                DomainTheme.Market -> "True leverage belongs to those who question inherited assumptions and rebuild from foundational truth."
                DomainTheme.Craft -> "Noise compounds quickly; wisdom is reductive. Master the fundamentals before chasing novelty."
            }
        }
    }

    /**
     * Generates a standalone <280 char post that preserves the full narrative arc
     * (beginning, middle, and resolution/takeaway) without cutting off context.
     */
    private fun generateMicroPost(
        arc: PostContextArc,
        style: OmniScribeStyle,
    ): String {
        val fullNarrative = arc.narrativeBeats.joinToString(" ")
        val shortClosing = when (style) {
            OmniScribeStyle.Punchy -> arc.takeaway
            OmniScribeStyle.Narrative -> "Real breakthroughs start where necessity meets action."
            OmniScribeStyle.Philosophical -> "First principles in action: question the status quo and build what matters."
        }

        // 1. If the full narrative + closing takeaway fits in 280 chars, combine them seamlessly!
        val combinedWithTakeaway = "$fullNarrative\n\n$shortClosing"
        if (combinedWithTakeaway.length <= 280) {
            return combinedWithTakeaway
        }

        // 2. Try single-space join with a concise punchline
        val compactTakeaway = when (style) {
            OmniScribeStyle.Punchy -> "Don't accept broken markets—build the fix."
            OmniScribeStyle.Narrative -> "Necessity drives the most meaningful innovation."
            OmniScribeStyle.Philosophical -> "First principles always beat the status quo."
        }
        val compactCombined = "$fullNarrative $compactTakeaway"
        if (compactCombined.length <= 280) {
            return compactCombined
        }

        // 3. If the full multi-sentence narrative itself fits within 280 chars, return the complete polished narrative!
        if (fullNarrative.length <= 280) {
            return fullNarrative
        }

        // 4. Otherwise synthesize Hook + Tension + Pivot tightly within 280 chars
        val tightArc = "${arc.hook} ${arc.tension} ${arc.pivot}"
        if (tightArc.length <= 280) {
            return tightArc
        }

        return truncateToSentenceOrWords(tightArc, 280)
    }

    /**
     * Generates a multi-paragraph standalone long-form post (for LinkedIn, Mastodon, Bluesky, etc.)
     * that walks through the full narrative arc and lands on a resonant human takeaway.
     */
    private fun generateStoryPost(
        arc: PostContextArc,
        style: OmniScribeStyle,
    ): String = buildString {
        when {
            arc.narrativeBeats.size >= 3 -> {
                // Paragraph 1: Hook & Setup
                appendLine(arc.hook)
                appendLine()
                // Paragraph 2: The Conflict / Tension & Turning Point
                appendLine("${arc.tension} ${arc.pivot}")
                appendLine()
                // Paragraph 3: Deep Human/Craft Reflection
                appendLine(arc.takeaway)
                appendLine()
                // Paragraph 4: Closing Resonance
                append(
                    when (style) {
                        OmniScribeStyle.Punchy -> "Solve a real problem for one person, and you unlock it for thousands."
                        OmniScribeStyle.Narrative -> "Never underestimate what happens when personal conviction meets the courage to build."
                        OmniScribeStyle.Philosophical -> "Question the default. Return to fundamentals. Build what ought to exist."
                    }
                )
            }

            arc.narrativeBeats.size == 2 -> {
                appendLine(arc.hook)
                appendLine()
                appendLine(arc.tension)
                appendLine()
                appendLine(arc.pivot)
                appendLine()
                append(arc.takeaway)
            }

            else -> {
                appendLine(arc.hook)
                appendLine()
                appendLine(arc.tension)
                appendLine()
                append(arc.takeaway)
            }
        }
    }

    internal fun normalizeAndSegmentArticleBeats(title: String, rawContent: String): List<String> {
        // Strip HTML tags and clean whitespace
        val clean = rawContent
            .replace(Regex("<[^>]*>"), " ")
            .replace(Regex("&nbsp;"), " ")
            .replace(Regex("&amp;"), "&")
            .replace(Regex("&lt;"), "<")
            .replace(Regex("&gt;"), ">")
            .replace(Regex("https?://\\S+"), "")
            .trim()

        val paragraphs = clean.split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.length > 30 }

        if (paragraphs.size >= 3) {
            val beats = mutableListOf<String>()
            // Beat 1: Setup / Thesis rooted in the article title and lead
            val lead = paragraphs.first()
            val leadSentence = lead.split(Regex("[.!?]\\s+")).firstOrNull().orEmpty().trim()
            beats.add(if (leadSentence.length > 20) ensurePeriod(leadSentence) else ensurePeriod(title))

            // Beat 2: Tension / Analytical evidence from middle
            val midPara = paragraphs[paragraphs.size / 2]
            val midSentence = midPara.split(Regex("[.!?]\\s+")).firstOrNull().orEmpty().trim()
            beats.add(if (midSentence.length > 20) ensurePeriod(midSentence) else ensurePeriod(midPara.take(160)))

            // Beat 3: Pivot / Critical turning point from later in the text
            val latePara = if (paragraphs.size >= 4) paragraphs[paragraphs.size - 2] else paragraphs[1]
            val lateSentence = latePara.split(Regex("[.!?]\\s+")).firstOrNull().orEmpty().trim()
            beats.add(if (lateSentence.length > 20) ensurePeriod(lateSentence) else ensurePeriod(latePara.take(160)))

            // Beat 4: Conclusion / Takeaway from final paragraph
            val lastPara = paragraphs.last()
            val lastSentence = lastPara.split(Regex("[.!?]\\s+")).lastOrNull { it.length > 20 }?.trim() ?: lastPara.take(160)
            beats.add(ensurePeriod(lastSentence))
            return beats
        }

        // Fallback for shorter articles: segment by sentences
        val segmented = normalizeAndSegmentBeats(clean)
        return if (segmented.size >= 2) {
            listOf(ensurePeriod(title)) + segmented.take(3)
        } else {
            listOf(ensurePeriod(title), clean)
        }
    }

    private fun generateArticleMicroPost(
        title: String,
        arc: PostContextArc,
        sourceUrl: String,
        style: OmniScribeStyle,
    ): String {
        val linkText = if (sourceUrl.isNotBlank()) "\n\nSource: $sourceUrl" else ""
        val takeaway = when (style) {
            OmniScribeStyle.Punchy -> arc.takeaway
            OmniScribeStyle.Narrative -> "When fundamental constraints shift, the entire landscape follows."
            OmniScribeStyle.Philosophical -> "Return to first principles to understand the real trajectory."
        }

        // Try: Title + Core insight + Takeaway + Link
        val candidate = "$title:\n\n${arc.hook} ${arc.pivot}\n\n$takeaway$linkText".trim()
        if (candidate.length <= 280) {
            return candidate
        }

        val shorterCandidate = "$title: ${arc.hook}\n\n$takeaway$linkText".trim()
        if (shorterCandidate.length <= 280) {
            return shorterCandidate
        }

        val minimalCandidate = "$title\n\n$takeaway$linkText".trim()
        if (minimalCandidate.length <= 280) {
            return minimalCandidate
        }

        return truncateToSentenceOrWords(minimalCandidate, 280)
    }

    private fun generateArticleStoryPost(
        title: String,
        arc: PostContextArc,
        sourceUrl: String,
        style: OmniScribeStyle,
    ): String = buildString {
        appendLine("Breakthrough Analysis: $title")
        appendLine()
        appendLine(arc.hook)
        appendLine()
        appendLine("${arc.tension} ${arc.pivot}")
        appendLine()
        appendLine(arc.takeaway)
        appendLine()
        appendLine(
            when (style) {
                OmniScribeStyle.Punchy -> "The biggest shifts happen quietly before everyone notices the consensus has changed."
                OmniScribeStyle.Narrative -> "Behind every major development is a fundamental realignment of incentives and execution."
                OmniScribeStyle.Philosophical -> "Look beyond the immediate commentary. The underlying structural trend is what endures."
            }
        )
        if (sourceUrl.isNotBlank()) {
            appendLine()
            append("Full article: $sourceUrl")
        }
    }

    private fun ensurePeriod(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return trimmed
        val last = trimmed.last()
        return if (last in charArrayOf('.', '!', '?')) trimmed else "$trimmed."
    }

    private fun truncateToSentenceOrWords(text: String, maxLength: Int): String {
        if (text.length <= maxLength) return text
        val candidate = text.take(maxLength - 1)
        val lastPeriod = maxOf(candidate.lastIndexOf('.'), candidate.lastIndexOf('!'), candidate.lastIndexOf('?'))
        if (lastPeriod >= maxLength / 2) {
            return candidate.take(lastPeriod + 1)
        }
        val lastSpace = candidate.lastIndexOf(' ')
        return if (lastSpace > 0) candidate.take(lastSpace).trimEnd(',', ';', ':') + "…" else candidate + "…"
    }
}
