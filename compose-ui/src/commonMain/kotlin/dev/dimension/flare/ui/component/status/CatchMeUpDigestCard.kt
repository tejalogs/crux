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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.AngleDown
import compose.icons.fontawesomeicons.solid.AngleRight
import compose.icons.fontawesomeicons.solid.AngleUp
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

/**
 * Refined Material 3 Expressive On-Device Feed Digest Card ("Catch Me Up").
 *
 * Distills unread timeline updates into interactive, multi-sentence executive
 * topic highlights using zero-egress on-device intelligence.
 */
@Composable
public fun CatchMeUpDigestCard(
    digest: FeedDigest,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onScrollToFeed: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
) {
    val uriHandler = LocalUriHandler.current
    var expanded by remember { mutableStateOf(true) }
    var selectedTopicFilter by remember { mutableStateOf<String?>(null) }

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
            .clip(RoundedCornerShape(18.dp))
            .background(PlatformTheme.colorScheme.cardAlt.copy(alpha = 0.48f))
            .border(
                width = 0.8.dp,
                color = PlatformTheme.colorScheme.outline.copy(alpha = 0.24f),
                shape = RoundedCornerShape(18.dp),
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
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(PlatformTheme.colorScheme.primary.copy(alpha = 0.14f)),
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
                            text = digest.title,
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
                                text = "${digest.postCount} Updates",
                                style = PlatformTheme.typography.caption,
                                fontWeight = FontWeight.SemiBold,
                                color = PlatformTheme.colorScheme.primary,
                            )
                        }
                    }
                    PlatformText(
                        text = if (expanded) digest.subtitle else "Tap to expand ${digest.highlights.size} executive highlights",
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
                    onClick = onDismiss,
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
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (onRefresh != null) {
                            PlatformTextButton(onClick = onRefresh) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.RotateRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                PlatformText("Refresh Brief", style = PlatformTheme.typography.caption)
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }

                        if (onScrollToFeed != null) {
                            PlatformTextButton(onClick = onScrollToFeed) {
                                PlatformText(
                                    "Continue to Timeline ↓",
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
