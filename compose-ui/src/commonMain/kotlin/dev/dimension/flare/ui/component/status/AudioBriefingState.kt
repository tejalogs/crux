package dev.dimension.flare.ui.component.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

/**
 * Controller for hands-free on-device audio briefing (Text-To-Speech).
 * Runs completely locally with zero network egress.
 */
@Immutable
public interface AudioBriefingState {
    public val isSpeaking: Boolean
    public val isPaused: Boolean
    public val isSupported: Boolean

    public fun speak(text: String)
    public fun pause()
    public fun resume()
    public fun stop()
}

/**
 * Creates and remembers a platform-specific [AudioBriefingState].
 */
@Composable
public expect fun rememberAudioBriefingState(): AudioBriefingState
