package org.crux.ui.haptic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

public enum class CruxHapticEvent {
    Tick,
    SelectionChange,
    GestureThreshold,
    LongPress,
    Confirm,
    Reject,
    Warning,
    Error,
}

@Immutable
public interface CruxHapticFeedback {
    public fun perform(event: CruxHapticEvent)
}

@Composable
public fun rememberCruxHapticFeedback(enabled: Boolean): CruxHapticFeedback {
    val delegate = LocalHapticFeedback.current
    return remember(enabled, delegate) {
        if (enabled) AndroidCruxHapticFeedback(delegate) else NoOpCruxHapticFeedback
    }
}

@Composable
public fun rememberFlareHapticFeedback(enabled: Boolean): CruxHapticFeedback = rememberCruxHapticFeedback(enabled)

private object NoOpCruxHapticFeedback : CruxHapticFeedback {
    override fun perform(event: CruxHapticEvent) = Unit
}

@Immutable
private class AndroidCruxHapticFeedback(
    private val delegate: HapticFeedback,
) : CruxHapticFeedback {
    override fun perform(event: CruxHapticEvent) {
        val type =
            when (event) {
                CruxHapticEvent.Tick -> HapticFeedbackType.SegmentTick
                CruxHapticEvent.SelectionChange -> HapticFeedbackType.SegmentFrequentTick
                CruxHapticEvent.GestureThreshold -> HapticFeedbackType.GestureThresholdActivate
                CruxHapticEvent.LongPress -> HapticFeedbackType.LongPress
                CruxHapticEvent.Confirm -> HapticFeedbackType.Confirm
                CruxHapticEvent.Reject -> HapticFeedbackType.Reject
                CruxHapticEvent.Warning -> HapticFeedbackType.ContextClick
                CruxHapticEvent.Error -> HapticFeedbackType.Reject
            }
        delegate.performHapticFeedback(type)
    }
}
