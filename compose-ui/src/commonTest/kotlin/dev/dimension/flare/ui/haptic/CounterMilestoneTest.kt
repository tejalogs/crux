package dev.dimension.flare.ui.haptic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CounterMilestoneTest {
    @Test
    fun belowFirstThresholdIsSilent() {
        assertNull(counterMilestoneEvent(previous = 0, current = 9, enabled = true))
        assertNull(counterMilestoneEvent(previous = 1, current = 5, enabled = true))
    }

    @Test
    fun crossingFirstThresholdTicks() {
        assertEquals(
            FlareHapticEvent.Tick,
            counterMilestoneEvent(previous = 9, current = 10, enabled = true),
        )
    }

    @Test
    fun withinSameRungIsSilent() {
        assertNull(counterMilestoneEvent(previous = 10, current = 42, enabled = true))
        assertNull(counterMilestoneEvent(previous = 101, current = 999, enabled = true))
    }

    @Test
    fun eachRungTicksOnce() {
        assertEquals(
            FlareHapticEvent.Tick,
            counterMilestoneEvent(previous = 99, current = 100, enabled = true),
        )
        assertEquals(
            FlareHapticEvent.Tick,
            counterMilestoneEvent(previous = 999, current = 1_000, enabled = true),
        )
        assertEquals(
            FlareHapticEvent.Tick,
            counterMilestoneEvent(previous = 9_999, current = 10_000, enabled = true),
        )
        assertEquals(
            FlareHapticEvent.Tick,
            counterMilestoneEvent(previous = 99_999, current = 100_000, enabled = true),
        )
    }

    @Test
    fun multiRungJumpConfirms() {
        assertEquals(
            FlareHapticEvent.Confirm,
            counterMilestoneEvent(previous = 5, current = 1_000, enabled = true),
        )
    }

    @Test
    fun singleRungJumpFromZeroTicks() {
        assertEquals(
            FlareHapticEvent.Tick,
            counterMilestoneEvent(previous = 0, current = 10, enabled = true),
        )
    }

    @Test
    fun decrementsAreSilent() {
        assertNull(counterMilestoneEvent(previous = 1_000, current = 999, enabled = true))
        assertNull(counterMilestoneEvent(previous = 10, current = 0, enabled = true))
    }

    @Test
    fun disabledNeverEmits() {
        assertNull(counterMilestoneEvent(previous = 9, current = 10, enabled = false))
        assertNull(counterMilestoneEvent(previous = 0, current = 1_000_000, enabled = false))
    }

    @Test
    fun equalValuesAreSilent() {
        assertNull(counterMilestoneEvent(previous = 10, current = 10, enabled = true))
    }
}
