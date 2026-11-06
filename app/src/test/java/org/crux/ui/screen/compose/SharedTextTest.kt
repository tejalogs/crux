package org.crux.ui.screen.compose

import org.junit.Assert.assertEquals
import org.junit.Test

class SharedTextTest {
    @Test
    fun bodyAloneIsKept() {
        assertEquals("hello", sharedTextWithTitle(text = "  hello ", title = null))
    }

    @Test
    fun titleAndBodyAreJoinedOnTwoLines() {
        assertEquals("Title\nbody", sharedTextWithTitle(text = "body", title = "Title"))
    }

    @Test
    fun duplicateTitleIsNotRepeated() {
        assertEquals("same", sharedTextWithTitle(text = "same", title = "same"))
        assertEquals("Title\nrest", sharedTextWithTitle(text = "Title\nrest", title = "Title"))
    }

    @Test
    fun titleAloneIsUsedWhenThereIsNoBody() {
        assertEquals("Title", sharedTextWithTitle(text = null, title = "Title"))
        assertEquals("", sharedTextWithTitle(text = null, title = null))
    }
}
