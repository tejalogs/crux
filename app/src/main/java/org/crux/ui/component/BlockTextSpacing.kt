package org.crux.ui.component

/**
 * Decides which whitespace survives when the text nodes of one block (a paragraph, list item,
 * heading or quote) are joined. HTML source indents and wraps its markup, so text nodes carry
 * leading and trailing whitespace that must not show as an indent or a trailing gap, while the
 * single space between a word and the link or emphasis after it must stay.
 *
 * Feed each piece of content in order; use [reset] when the block is flushed.
 */
internal class BlockTextSpacing {
    private var hasContent = false
    private var pendingSpace = false
    private var atBoundary = true

    /** What to append for a text node's [raw] text: trimmed, with one space in front only when it separates two pieces. */
    fun text(raw: String): String {
        if (raw.isEmpty()) return ""
        val core = raw.trim()
        if (core.isEmpty()) {
            if (hasContent && !atBoundary) pendingSpace = true
            return ""
        }
        val separator = if (hasContent && !atBoundary && (pendingSpace || raw.first().isWhitespace())) " " else ""
        pendingSpace = raw.last().isWhitespace()
        hasContent = true
        atBoundary = false
        return separator + core
    }

    /** The space owed before inline content that is not a text node (code, an image's alt text), or empty. */
    fun beforeInline(): String {
        val owed = hasContent && pendingSpace && !atBoundary
        pendingSpace = false
        if (owed) atBoundary = true
        return if (owed) " " else ""
    }

    /** Records inline content that was appended without going through [text]. */
    fun inlineAppended() {
        hasContent = true
        atBoundary = false
        pendingSpace = false
    }

    /** A line break starts a fresh line: whitespace right after it is dropped. */
    fun lineBreak() {
        hasContent = true
        atBoundary = true
        pendingSpace = false
    }

    fun reset() {
        hasContent = false
        pendingSpace = false
        atBoundary = true
    }
}
