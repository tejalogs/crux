package dev.dimension.flare.ui.component.status

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.AngleDown
import compose.icons.fontawesomeicons.solid.AngleRight
import compose.icons.fontawesomeicons.solid.AngleUp
import compose.icons.fontawesomeicons.solid.CirclePause
import compose.icons.fontawesomeicons.solid.CirclePlay
import compose.icons.fontawesomeicons.solid.RotateRight
import compose.icons.fontawesomeicons.solid.WandMagicSparkles
import compose.icons.fontawesomeicons.solid.Xmark
import dev.dimension.flare.data.ai.FeedDigest
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.HorizontalDivider
import dev.dimension.flare.ui.component.platform.PlatformIconButton
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.component.platform.PlatformTextButton
import dev.dimension.flare.ui.model.ClickContext
import dev.dimension.flare.ui.model.toUiImage
import dev.dimension.flare.ui.theme.PlatformTheme
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import java.util.Calendar

/**
 * Animated 4-bar equalizer that pulses rhythmically during on-device Audio Briefing.
 */
@Composable
private fun AudioWaveAnimation(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    color: Color = PlatformTheme.colorScheme.primary,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "audio_wave_transition")

    val h1 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "h1",
    )
    val h2 by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(380, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "h2",
    )
    val h3 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(520, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "h3",
    )
    val h4 by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(410, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "h4",
    )

    Row(
        modifier = modifier.height(20.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val heights = listOf(h1, h2, h3, h4)
        heights.forEach { factor ->
            val actualHeight = if (isPlaying) (20 * factor).coerceIn(4f, 20f).dp else 4.dp
            Box(
                modifier = Modifier
                    .width(3.2.dp)
                    .height(actualHeight)
                    .clip(RoundedCornerShape(50))
                    .background(color),
            )
        }
    }
}

/**
 * Refined Material 3 Expressive On-Device Feed Digest Card ("Catch Me Up" / Executive Pulse).
 *
 * Features:
 * - Dynamic time-of-day greeting ("Morning Pulse", "Midday Brief", "Evening Dispatch", "Nightly Digest").
 * - Hands-free on-device Text-To-Speech (TTS) audio dispatch with play, pause, and stop controls.
 * - Interactive multi-sentence narrative highlights with topic filters.
 * - 1-tap "✨ Synthesize Carousel" action that launches editorial carousel generation.
 */
