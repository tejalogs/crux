package org.crux.ui.haptic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

/**
 * Semantic haptic output.
 *
 * Implementations translate [CruxHapticEvent] into whatever the host platform supports and
 * silently ignore events the platform cannot express.
 */
@Immutable
public interface CruxHapticFeedback {
    public fun perform(event: CruxHapticEvent)
}

/**
 * Returns the haptic output for the current platform, gated by [enabled].
 *
 * When [enabled] is false every call is a no-op, which lets call sites stay unconditional and
 * lets the preference be flipped without recomposing every action button.
 */
@Composable
public fun rememberFlareHapticFeedback(enabled: Boolean): CruxHapticFeedback = rememberFlareHapticFeedbackInternal(enabled)

@Composable
internal expect fun rememberFlareHapticFeedbackInternal(enabled: Boolean): CruxHapticFeedback

internal fun CruxHapticFeedback.performIfEnabled(
    enabled: Boolean,
    event: CruxHapticEvent,
) {
    if (enabled) {
        perform(event)
    }
}
