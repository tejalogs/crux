package org.crux.ui.component.status

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.crux.compose.ui.Res
import org.crux.compose.ui.catch_up_author_fallback
import org.crux.compose.ui.catch_up_read_post
import org.crux.data.ai.DigestHighlight
import org.crux.ui.component.AvatarComponent
import org.crux.ui.component.platform.PlatformText
import org.crux.ui.component.platform.PlatformTextButton
import org.crux.ui.model.toUiImage
import org.crux.ui.theme.PlatformTheme
import org.jetbrains.compose.resources.stringResource

/**
 * Compact single-line highlight row for the collapsed inline catch-up card.
 */
@Composable
public fun DigestCompactHighlightRow(
    highlight: DigestHighlight,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val authorFallback = stringResource(Res.string.catch_up_author_fallback)
    val talkBackDescription =
        "${highlight.topic}, by ${highlight.authorName ?: authorFallback}: ${highlight.summary}"

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = talkBackDescription
                }.clip(RoundedCornerShape(10.dp))
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier
                    },
                ).padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AvatarComponent(
            data = highlight.authorAvatar?.toUiImage(),
            size = 24.dp,
            platformId = highlight.platformId,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            PlatformText(
                text = highlight.topic,
                style = PlatformTheme.typography.caption,
                fontWeight = FontWeight.Bold,
                color = PlatformTheme.colorScheme.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            PlatformText(
                text = highlight.summary,
                style = PlatformTheme.typography.caption,
                color = PlatformTheme.colorScheme.caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Detailed news-brief card for the full bottom sheet.
 */
@Composable
public fun DigestDetailHighlightCard(
    highlight: DigestHighlight,
    modifier: Modifier = Modifier,
    onReadPost: (() -> Unit)? = null,
) {
    val authorFallback = stringResource(Res.string.catch_up_author_fallback)
    val talkBackDescription =
        "${highlight.topic}, by ${highlight.authorName ?: authorFallback}: ${highlight.summary}"

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = talkBackDescription
                }.clip(RoundedCornerShape(16.dp))
                .background(PlatformTheme.colorScheme.cardAlt.copy(alpha = 0.5f))
                .border(
                    width = 1.dp,
                    color = PlatformTheme.colorScheme.outline.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(16.dp),
                ).padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Author row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AvatarComponent(
                    data = highlight.authorAvatar?.toUiImage(),
                    size = 36.dp,
                    platformId = highlight.platformId,
                )
                Column(modifier = Modifier.weight(1f)) {
                    PlatformText(
                        text = highlight.authorName ?: authorFallback,
                        style = PlatformTheme.typography.body,
                        fontWeight = FontWeight.SemiBold,
                        color = PlatformTheme.colorScheme.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val authorHandle = highlight.authorHandle
                    if (!authorHandle.isNullOrBlank()) {
                        PlatformText(
                            text = authorHandle,
                            style = PlatformTheme.typography.caption,
                            color = PlatformTheme.colorScheme.caption,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // Topic title
            PlatformText(
                text = highlight.topic,
                style = PlatformTheme.typography.title,
                fontWeight = FontWeight.Bold,
                color = PlatformTheme.colorScheme.primary,
            )

            // Summary
            PlatformText(
                text = highlight.summary,
                style = PlatformTheme.typography.body,
                color = PlatformTheme.colorScheme.text,
            )

            // Engagement & Read post action
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val engagementInfo = highlight.engagementInfo
                if (!engagementInfo.isNullOrBlank()) {
                    PlatformText(
                        text = engagementInfo,
                        style = PlatformTheme.typography.caption,
                        color = PlatformTheme.colorScheme.caption,
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                if (onReadPost != null) {
                    PlatformTextButton(
                        onClick = onReadPost,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                    ) {
                        PlatformText(
                            text = stringResource(Res.string.catch_up_read_post),
                            fontWeight = FontWeight.Medium,
                            color = PlatformTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
