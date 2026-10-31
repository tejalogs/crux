package dev.dimension.flare.ui.component.status

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

private class AndroidAudioBriefingState(
    private val context: Context,
) : AudioBriefingState {
    override var isSpeaking: Boolean by mutableStateOf(false)
        private set
    override var isPaused: Boolean by mutableStateOf(false)
        private set
    override var speechRate: Float by mutableStateOf(1.0f)
        private set
    override val isSupported: Boolean = true

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingSpeechText: String? = null
    private var lastSpokenText: String = ""

    init {
        tts =
            TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.getDefault()
                    tts?.setSpeechRate(speechRate)
                    isInitialized = true
                    tts?.setOnUtteranceProgressListener(
                        object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {
                                isSpeaking = true
                                isPaused = false
                            }

                            override fun onDone(utteranceId: String?) {
                                isSpeaking = false
                                isPaused = false
                            }

                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) {
                                isSpeaking = false
                                isPaused = false
                            }
                        },
                    )
                    val pending = pendingSpeechText
                    if (!pending.isNullOrBlank()) {
                        pendingSpeechText = null
                        speak(pending)
                    }
                }
            }
    }

    override fun speak(text: String) {
        lastSpokenText = text
        if (!isInitialized) {
            pendingSpeechText = text
            return
        }
        tts?.stop()
        tts?.setSpeechRate(speechRate)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "catch_me_up_briefing")
        isSpeaking = true
        isPaused = false
    }

    override fun updateSpeechRate(rate: Float) {
        speechRate = rate
        tts?.setSpeechRate(rate)
    }

    override fun pause() {
        if (isSpeaking) {
            tts?.stop()
            isSpeaking = false
            isPaused = true
        }
    }

    override fun resume() {
        if (isPaused && lastSpokenText.isNotBlank()) {
            speak(lastSpokenText)
        }
    }

    override fun stop() {
        tts?.stop()
        isSpeaking = false
        isPaused = false
        pendingSpeechText = null
        lastSpokenText = ""
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isSpeaking = false
        isPaused = false
    }
}

@Composable
public actual fun rememberAudioBriefingState(): AudioBriefingState {
    val context = LocalContext.current
    val state = remember(context) { AndroidAudioBriefingState(context) }
    DisposableEffect(state) {
        onDispose {
            state.release()
        }
    }
    return state
}
