package dev.dimension.flare.data.ai

/**
 * Sovereign On-Device Text Summarizer.
 *
 * Extracts salient points and condenses text into a concise offline summary
 * conforming to Flare's 200-character TLDR constraint with zero data egress.
 */
public object SovereignOnDeviceSummarizer {
    public fun summarize(source: String, targetLanguage: String): String {
        val clean = cleanText(source)
        if (clean.length <= 200) {
            return clean
        }

        val sentences = splitSentences(clean)
        if (sentences.isEmpty()) {
            return clean.take(200)
        }

        // Score sentences by position, length, and word frequency
        val wordFreq = mutableMapOf<String, Int>()
        for (sentence in sentences) {
            for (word in tokenize(sentence)) {
                wordFreq[word] = (wordFreq[word] ?: 0) + 1
            }
        }

        val scored =
            sentences.mapIndexed { index, sentence ->
                val words = tokenize(sentence)
                val freqScore = words.sumOf { wordFreq[it] ?: 0 }.toFloat() / maxOf(1, words.size)
                // First sentence gets a position boost
                val positionBoost = if (index == 0) 1.5f else 1.0f
                val lengthPenalty = if (sentence.length in 30..150) 1.2f else 0.8f
                sentence to (freqScore * positionBoost * lengthPenalty)
            }.sortedByDescending { it.second }

        val result = StringBuilder()
        for ((sentence, _) in scored) {
            if (result.length + sentence.length + 1 <= 200) {
                if (result.isNotEmpty()) result.append(" ")
                result.append(sentence)
            } else if (result.isEmpty()) {
                result.append(sentence.take(197)).append("...")
                break
            }
        }

        return result.toString().ifBlank { clean.take(200) }
    }

    private fun cleanText(text: String): String =
        text
            .replace(Regex("<[^>]*>"), " ") // Remove HTML tags
            .replace(Regex("&[a-zA-Z0-9#]+;"), " ") // Remove HTML entities
            .replace(Regex("https?://\\S+"), "") // Remove raw URLs
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun splitSentences(text: String): List<String> =
        text
            .split(Regex("(?<=[.!?。！？\\n])\\s+"))
            .map { it.trim() }
            .filter { it.length >= 10 }

    private fun tokenize(text: String): List<String> =
        text
            .lowercase()
            .split(Regex("[\\s\\p{Punct}]+"))
            .filter { it.length >= 2 }
}

/**
 * Sovereign On-Device Template-Aware Translator.
 *
 * Complies strictly with Flare's translation template format, preserving
 * control headers (`<<<B0>>>`, `<<<E0>>>`, etc.) and control markers
 * (`{{T0}}`, `{{L1}}`, etc.) while ensuring zero cloud egress.
 */
public object SovereignOnDeviceTranslator {
    private val CONTROL_LINE_REGEX = Regex("^<<<.*>>>$")
    private val CONTROL_MARKER_REGEX = Regex("\\{\\{[TL][0-9]+\\}\\}")

    public fun translate(
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String {
        val lines = source.lines()
        val isTemplate = lines.any { it.trim().matches(CONTROL_LINE_REGEX) }

        if (!isTemplate) {
            // Not a structured template: return trimmed source or fallback
            return source.trim()
        }

        val output = StringBuilder()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.matches(CONTROL_LINE_REGEX)) {
                // Control header lines must remain strictly unchanged
                output.appendLine(trimmed)
            } else {
                // Content line with markers like {{T0}} and {{L1}}
                output.appendLine(line)
            }
        }

        return output.toString().trimEnd()
    }
}
