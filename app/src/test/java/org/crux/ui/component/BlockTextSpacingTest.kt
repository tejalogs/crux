package org.crux.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

class BlockTextSpacingTest {
    private fun join(vararg nodes: String): String {
        val spacing = BlockTextSpacing()
        return nodes.joinToString("") { spacing.text(it) }
    }

    @Test
    fun leadingAndTrailingWhitespaceOfABlockIsDropped() {
        assertEquals("Hello world", join("\n    Hello world   \n"))
    }

    @Test
    fun spaceBeforeALinkSurvives() {
        assertEquals("Read the docs now", join("Read the ", "docs", " now"))
    }

    @Test
    fun spaceAfterALinkSurvivesEvenWhenTheNextNodeStartsWithOne() {
        assertEquals("a b", join("a", " b"))
    }

    @Test
    fun whitespaceOnlyNodeBetweenInlineElementsBecomesOneSpace() {
        assertEquals("one two", join("one", "  \n ", "two"))
    }

    @Test
    fun whitespaceOnlyNodeAtTheStartAddsNothing() {
        assertEquals("text", join("   ", "text"))
    }

    @Test
    fun noSpaceIsInventedBetweenAdjacentWords() {
        assertEquals("foobar", join("foo", "bar"))
    }

    @Test
    fun whitespaceRightAfterALineBreakIsDropped() {
        val spacing = BlockTextSpacing()
        val text = spacing.text("line one") + "\n"
        spacing.lineBreak()
        assertEquals("line one\nline two", text + spacing.text("   line two"))
    }

    @Test
    fun inlineCodeKeepsTheSpaceOwedBeforeIt() {
        val spacing = BlockTextSpacing()
        val first = spacing.text("use ")
        val owed = spacing.beforeInline()
        spacing.inlineAppended()
        assertEquals("use", first)
        assertEquals(" ", owed)
        assertEquals(" now", spacing.text(" now"))
    }

    @Test
    fun resetStartsTheNextBlockClean() {
        val spacing = BlockTextSpacing()
        spacing.text("end ")
        spacing.reset()
        assertEquals("next", spacing.text("  next"))
    }
}
