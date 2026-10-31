package dev.dimension.flare.ui.component.status

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Direction of a horizontal swipe-to-act gesture.
 */
public enum class SwipeActionDirection {
    Start,
    End,
}

/**
 * Pure description of how a swipe resolves, independent of Compose animation state.
 *
 * Keeping this separate from the gesture detector means the policy is unit-testable and the
 * animation layer only has to react to a decision.
 */
@Immutable
public data class SwipeActionResolution(
    val triggered: Boolean,
    /** Fraction of the commit threshold that was reached, in `0f..1f`. */
    val progress: Float,
    /** Direction the user was heading when the gesture resolved. */
    val direction: SwipeActionDirection,
)

/**
 * Decides whether a horizontal drag should commit its action or spring back.
 *
 * @param offsetPx current drag offset in pixels
 * @param thresholdPx distance at which the action commits
 * @param velocityPxPerSecond fling velocity at release
 * @param flingVelocityThresholdPxPerSecond speed at which a fling commits regardless of distance
 * @param maxFlingDistancePx hard cap, so a violent fling cannot skip the whole threshold
 */
internal fun resolveSwipeAction(
    offsetPx: Float,
    thresholdPx: Float,
    velocityPxPerSecond: Float,
    flingVelocityThresholdPxPerSecond: Float,
    maxFlingDistancePx: Float,
): SwipeActionResolution {
    if (thresholdPx <= 0f) {
        return SwipeActionResolution(triggered = false, progress = 0f, direction = SwipeActionDirection.End)
    }
    val direction =
        if (offsetPx < 0f) {
            SwipeActionDirection.Start
        } else {
            SwipeActionDirection.End
        }
    val magnitude = abs(offsetPx)
    val capped = minOf(magnitude, maxFlingDistancePx)
    val byDistance = magnitude >= thresholdPx
    val byFling = abs(velocityPxPerSecond) >= flingVelocityThresholdPxPerSecond && capped >= thresholdPx * 0.25f
    return SwipeActionResolution(
        triggered = byDistance || byFling,
        progress = (capped / thresholdPx).coerceIn(0f, 1f),
        direction = direction,
    )
}

/**
 * Settle offset for a resolved gesture.
 *
 * A committed gesture animates fully to the action edge; a cancelled one springs home.
 */
internal fun settleOffset(
    triggered: Boolean,
    offsetPx: Float,
    thresholdPx: Float,
): Float =
    if (triggered) {
        if (offsetPx < 0f) -thresholdPx else thresholdPx
    } else {
        0f
    }

/** Converts a drag fraction into icon alpha, so the affordance fades in as the gesture arms. */
internal fun swipeActionAlpha(progress: Float): Float = (progress * 2f).coerceIn(0f, 1f)

/** Converts a drag fraction into an icon scale, giving the reveal a springy overshoot. */
internal fun swipeActionScale(progress: Float): Float = 0.6f + 0.4f * progress.coerceIn(0f, 1f)

/** Default commit threshold as a fraction of the item width. */
internal val DefaultSwipeThresholdFraction: Float = 0.3f

/** Speed at which a fling commits the gesture regardless of distance travelled. */
@Suppress("ktlint:standard:property-naming")
internal const val FlingVelocityThreshold: Float = 1_200f

/** Upper bound on fling distance as a fraction of width, so a violent flick cannot skip the row. */
@Suppress("ktlint:standard:property-naming")
internal const val MaxFlingDistanceFraction: Float = 0.5f

/** Converts a width and fraction into a commit threshold in pixels. */
internal fun swipeThresholdPx(
    widthPx: Float,
    fraction: Float = DefaultSwipeThresholdFraction,
): Int = (widthPx * fraction).roundToInt().coerceAtLeast(1)

/** Convenience wrapper expressing the default threshold in Dp-independent proportions. */
public fun defaultSwipeThresholdFor(
    width: Dp,
    density: Float,
): Float = width.value * density * DefaultSwipeThresholdFraction

/** Reads a live animated offset as a commit progress fraction. */
internal fun Float.progressFor(thresholdPx: Float): Float =
    if (thresholdPx <= 0f) 0f else (kotlin.math.abs(this) / thresholdPx).coerceIn(0f, 1f)
