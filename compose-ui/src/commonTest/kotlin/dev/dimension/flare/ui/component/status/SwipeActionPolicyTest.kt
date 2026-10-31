package dev.dimension.flare.ui.component.status

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SwipeActionPolicyTest {
    @Test
    fun shortDragDoesNotTrigger() {
        val result =
            resolveSwipeAction(
                offsetPx = 40f,
                thresholdPx = 200f,
                velocityPxPerSecond = 10f,
                flingVelocityThresholdPxPerSecond = 1200f,
                maxFlingDistancePx = 200f,
            )
        assertFalse(result.triggered)
        assertEquals(SwipeActionDirection.End, result.direction)
        assertEquals(0.2f, result.progress, 0.001f)
    }

    @Test
    fun dragPastThresholdTriggers() {
        val result =
            resolveSwipeAction(
                offsetPx = 240f,
                thresholdPx = 200f,
                velocityPxPerSecond = 10f,
                flingVelocityThresholdPxPerSecond = 1200f,
                maxFlingDistancePx = 200f,
            )
        assertTrue(result.triggered)
        assertEquals(1f, result.progress, 0.001f)
    }

    @Test
    fun negativeOffsetReportsStartDirection() {
        val result =
            resolveSwipeAction(
                offsetPx = -240f,
                thresholdPx = 200f,
                velocityPxPerSecond = 10f,
                flingVelocityThresholdPxPerSecond = 1200f,
                maxFlingDistancePx = 200f,
            )
        assertTrue(result.triggered)
        assertEquals(SwipeActionDirection.Start, result.direction)
    }

    @Test
    fun slowFlingBelowMinimumDistanceDoesNotTrigger() {
        val result =
            resolveSwipeAction(
                offsetPx = 10f,
                thresholdPx = 200f,
                velocityPxPerSecond = 3000f,
                flingVelocityThresholdPxPerSecond = 1200f,
                maxFlingDistancePx = 200f,
            )
        assertFalse(result.triggered)
    }

    @Test
    fun fastFlingCommitsFromPartialDistance() {
        val result =
            resolveSwipeAction(
                offsetPx = 80f,
                thresholdPx = 200f,
                velocityPxPerSecond = 3000f,
                flingVelocityThresholdPxPerSecond = 1200f,
                maxFlingDistancePx = 200f,
            )
        assertTrue(result.triggered)
    }

    @Test
    fun flingIsCappedByDistance() {
        // A huge offset cannot inflate progress past 1f.
        val result =
            resolveSwipeAction(
                offsetPx = 5_000f,
                thresholdPx = 200f,
                velocityPxPerSecond = 10f,
                flingVelocityThresholdPxPerSecond = 1200f,
                maxFlingDistancePx = 200f,
            )
        assertTrue(result.triggered)
        assertEquals(1f, result.progress, 0.001f)
    }

    @Test
    fun zeroThresholdNeverTriggers() {
        val result =
            resolveSwipeAction(
                offsetPx = 500f,
                thresholdPx = 0f,
                velocityPxPerSecond = 5000f,
                flingVelocityThresholdPxPerSecond = 1200f,
                maxFlingDistancePx = 200f,
            )
        assertFalse(result.triggered)
        assertEquals(0f, result.progress, 0.001f)
    }

    @Test
    fun settleHomesOnCancel() {
        assertEquals(0f, settleOffset(triggered = false, offsetPx = 150f, thresholdPx = 200f), 0.001f)
        assertEquals(0f, settleOffset(triggered = false, offsetPx = -150f, thresholdPx = 200f), 0.001f)
    }

    @Test
    fun settleSnapsToEdgeOnCommit() {
        assertEquals(200f, settleOffset(triggered = true, offsetPx = 260f, thresholdPx = 200f), 0.001f)
        assertEquals(-200f, settleOffset(triggered = true, offsetPx = -260f, thresholdPx = 200f), 0.001f)
    }

    @Test
    fun alphaFadesInAcrossFirstHalf() {
        assertEquals(0f, swipeActionAlpha(0f), 0.001f)
        assertEquals(1f, swipeActionAlpha(0.5f), 0.001f)
        assertEquals(1f, swipeActionAlpha(1f), 0.001f)
    }

    @Test
    fun scaleGrowsWithProgress() {
        assertTrue(swipeActionScale(1f) > swipeActionScale(0f))
        assertEquals(1f, swipeActionScale(1f), 0.001f)
    }

    @Test
    fun thresholdIsAFractionOfWidth() {
        assertEquals(300, swipeThresholdPx(widthPx = 1000f, fraction = 0.3f))
        assertTrue(swipeThresholdPx(widthPx = 10f, fraction = 0.3f) >= 1)
    }
}