@Composable
public fun CatchMeUpDigestCard(
    digest: FeedDigest,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onScrollToFeed: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    onSynthesizeCarousel: ((FeedDigest) -> Unit)? = null,
) {
    val uriHandler = LocalUriHandler.current
    var expanded by remember { mutableStateOf(true) }
    var selectedTopicFilter by remember { mutableStateOf<String?>(null) }
    val audioState = rememberAudioBriefingState()

    // Dynamic Time-of-Day Greeting
    val currentHour = remember {
        Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    }
    val greeting = remember(currentHour) {
        when (currentHour) {
            in 5..11 -> "Morning Pulse"
            in 12..16 -> "Midday Brief"
            in 17..21 -> "Evening Dispatch"
            else -> "Nightly Digest"
        }
    }

    // Hands-Free TTS Speech Script
    val audioScript = remember(digest, greeting) {
        buildString {
            append("Good day. Here is your $greeting, distilling ${digest.postCount} updates from your timeline. ")
            digest.highlights.forEachIndexed { index, highlight ->
                append("Topic ${index + 1}: ${highlight.topic}. ")
                append("${highlight.summary} ")
                if (!highlight.authorName.isNullOrBlank()) {
                    append("Shared by ${highlight.authorName}. ")
                }
            }
            append("That concludes your executive briefing.")
        }
    }

    val displayedHighlights = remember(digest.highlights, selectedTopicFilter) {
        val filter = selectedTopicFilter
        if (filter == null) {
            digest.highlights
        } else {
            digest.highlights.filter {
                it.topic.equals(filter, ignoreCase = true) ||
                    it.summary.contains(filter.removePrefix("#"), ignoreCase = true)
            }.ifEmpty { digest.highlights }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = screenHorizontalPadding, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(PlatformTheme.colorScheme.cardAlt.copy(alpha = 0.52f))
            .border(
                width = 0.9.dp,
                color = if (audioState.isSpeaking) {
                    PlatformTheme.colorScheme.primary.copy(alpha = 0.45f)
                } else {
                    PlatformTheme.colorScheme.outline.copy(alpha = 0.24f)
                },
                shape = RoundedCornerShape(20.dp),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            // Header Row (Clickable to expand/collapse)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
            ) {
                // Editorial Tonal Squircle Icon
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(PlatformTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.WandMagicSparkles,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = PlatformTheme.colorScheme.primary,
                    )
                }

                // Title & Subtitle
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PlatformText(
                            text = greeting,
                            style = PlatformTheme.typography.title,
                            fontWeight = FontWeight.Bold,
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(PlatformTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            PlatformText(
                                text = "${digest.postCount} Signals",
                                style = PlatformTheme.typography.caption,
                                fontWeight = FontWeight.SemiBold,
                                color = PlatformTheme.colorScheme.primary,
                            )
                        }
                    }
                    PlatformText(
                        text = if (expanded) {
                            "${digest.highlights.size} Curated Insights • Zero Egress"
                        } else {
                            "Tap to expand executive briefing"
                        },
                        style = PlatformTheme.typography.caption,
                        color = PlatformTheme.colorScheme.caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Expand/Collapse toggle
                PlatformIconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(30.dp),
                ) {
                    FAIcon(
                        imageVector = if (expanded) FontAwesomeIcons.Solid.AngleUp else FontAwesomeIcons.Solid.AngleDown,
                        contentDescription = "Toggle Digest",
                        modifier = Modifier.size(14.dp),
                        tint = PlatformTheme.colorScheme.caption,
                    )
                }

                // Dismiss button
                PlatformIconButton(
                    onClick = {
                        audioState.stop()
                        onDismiss()
                    },
                    modifier = Modifier.size(30.dp),
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.Xmark,
                        contentDescription = "Dismiss",
                        modifier = Modifier.size(14.dp),
                        tint = PlatformTheme.colorScheme.caption,
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(10.dp))

                    // Audio Briefing Control Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(13.dp))
                            .background(
                                if (audioState.isSpeaking) {
                                    PlatformTheme.colorScheme.primary.copy(alpha = 0.12f)
                                } else {
                                    PlatformTheme.colorScheme.card.copy(alpha = 0.65f)
                                }
                            )
                            .border(
                                width = 0.7.dp,
                                color = if (audioState.isSpeaking) {
                                    PlatformTheme.colorScheme.primary.copy(alpha = 0.45f)
                                } else {
                                    PlatformTheme.colorScheme.outline.copy(alpha = 0.16f)
                                },
                                shape = RoundedCornerShape(13.dp),
                            )
                            .padding(horizontal = 11.dp, vertical = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                AudioWaveAnimation(
                                    isPlaying = audioState.isSpeaking,
                                    color = if (audioState.isSpeaking) PlatformTheme.colorScheme.primary else PlatformTheme.colorScheme.caption,
                                )
                                Column {
                                    PlatformText(
                                        text = when {
                                            audioState.isSpeaking -> "Executive Audio Briefing Playing"
                                            audioState.isPaused -> "Briefing Paused"
                                            else -> "Hands-Free Audio Dispatch"
                                        },
                                        style = PlatformTheme.typography.caption,
                                        fontWeight = FontWeight.Bold,
                                        color = if (audioState.isSpeaking) PlatformTheme.colorScheme.primary else PlatformTheme.colorScheme.text,
                                    )
                                    PlatformText(
                                        text = when {
                                            audioState.isSpeaking -> "On-Device Speech Synthesis • Zero Egress"
                                            audioState.isPaused -> "Tap play to resume your executive dispatch"
                                            else -> "Listen to this digest aloud"
                                        },
                                        style = PlatformTheme.typography.caption,
                                        color = PlatformTheme.colorScheme.caption,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                // Play / Pause Button
                                PlatformIconButton(
                                    onClick = {
                                        when {
                                            audioState.isSpeaking -> audioState.pause()
                                            audioState.isPaused -> audioState.resume()
                                            else -> audioState.speak(audioScript)
                                        }
                                    },
                                    modifier = Modifier.size(34.dp),
                                ) {
                                    FAIcon(
                                        imageVector = if (audioState.isSpeaking) FontAwesomeIcons.Solid.CirclePause else FontAwesomeIcons.Solid.CirclePlay,
                                        contentDescription = if (audioState.isSpeaking) "Pause Briefing" else "Play Briefing",
                                        modifier = Modifier.size(20.dp),
                                        tint = PlatformTheme.colorScheme.primary,
                                    )
                                }

                                // Stop Button (visible if speaking or paused)
                                if (audioState.isSpeaking || audioState.isPaused) {
                                    PlatformIconButton(
                                        onClick = { audioState.stop() },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        FAIcon(
                                            imageVector = FontAwesomeIcons.Solid.Xmark,
                                            contentDescription = "Stop Briefing",
                                            modifier = Modifier.size(13.dp),
                                            tint = PlatformTheme.colorScheme.caption,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Interactive Topic Filter Chips
                    if (digest.topTopics.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(digest.topTopics) { topic ->
                                val isSelected = selectedTopicFilter == topic
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            if (isSelected) {
                                                PlatformTheme.colorScheme.primary.copy(alpha = 0.16f)
                                            } else {
                                                PlatformTheme.colorScheme.card.copy(alpha = 0.85f)
                                            }
                                        )
                                        .border(
                                            0.7.dp,
                                            if (isSelected) {
                                                PlatformTheme.colorScheme.primary.copy(alpha = 0.55f)
                                            } else {
                                                PlatformTheme.colorScheme.outline.copy(alpha = 0.22f)
                                            },
                                            RoundedCornerShape(50),
                                        )
                                        .clickable {
                                            selectedTopicFilter = if (isSelected) null else topic
                                        }
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    PlatformText(
                                        text = topic,
                                        style = PlatformTheme.typography.caption,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) PlatformTheme.colorScheme.primary else PlatformTheme.colorScheme.text,
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Key Highlights (Interactive Cards)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        displayedHighlights.forEach { highlight ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(PlatformTheme.colorScheme.card.copy(alpha = 0.72f))
                                    .border(
                                        0.6.dp,
                                        PlatformTheme.colorScheme.outline.copy(alpha = 0.16f),
                                        RoundedCornerShape(12.dp),
                                    )
                                    .let { mod ->
                                        val clickHandler = highlight.onClick
                                        if (clickHandler != null) {
                                            mod.clickable {
                                                clickHandler.invoke(
                                                    ClickContext(
                                                        launcher = { url -> uriHandler.openUri(url) }
                                                    )
                                                )
                                            }
                                        } else {
                                            mod
                                        }
                                    }
                                    .padding(horizontal = 11.dp, vertical = 9.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    // Author avatar or bullet indicator
                                    val avatarUrl = highlight.authorAvatar
                                    if (!avatarUrl.isNullOrBlank()) {
                                        AvatarComponent(
                                            data = avatarUrl.toUiImage(),
                                            size = 26.dp,
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 5.dp)
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(PlatformTheme.colorScheme.primary),
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            val authorName = highlight.authorName
                                            if (!authorName.isNullOrBlank()) {
                                                PlatformText(
                                                    text = authorName,
                                                    style = PlatformTheme.typography.caption,
                                                    fontWeight = FontWeight.Bold,
                                                    color = PlatformTheme.colorScheme.text,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(PlatformTheme.colorScheme.primary.copy(alpha = 0.11f))
                                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                                            ) {
                                                PlatformText(
                                                    text = highlight.topic,
                                                    style = PlatformTheme.typography.caption,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = PlatformTheme.colorScheme.primary,
                                                    maxLines = 1,
                                                )
                                            }
                                            Spacer(modifier = Modifier.weight(1f))
                                            if (highlight.onClick != null) {
                                                FAIcon(
                                                    imageVector = FontAwesomeIcons.Solid.AngleRight,
                                                    contentDescription = "Open Post",
                                                    modifier = Modifier.size(11.dp),
                                                    tint = PlatformTheme.colorScheme.caption.copy(alpha = 0.65f),
                                                )
                                            }
                                        }
                                        PlatformText(
                                            text = highlight.summary,
                                            style = PlatformTheme.typography.body,
                                            color = PlatformTheme.colorScheme.text.copy(alpha = 0.92f),
                                            modifier = Modifier.padding(top = 3.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(
                        color = PlatformTheme.colorScheme.outline.copy(alpha = 0.14f),
                        thickness = 0.6.dp,
                    )

                    // Bottom Action Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (onSynthesizeCarousel != null) {
                                PlatformTextButton(
                                    onClick = { onSynthesizeCarousel(digest) },
                                ) {
                                    FAIcon(
                                        imageVector = FontAwesomeIcons.Solid.WandMagicSparkles,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp),
                                        tint = PlatformTheme.colorScheme.primary,
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    PlatformText(
                                        text = "Synthesize Carousel",
                                        style = PlatformTheme.typography.caption,
                                        fontWeight = FontWeight.Bold,
                                        color = PlatformTheme.colorScheme.primary,
                                    )
                                }
                            }

                            if (onRefresh != null) {
                                PlatformTextButton(onClick = onRefresh) {
                                    FAIcon(
                                        imageVector = FontAwesomeIcons.Solid.RotateRight,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    PlatformText("Refresh", style = PlatformTheme.typography.caption)
                                }
                            }
                        }

                        if (onScrollToFeed != null) {
                            PlatformTextButton(onClick = onScrollToFeed) {
                                PlatformText(
                                    "Timeline ↓",
                                    style = PlatformTheme.typography.caption,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
