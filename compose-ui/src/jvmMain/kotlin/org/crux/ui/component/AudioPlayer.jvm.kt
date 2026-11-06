package org.crux.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.net.URI
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.LineEvent
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal actual fun rememberPlayerState(uri: String): PlayerState {
    var playing by remember(uri) { mutableStateOf(false) }
    var loading by remember(uri) { mutableStateOf(true) }
    var durationMicros by remember(uri) { mutableLongStateOf(0L) }
    var currentMicros by remember(uri) { mutableLongStateOf(0L) }
    var clipRef by remember(uri) { mutableStateOf<Clip?>(null) }

    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            loading = true
            try {
                val url = URI.create(uri).toURL()
                val audioIn = AudioSystem.getAudioInputStream(BufferedInputStream(url.openStream()))
                val clip = AudioSystem.getClip()
                clip.open(audioIn)
                durationMicros = clip.microsecondLength
                clip.addLineListener { event ->
                    if (event.type == LineEvent.Type.STOP) {
                        if (clip.microsecondPosition >= clip.microsecondLength) {
                            clip.microsecondPosition = 0
                            playing = false
                        }
                    }
                }
                clipRef = clip
            } catch (_: Exception) {
                // If stream decoding fails on JVM, keep graceful fallback
            } finally {
                loading = false
            }
        }
    }

    if (playing) {
        LaunchedEffect(clipRef) {
            val clip = clipRef ?: return@LaunchedEffect
            while (isActive && clip.isRunning) {
                currentMicros = clip.microsecondPosition
                delay(50.milliseconds)
            }
            playing = clip.isRunning
        }
    }

    DisposableEffect(uri) {
        onDispose {
            clipRef?.let {
                runCatching {
                    if (it.isRunning) it.stop()
                    it.close()
                }
            }
        }
    }

    return remember(uri, clipRef, playing, loading, durationMicros, currentMicros) {
        object : PlayerState {
            override val playing: Boolean
                get() = playing
            override val loading: Boolean
                get() = loading
            override val duration: Long
                get() = durationMicros / 1000L
            override val progress: Float
                get() = if (durationMicros > 0) (currentMicros.toFloat() / durationMicros.toFloat()).coerceIn(0f, 1f) else 0f

            override fun play() {
                val clip = clipRef ?: return
                if (!clip.isRunning) {
                    clip.start()
                    playing = true
                }
            }

            override fun pause() {
                val clip = clipRef ?: return
                if (clip.isRunning) {
                    clip.stop()
                    playing = false
                }
            }

            override fun seekTo(position: Long) {
                val clip = clipRef ?: return
                val targetMicros = (position * 1000L).coerceIn(0L, durationMicros)
                clip.microsecondPosition = targetMicros
                currentMicros = targetMicros
            }
        }
    }
}
