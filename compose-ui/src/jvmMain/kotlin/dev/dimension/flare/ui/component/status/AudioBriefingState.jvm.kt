package dev.dimension.flare.ui.component.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

private object JvmAudioBriefingState : AudioBriefingState {
    override val isSpeaking: Boolean = false
    override val isPaused: Boolean = false
    override val isSupported: Boolean = false

    override fun speak(text: String) {}

    override fun pause() {}

    override fun resume() {}

    override fun stop() {}
}

@Composable
public actual fun rememberAudioBriefingState(): AudioBriefingState = remember { JvmAudioBriefingState }
