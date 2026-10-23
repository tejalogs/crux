package dev.dimension.flare.ui.haptic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

@Composable
internal actual fun rememberFlareHapticFeedbackInternal(enabled: Boolean): FlareHapticFeedback {
    val delegate = LocalHapticFeedback.current
    return remember(enabled, delegate) {
        if (enabled) {
            AndroidFlareHapticFeedback(delegate)
        } else {
            NoOpFlareHapticFeedback
        }
    }
}

@Immutable
private class AndroidFlareHapticFeedback(
    private val delegate: androidx.compose.ui.hapticfeedback.HapticFeedback,
) : FlareHapticFeedback {
    override fun perform(event: FlareHapticEvent) {
        val type =
            when (event) {
                FlareHapticEvent.Tick -> HapticFeedbackType.SegmentTick
                FlareHapticEvent.SelectionChange -> HapticFeedbackType.SegmentFrequentTick
                FlareHapticEvent.GestureThreshold -> HapticFeedbackType.GestureThresholdActivate
                FlareHapticEvent.LongPress -> HapticFeedbackType.LongPress
                FlareHapticEvent.Confirm -> HapticFeedbackType.Confirm
                FlareHapticEvent.Reject -> HapticFeedbackType.Reject
                FlareHapticEvent.Warning -> HapticFeedbackType.ContextClick
                FlareHapticEvent.Error -> HapticFeedbackType.Reject
            }
        delegate.performHapticFeedback(type)
    }
}
