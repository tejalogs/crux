package org.crux.ui.haptic

import androidx.compose.runtime.Immutable

/**
 * Fallback used by hosts with no haptic hardware, and by every host when the preference is off.
 *
 * Shared across platforms so the disabled path always allocates the same instance.
 */
@Immutable
internal object NoOpCruxHapticFeedback : CruxHapticFeedback {
    override fun perform(event: CruxHapticEvent): Unit = Unit
}

internal typealias NoOpFlareHapticFeedback = NoOpCruxHapticFeedback
