package dev.dimension.flare.ui.haptic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

/**
 * Semantic haptic output.
 *
 * Implementations translate [FlareHapticEvent] into whatever the host platform supports and
 * silently ignore events the platform cannot express.
 */
@Immutable
public interface FlareHapticFeedback {
    public fun perform(event: FlareHapticEvent)
}

/**
 * Returns the haptic output for the current platform, gated by [enabled].
 *
 * When [enabled] is false every call is a no-op, which lets call sites stay unconditional and
 * lets the preference be flipped without recomposing every action button.
 */
@Composable
public fun rememberFlareHapticFeedback(enabled: Boolean): FlareHapticFeedback =
    rememberFlareHapticFeedbackInternal(enabled)

@Composable
internal expect fun rememberFlareHapticFeedbackInternal(enabled: Boolean): FlareHapticFeedback

internal fun FlareHapticFeedback.performIfEnabled(
    enabled: Boolean,
    event: FlareHapticEvent,
) {
    if (enabled) {
        perform(event)
    }
}
