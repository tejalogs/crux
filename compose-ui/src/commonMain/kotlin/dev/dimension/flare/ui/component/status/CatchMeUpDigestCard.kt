package dev.dimension.flare.ui.component.status

import androidx.compose.animation.AnimatedVisibility
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
import dev.dimension.flare.ui.component.HorizontalDivider
import dev.dimension.flare.ui.component.platform.PlatformIconButton
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.component.platform.PlatformTextButton
import dev.dimension.flare.ui.theme.PlatformTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dimension.flare.ui.model.toUiImage
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.AngleDown
import compose.icons.fontawesomeicons.solid.AngleUp
import compose.icons.fontawesomeicons.solid.Robot
import compose.icons.fontawesomeicons.solid.RotateRight
import compose.icons.fontawesomeicons.solid.Xmark
import dev.dimension.flare.data.ai.FeedDigest
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.theme.screenHorizontalPadding

/**
 * Modern Material 3 Expressive On-Device Feed Digest Card.
 *
 * Distills unread timeline updates into executive topic highlights
 * using on-device Gemma 4 Edge zero-egress intelligence.
 */
@Composable
public fun CatchMeUpDigestCard(
    digest: FeedDigest,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onScrollToFeed: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(true) }

    val prismBorder = remember {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF8B5CF6).copy(alpha = 0.55f),
                Color(0xFF4F46E5).copy(alpha = 0.40f),
                Color(0xFF06B6D4).copy(alpha = 0.55f),
            )
        )
    }

    val prismIconGradient = remember {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF8B5CF6),
                Color(0xFF6366F1),
                Color(0xFF06B6D4),
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = screenHorizontalPadding, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(PlatformTheme.colorScheme.cardAlt.copy(alpha = 0.35f))
            .border(1.dp, prismBorder, RoundedCornerShape(20.dp)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Prism Squircle Icon
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(prismIconGradient),
                    contentAlignment = Alignment.Center,
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.Robot,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color.White,
                    )
                }

                // Title & Subtitle
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PlatformText(
                            text = digest.title,
                            style = PlatformTheme.typography.title,
                            fontWeight = FontWeight.Bold,
                        )
                        // Gemma 4 Edge Pill Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(PlatformTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            PlatformText(
                                text = "Gemma 4 Edge",
                                style = PlatformTheme.typography.caption,
                                fontWeight = FontWeight.SemiBold,
                                color = PlatformTheme.colorScheme.primary,
                            )
                        }
                    }
                    PlatformText(
                        text = digest.subtitle,
                        style = PlatformTheme.typography.caption,
                        color = PlatformTheme.colorScheme.caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Expand/Collapse toggle
                PlatformIconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(32.dp),
                ) {
                    FAIcon(
                        imageVector = if (expanded) FontAwesomeIcons.Solid.AngleUp else FontAwesomeIcons.Solid.AngleDown,
                        contentDescription = "Toggle Digest",
                        modifier = Modifier.size(16.dp),
                        tint = PlatformTheme.colorScheme.caption,
                    )
                }

                // Dismiss button
                PlatformIconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp),
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.Xmark,
                        contentDescription = "Dismiss",
                        modifier = Modifier.size(16.dp),
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
                    Spacer(modifier = Modifier.height(12.dp))

                    // Trending Topics Pill Chips
                    if (digest.topTopics.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(digest.topTopics) { topic ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(PlatformTheme.colorScheme.card.copy(alpha = 0.80f))
                                        .border(0.6.dp, PlatformTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                ) {
                                    PlatformText(
                                        text = topic,
                                        style = PlatformTheme.typography.caption,
                                        fontWeight = FontWeight.Medium,
                                        color = PlatformTheme.colorScheme.text,
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    HorizontalDivider(
                        color = PlatformTheme.colorScheme.outline.copy(alpha = 0.15f),
                        thickness = 0.8.dp,
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Key Highlights
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        digest.highlights.forEach { highlight ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                // Author avatar or bullet squircle
                                if (!highlight.authorAvatar.isNullOrBlank()) {
                                    AvatarComponent(
                                        data = highlight.authorAvatar.toUiImage(),
                                        size = 24.dp,
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 4.dp)
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(PlatformTheme.colorScheme.primary),
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        PlatformText(
                                            text = highlight.topic,
                                            style = PlatformTheme.typography.caption,
                                            fontWeight = FontWeight.Bold,
                                            color = PlatformTheme.colorScheme.primary,
                                        )
                                        if (!highlight.authorHandle.isNullOrBlank()) {
                                            PlatformText(
                                                text = "· ${highlight.authorHandle}",
                                                style = PlatformTheme.typography.caption,
                                                color = PlatformTheme.colorScheme.caption,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    PlatformText(
                                        text = highlight.summary,
                                        style = PlatformTheme.typography.body,
                                        color = PlatformTheme.colorScheme.text,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Bottom Action Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (onRefresh != null) {
                            PlatformTextButton(onClick = onRefresh) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.RotateRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                PlatformText("Refresh", style = PlatformTheme.typography.caption)
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }

                        if (onScrollToFeed != null) {
                            PlatformTextButton(onClick = onScrollToFeed) {
                                PlatformText(
                                    "Dive into Feed ↓",
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
