package org.crux.data.ai.scribe

/**
 * What the on-device model said about a rule-built carousel: overall direction, a few concrete
 * notes, and replacement text for the slides it fixed. Slides it did not fix are not listed.
 */
public data class CarouselReview(
    public val direction: String,
    public val notes: List<String>,
    public val fixes: Map<Int, SlideFix>,
) {
    public data class SlideFix(
        public val headline: String?,
        public val body: String?,
    )

    public val isEmpty: Boolean get() = direction.isBlank() && notes.isEmpty() && fixes.isEmpty()

    /** Applies the fixes to [slides] (matched by position) and returns a new list. */
    public fun applyTo(slides: List<CarouselSlide>): List<CarouselSlide> =
        slides.mapIndexed { position, slide ->
            val fix = fixes[position] ?: return@mapIndexed slide
            slide.copy(
                headline = fix.headline ?: slide.headline,
                body = fix.body ?: slide.body,
            )
        }
}

/**
 * The fixed rules in [CarouselDeckGenerator] build the deck; the model only reviews it. Keeping
 * the model in an editor role (short output, a strict line format, every fix validated) suits a
 * small on-device model and means a bad answer can never make the deck worse than the rule version.
 */
public object CarouselReviewer {
    public const val MAX_HEADLINE_CHARS: Int = 70
    public const val MAX_BODY_CHARS: Int = 260
    private const val MAX_NOTES = 3
    private const val MAX_DIRECTION_CHARS = 220
    private const val MAX_NOTE_CHARS = 160
    private const val MAX_SOURCE_CHARS = 2_500

    public fun buildReviewPrompt(
        slides: List<CarouselSlide>,
        sourceText: String,
    ): String =
        buildString {
            appendLine("You are an editor reviewing a social media carousel that was built from an article.")
            appendLine("Use only facts from the article. Do not invent numbers, names, quotes or claims.")
            appendLine("Keep each headline under $MAX_HEADLINE_CHARS characters and each body under $MAX_BODY_CHARS characters.")
            appendLine("Only rewrite slides that are unclear, repetitive, cut off mid-sentence or weak. Leave good slides out.")
            appendLine()
            appendLine("Reply in exactly this format and nothing else:")
            appendLine("DIRECTION: one sentence on how to make the whole carousel stronger")
            appendLine("NOTE: a specific problem you found (at most $MAX_NOTES NOTE lines)")
            appendLine("SLIDE <number> HEADLINE: improved headline")
            appendLine("SLIDE <number> BODY: improved body")
            appendLine()
            appendLine("ARTICLE:")
            appendLine(sourceText.trim().take(MAX_SOURCE_CHARS))
            appendLine()
            appendLine("CAROUSEL:")
            slides.forEachIndexed { position, slide ->
                appendLine("SLIDE ${position + 1} HEADLINE: ${slide.headline.oneLine()}")
                appendLine("SLIDE ${position + 1} BODY: ${slide.body.oneLine()}")
            }
        }.trim()

    /**
     * Parses the model's reply. Returns null when nothing usable came back. Fixes that are blank,
     * too long, identical to the original, or introduce numbers absent from the article and the
     * original slide are dropped.
     */
    public fun parseReview(
        raw: String?,
        slides: List<CarouselSlide>,
        sourceText: String,
    ): CarouselReview? {
        if (raw.isNullOrBlank()) return null
        var direction = ""
        val notes = mutableListOf<String>()
        val headlines = mutableMapOf<Int, String>()
        val bodies = mutableMapOf<Int, String>()

        raw.lineSequence().map { it.trim().trimStart('*', '-', '•', ' ') }.forEach { line ->
            val slideMatch = SLIDE_LINE.matchEntire(line)
            when {
                slideMatch != null -> {
                    val position = slideMatch.groupValues[1].toInt() - 1
                    val field = slideMatch.groupValues[2].uppercase()
                    val text = slideMatch.groupValues[3].cleanValue()
                    if (position in slides.indices && text.isNotBlank()) {
                        if (field == "HEADLINE") headlines[position] = text else bodies[position] = text
                    }
                }

                line.startsWith("DIRECTION:", ignoreCase = true) && direction.isBlank() -> {
                    direction = line.substringAfter(':').cleanValue().take(MAX_DIRECTION_CHARS)
                }

                line.startsWith("NOTE:", ignoreCase = true) && notes.size < MAX_NOTES -> {
                    line.substringAfter(':').cleanValue().take(MAX_NOTE_CHARS).takeIf { it.isNotBlank() }?.let(notes::add)
                }
            }
        }

        val fixes =
            slides.indices
                .mapNotNull { position ->
                    val original = slides[position]
                    val allowedNumbers = numbersIn(sourceText) + numbersIn(original.headline) + numbersIn(original.body)
                    val headline =
                        headlines[position]?.takeIf {
                            it.length <= MAX_HEADLINE_CHARS && it != original.headline && numbersIn(it).all(allowedNumbers::contains)
                        }
                    val body =
                        bodies[position]?.takeIf {
                            it.length <= MAX_BODY_CHARS && it != original.body && numbersIn(it).all(allowedNumbers::contains)
                        }
                    if (headline == null && body == null) null else position to CarouselReview.SlideFix(headline, body)
                }.toMap()

        return CarouselReview(direction, notes, fixes).takeUnless { it.isEmpty }
    }

    private val SLIDE_LINE = Regex("""SLIDE\s+(\d+)\s+(HEADLINE|BODY)\s*:\s*(.*)""", RegexOption.IGNORE_CASE)
    private val NUMBER = Regex("""\d+(?:[.,]\d+)?""")

    private fun numbersIn(text: String): Set<String> = NUMBER.findAll(text).map { it.value.replace(",", "") }.toSet()

    private fun String.oneLine(): String = replace(Regex("\\s+"), " ").trim()

    private fun String.cleanValue(): String =
        oneLine()
            .trim('"', '\'', '“', '”', '*', '`')
            .trim()
}
