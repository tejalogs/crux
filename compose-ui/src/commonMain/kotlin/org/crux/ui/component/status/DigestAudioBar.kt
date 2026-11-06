package org.crux.ui.component.status

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CirclePause
import compose.icons.fontawesomeicons.solid.CirclePlay
import org.crux.compose.ui.Res
import org.crux.compose.ui.catch_up_audio_paused
import org.crux.compose.ui.catch_up_audio_playing
import org.crux.compose.ui.catch_up_audio_stopped
import org.crux.compose.ui.catch_up_audio_title
import org.crux.compose.ui.catch_up_audio_unavailable
import org.crux.compose.ui.catch_up_listen_description
import org.crux.compose.ui.catch_up_pause_description
import org.crux.data.ai.FeedDigest
import org.crux.ui.component.FAIcon
import org.crux.ui.component.platform.PlatformDropdownMenu
import org.crux.ui.component.platform.PlatformDropdownMenuItem
import org.crux.ui.component.platform.PlatformIconButton
import org.crux.ui.component.platform.PlatformText
import org.crux.ui.component.platform.PlatformTextButton
import org.crux.ui.theme.PlatformTheme
import org.jetbrains.compose.resources.stringResource

/**
 * Builds clean, unexaggerated spoken audio script for the catch-up.
 */
public fun buildDigestAudioScript(digest: FeedDigest): String =
    buildString {
        append("Here is your catch-up for ${digest.postCount} posts. ")
        digest.highlights.forEachIndexed { index, highlight ->
            append("Topic ${index + 1}: ${highlight.topic}. ")
            append("${highlight.summary} ")
            if (!highlight.authorName.isNullOrBlank()) {
                append("From ${highlight.authorName}. ")
            }
        }
        append("That concludes your catch-up.")
    }

/**
 * 5-bar audio waveform indicator that pulses ONLY when audio is actively speaking
 * and reduced motion is not requested.
 */
@Composable
public fun DigestAudioWaveform(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    color: Color = PlatformTheme.colorScheme.primary,
) {
    val reducedMotion = isReducedMotion()
    val shouldAnimate = isPlaying && !reducedMotion

    val transition = rememberInfiniteTransition(label = "digest_waveform")
    val h1 by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
        label = "h1",
    )
    val h2 by transition.animateFloat(
        initialValue = 0.75f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(360, easing = LinearEasing), RepeatMode.Reverse),
        label = "h2",
    )
    val h3 by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(480, easing = LinearEasing), RepeatMode.Reverse),
        label = "h3",
    )
    val h4 by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(390, easing = LinearEasing), RepeatMode.Reverse),
        label = "h4",
    )
    val h5 by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(450, easing = LinearEasing), RepeatMode.Reverse),
        label = "h5",
    )

    Row(
        modifier = modifier.height(18.dp),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val animated = listOf(h1, h2, h3, h4, h5)
        val idle = listOf(4f, 8f, 14f, 8f, 4f)
        for (i in 0 until 5) {
            val barHeight = if (shouldAnimate) (18 * animated[i]).coerceIn(3f, 18f).dp else idle[i].dp
            Box(
                modifier =
                    Modifier
                        .width(2.8.dp)
                        .height(barHeight)
                        .clip(RoundedCornerShape(50))
                        .background(if (isPlaying) color else color.copy(alpha = 0.35f)),
            )
        }
    }
}

/**
 * Slim docked audio player controls used in the catch-up bottom sheet.
 */
@Composable
public fun DigestAudioPlayerBar(
    audioState: AudioBriefingState,
    audioScript: String,
    modifier: Modifier = Modifier,
) {
    if (!audioState.isSupported) {
        Box(
            modifier = modifier.fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            PlatformText(
                text = stringResource(Res.string.catch_up_audio_unavailable),
                style = PlatformTheme.typography.caption,
                color = PlatformTheme.colorScheme.caption,
            )
        }
        return
    }

    var showSpeedMenu by remember { mutableStateOf(false) }
    val rates = listOf(1.0f, 1.25f, 1.5f, 2.0f)

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(PlatformTheme.colorScheme.cardAlt.copy(alpha = 0.7f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PlatformIconButton(
            onClick = {
                when {
                    audioState.isSpeaking -> audioState.pause()
                    audioState.isPaused -> audioState.resume()
                    else -> audioState.speak(audioScript)
                }
            },
            modifier = Modifier.size(48.dp),
        ) {
            FAIcon(
                imageVector =
                    if (audioState.isSpeaking) {
                        FontAwesomeIcons.Solid.CirclePause
                    } else {
                        FontAwesomeIcons.Solid.CirclePlay
                    },
                contentDescription =
                    stringResource(
                        if (audioState.isSpeaking) {
                            Res.string.catch_up_pause_description
                        } else {
                            Res.string.catch_up_listen_description
                        },
                    ),
                modifier = Modifier.size(28.dp),
                tint = PlatformTheme.colorScheme.primary,
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            PlatformText(
                text = stringResource(Res.string.catch_up_audio_title),
                style = PlatformTheme.typography.body,
                fontWeight = FontWeight.SemiBold,
                color = PlatformTheme.colorScheme.text,
            )
            PlatformText(
                text =
                    stringResource(
                        when {
                            audioState.isSpeaking -> Res.string.catch_up_audio_playing
                            audioState.isPaused -> Res.string.catch_up_audio_paused
                            else -> Res.string.catch_up_audio_stopped
                        },
                    ),
                style = PlatformTheme.typography.caption,
                color = PlatformTheme.colorScheme.caption,
            )
        }

        DigestAudioWaveform(
            isPlaying = audioState.isSpeaking,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        Box {
            PlatformTextButton(
                onClick = { showSpeedMenu = true },
                modifier = Modifier.height(48.dp),
            ) {
                PlatformText(
                    text = "${audioState.speechRate}x",
                    fontWeight = FontWeight.Medium,
                    color = PlatformTheme.colorScheme.primary,
                )
            }
            PlatformDropdownMenu(
                expanded = showSpeedMenu,
                onDismissRequest = { showSpeedMenu = false },
            ) {
                rates.forEach { rate ->
                    PlatformDropdownMenuItem(
                        text = {
                            PlatformText(
                                text = "${rate}x",
                                fontWeight = if (audioState.speechRate == rate) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        onClick = {
                            audioState.updateSpeechRate(rate)
                            showSpeedMenu = false
                        },
                    )
                }
            }
        }
    }
}
