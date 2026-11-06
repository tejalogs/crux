package org.crux.ui.haptic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

@Composable
internal actual fun rememberFlareHapticFeedbackInternal(enabled: Boolean): CruxHapticFeedback {
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
