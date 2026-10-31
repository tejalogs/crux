package dev.dimension.flare.ui.haptic

/**
 * Threshold ladder for counter feedback.
 *
 * Counts climb unevenly: the jump from 9 to 10 reads as a milestone, the jump from 990 to 1_000
 * reads as a much larger one. Laddering the thresholds keeps the number of ticks roughly
 * logarithmic in the visible range so a counter animating from 1 to 5_000 never buzzes.
 */
public enum class CounterMilestone(
    internal val threshold: Long,
) {
    Ten(10L),
    Hundred(100L),
    Thousand(1_000L),
    TenThousand(10_000L),
    HundredThousand(100_000L),
    Million(1_000_000L),
    ;

    internal companion object {
        private val ascending = entries.sortedBy { it.threshold }

        /**
         * Highest ladder entry strictly below [value], or null below the first threshold.
         */
        internal fun atOrBelow(value: Long): CounterMilestone? = ascending.lastOrNull { it.threshold <= value }
    }
}

/**
 * Decides whether a counter transition should produce haptic feedback.
 *
 * @param previous the value the counter had before the change
 * @param current the value the counter has after the change
 * @param enabled master switch for counter feedback
 * @return the event to emit, or null when the transition should stay silent
 */
internal fun counterMilestoneEvent(
    previous: Long,
    current: Long,
    enabled: Boolean,
): FlareHapticEvent? {
    if (!enabled) return null
    if (current <= previous) return null
    val previousLadder = CounterMilestone.atOrBelow(previous)
    val currentLadder = CounterMilestone.atOrBelow(current) ?: return null
    if (currentLadder == previousLadder) return null
    val crossed = currentLadder.threshold / (previousLadder?.threshold ?: 1L)
    // Several rungs crossed at once (a big jump); a stronger cue reads as progress.
    return if (crossed >= 100L) FlareHapticEvent.Confirm else FlareHapticEvent.Tick
}
