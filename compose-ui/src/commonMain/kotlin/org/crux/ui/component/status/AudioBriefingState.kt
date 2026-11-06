package org.crux.ui.component.status

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
    public val speechRate: Float get() = 1.0f

    public fun speak(text: String)

    public fun pause()

    public fun resume()

    public fun stop()

    public fun updateSpeechRate(rate: Float) {}
}

/**
 * Creates and remembers a platform-specific [AudioBriefingState].
 */
@Composable
public expect fun rememberAudioBriefingState(): AudioBriefingState
