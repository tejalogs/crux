package dev.dimension.flare.ui.component.status

import androidx.compose.runtime.Composable

/**
 * Returns true if the system or platform has requested reduced motion / disabled animations.
 */
@Composable
public expect fun isReducedMotion(): Boolean
