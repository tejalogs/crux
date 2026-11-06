package org.crux.ui.component.status

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Bolt
import compose.icons.fontawesomeicons.solid.CirclePause
import compose.icons.fontawesomeicons.solid.CirclePlay
import compose.icons.fontawesomeicons.solid.Xmark
import org.crux.compose.ui.Res
import org.crux.compose.ui.catch_up_dismiss_description
import org.crux.compose.ui.catch_up_listen_description
import org.crux.compose.ui.catch_up_open_brief
import org.crux.compose.ui.catch_up_pause_description
import org.crux.compose.ui.catch_up_post_single
import org.crux.compose.ui.catch_up_posts_count
import org.crux.compose.ui.catch_up_title
import org.crux.data.ai.FeedDigest
import org.crux.ui.component.FAIcon
import org.crux.ui.component.platform.PlatformIconButton
import org.crux.ui.component.platform.PlatformText
import org.crux.ui.component.platform.PlatformTextButton
import org.crux.ui.model.ClickContext
import org.crux.ui.theme.PlatformTheme
import org.crux.ui.theme.screenHorizontalPadding
import org.jetbrains.compose.resources.stringResource

/**
 * Material 3 Expressive Compact Catch-up Card for the Home Timeline.
 * Collapsed by default, taking at most ~25% of the screen height.
 */
@Composable
public fun CatchMeUpDigestCard(
    digest: FeedDigest,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onScrollToFeed: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    onSynthesizeCarousel: ((FeedDigest) -> Unit)? = null,
    onOpenFullscreen: (() -> Unit)? = null,
) {
    val uriHandler = LocalUriHandler.current
    val audioState = rememberAudioBriefingState()
    val audioScript = remember(digest) { buildDigestAudioScript(digest) }
    val formattedTime = remember(digest.generatedAt) { formatDigestTime(digest.generatedAt) }

    val postCountText =
        if (digest.postCount == 1) {
            stringResource(Res.string.catch_up_post_single, formattedTime)
        } else {
            stringResource(Res.string.catch_up_posts_count, digest.postCount, formattedTime)
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = screenHorizontalPadding, vertical = 6.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(PlatformTheme.colorScheme.cardAlt.copy(alpha = 0.6f))
                .border(
                    width = 1.dp,
                    color = PlatformTheme.colorScheme.outline.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(20.dp),
                ).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Header Row: Icon + Title + Count + 48dp Dismiss
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(PlatformTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = PlatformTheme.colorScheme.onPrimaryContainer,
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        PlatformText(
                            text = stringResource(Res.string.catch_up_title),
                            style = PlatformTheme.typography.body,
                            fontWeight = FontWeight.Bold,
                            color = PlatformTheme.colorScheme.text,
                        )
                        PlatformText(
                            text = postCountText,
                            style = PlatformTheme.typography.caption,
                            color = PlatformTheme.colorScheme.caption,
                        )
                    }
                }

                // 48dp Dismiss Button
                PlatformIconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp),
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.Xmark,
                        contentDescription = stringResource(Res.string.catch_up_dismiss_description),
                        modifier = Modifier.size(14.dp),
                        tint = PlatformTheme.colorScheme.caption,
                    )
                }
            }

            // Up to 3 compact highlights
            Column(
                modifier = Modifier.padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                digest.highlights.take(3).forEach { highlight ->
                    val highlightOnClick = highlight.onClick
                    DigestCompactHighlightRow(
                        highlight = highlight,
                        onClick =
                            if (highlightOnClick != null) {
                                { highlightOnClick.invoke(ClickContext(uriHandler::openUri)) }
                            } else {
                                null
                            },
                    )
                }
            }

            // Footer: Primary "Open brief" button + Listen button (if supported)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                PlatformTextButton(
                    onClick = { onOpenFullscreen?.invoke() },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                ) {
                    PlatformText(
                        text = stringResource(Res.string.catch_up_open_brief),
                        fontWeight = FontWeight.SemiBold,
                        color = PlatformTheme.colorScheme.primary,
                    )
                }

                if (audioState.isSupported) {
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
                            modifier = Modifier.size(22.dp),
                            tint = PlatformTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
