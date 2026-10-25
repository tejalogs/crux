package dev.dimension.flare.ui.component.status

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

    Surface(
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, prismBorder),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = screenHorizontalPadding, vertical = 8.dp),
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
                        Text(
                            text = digest.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        // Gemma 4 Edge Pill Badge
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        ) {
                            Text(
                                text = "Gemma 4 Edge",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Text(
                        text = digest.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Expand/Collapse toggle
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(32.dp),
                ) {
                    FAIcon(
                        imageVector = if (expanded) FontAwesomeIcons.Solid.AngleUp else FontAwesomeIcons.Solid.AngleDown,
                        contentDescription = "Toggle Digest",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Dismiss button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp),
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.Xmark,
                        contentDescription = "Dismiss",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.80f),
                                    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                                ) {
                                    Text(
                                        text = topic,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
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
                                            .background(MaterialTheme.colorScheme.primary),
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = highlight.topic,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        if (!highlight.authorHandle.isNullOrBlank()) {
                                            Text(
                                                text = "· ${highlight.authorHandle}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    Text(
                                        text = highlight.summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
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
                            TextButton(onClick = onRefresh) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.RotateRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Refresh", style = MaterialTheme.typography.labelMedium)
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }

                        if (onScrollToFeed != null) {
                            TextButton(onClick = onScrollToFeed) {
                                Text("Dive into Feed ↓", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}
