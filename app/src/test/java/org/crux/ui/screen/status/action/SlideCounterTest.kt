package org.crux.ui.screen.status.action

import org.junit.Assert.assertEquals
import org.junit.Test

class SlideCounterTest {
    @Test
    fun padsSingleDigitsToTwo() {
        assertEquals("01/04", slideCounter(1, 4))
        assertEquals("09", slideNumber(9))
    }

    @Test
    fun doesNotPrefixTwoDigitNumbersWithAnExtraZero() {
        // The old "0${i}/0${total}" format produced "010/012" here.
        assertEquals("10/12", slideCounter(10, 12))
        assertEquals("12", slideNumber(12))
    }

    @Test
    fun keepsThreeDigitNumbersIntact() {
        assertEquals("100/120", slideCounter(100, 120))
    }
}
