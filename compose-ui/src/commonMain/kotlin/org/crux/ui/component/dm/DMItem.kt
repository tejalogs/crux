package org.crux.ui.component.dm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CircleExclamation
import org.crux.compose.ui.Res
import org.crux.compose.ui.dm_deleted
import org.crux.compose.ui.dm_sending
import org.crux.compose.ui.send
import org.crux.ui.component.AvatarComponent
import org.crux.ui.component.AvatarComponentDefaults
import org.crux.ui.component.DateTimeText
import org.crux.ui.component.FAIcon
import org.crux.ui.component.RichText
import org.crux.ui.component.placeholder
import org.crux.ui.component.platform.PlatformIconButton
import org.crux.ui.component.platform.PlatformText
import org.crux.ui.component.status.CommonStatusComponent
import org.crux.ui.component.status.MediaItem
import org.crux.ui.model.UiDMItem
import org.crux.ui.model.UiMedia
import org.crux.ui.model.UiProfile
import org.crux.ui.route.DeeplinkRoute
import org.crux.ui.route.toUri
import org.crux.ui.theme.PlatformTheme
import org.jetbrains.compose.resources.stringResource

@Composable
public fun DMItem(
    item: UiDMItem,
    onRetry: () -> Unit,
    onUserClicked: (UiProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier =
            modifier
                .fillMaxWidth(),
        horizontalAlignment =
            if (item.isFromMe) {
                Alignment.End
            } else {
                Alignment.Start
            },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(0.75f),
            contentAlignment =
                if (item.isFromMe) {
                    Alignment.CenterEnd
                } else {
                    Alignment.CenterStart
                },
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (item.showSender) {
                    AvatarComponent(
                        data = item.user.avatar,
                        modifier =
                            Modifier.clickable {
                                onUserClicked.invoke(item.user)
                            },
                    )
                }
                if (item.sendState == UiDMItem.SendState.Failed) {
                    PlatformIconButton(
                        onClick = onRetry,
                    ) {
                        FAIcon(
                            FontAwesomeIcons.Solid.CircleExclamation,
                            contentDescription = stringResource(Res.string.send),
                            tint = PlatformTheme.colorScheme.error,
                        )
                    }
                }
                when (val message = item.content) {
                    is UiDMItem.Message.Text -> {
                        RichText(
                            text = message.text,
                            modifier =
                                Modifier
                                    .background(
                                        color = PlatformTheme.colorScheme.card,
                                        shape =
                                            if (item.isFromMe) {
                                                PlatformTheme.shapes.dmShapeFromMe
                                            } else {
                                                PlatformTheme.shapes.dmShapeFromOther
                                            },
                                    ).padding(
                                        vertical = 8.dp,
                                        horizontal = 16.dp,
                                    ),
                            color = PlatformTheme.colorScheme.onCard,
                        )
                    }

                    UiDMItem.Message.Deleted -> {
                        PlatformText(
                            text = stringResource(Res.string.dm_deleted),
                            style = PlatformTheme.typography.caption,
                            color = PlatformTheme.colorScheme.onCard,
                        )
                    }

                    is UiDMItem.Message.Media -> {
                        MediaItem(
                            media = message.media,
                            modifier =
                                Modifier
                                    .clip(PlatformTheme.shapes.large)
                                    .clickable {
                                        if (message.media is UiMedia.Image) {
                                            uriHandler.openUri(
                                                DeeplinkRoute.Media
                                                    .Image(
                                                        message.media.url,
                                                        previewUrl = null,
                                                    ).toUri(),
                                            )
                                        }
                                    },
                        )
                    }

                    is UiDMItem.Message.Status -> {
                        CommonStatusComponent(
                            message.status,
                            modifier =
                                Modifier
                                    .clip(PlatformTheme.shapes.large)
                                    .background(
                                        color =
                                            if (item.isFromMe) {
                                                PlatformTheme.colorScheme.primaryContainer
                                            } else {
                                                PlatformTheme.colorScheme.card
                                            },
                                        shape = PlatformTheme.shapes.large,
                                    ).padding(8.dp),
                        )
                    }
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (item.showSender) {
                Spacer(modifier = Modifier.width(AvatarComponentDefaults.size))
                RichText(
                    text = item.user.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textStyle = PlatformTheme.typography.caption,
                    color = PlatformTheme.colorScheme.onCard,
                )
            }
            if (item.sendState == UiDMItem.SendState.Sending) {
                PlatformText(
                    text = stringResource(Res.string.dm_sending),
                    style = PlatformTheme.typography.caption,
                    color = PlatformTheme.colorScheme.onCard,
                )
            } else if (item.sendState == null || item.sendState != UiDMItem.SendState.Failed) {
                DateTimeText(
                    item.timestamp,
                    style = PlatformTheme.typography.caption,
                    color = PlatformTheme.colorScheme.onCard,
                )
            }
        }
    }
}

@Composable
public fun DMLoadingItem(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        PlatformText(
            text =
                "Lorem ipsum dolor sit amet, consectetur adipiscing elit." +
                    " Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.",
            modifier =
                Modifier
                    .placeholder(true)
                    .background(
                        color = PlatformTheme.colorScheme.card,
                        shape = PlatformTheme.shapes.dmShapeFromOther,
                    ).padding(
                        vertical = 8.dp,
                        horizontal = 16.dp,
                    ),
            color = PlatformTheme.colorScheme.onCard,
        )
    }
}
