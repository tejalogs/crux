package dev.dimension.flare.ui.haptic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
internal actual fun rememberFlareHapticFeedbackInternal(enabled: Boolean): FlareHapticFeedback =
    remember(enabled) {
        // Desktop hosts expose no haptic hardware through Compose; keep call sites uniform.
        NoOpFlareHapticFeedback
    }
