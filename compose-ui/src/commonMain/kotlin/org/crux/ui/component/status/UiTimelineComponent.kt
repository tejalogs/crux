package org.crux.ui.component.status

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.zIndex
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Check
import compose.icons.fontawesomeicons.solid.Xmark
import org.crux.compose.ui.Res
import org.crux.compose.ui.notification_item_accept_follow_request
import org.crux.compose.ui.notification_item_reject_follow_request
import org.crux.data.datasource.microblog.ActionMenu
import org.crux.data.model.PostActionStyle
import org.crux.model.MicroBlogKey
import org.crux.ui.component.AvatarComponent
import org.crux.ui.component.AvatarComponentDefaults
import org.crux.ui.component.CruxDividerDefaults
import org.crux.ui.component.FAIcon
import org.crux.ui.component.LocalTimelineAppearance
import org.crux.ui.component.NetworkBadge
import org.crux.ui.component.VerticalDivider
import org.crux.ui.component.platform.PlatformButton
import org.crux.ui.component.platform.PlatformCard
import org.crux.ui.component.platform.PlatformFilledTonalButton
import org.crux.ui.component.platform.PlatformText
import org.crux.ui.component.platform.PlatformTextStyle
import org.crux.ui.component.toImageVector
import org.crux.ui.model.ClickContext
import org.crux.ui.model.UiTimelineV2
import org.crux.ui.model.asTimelinePostItem
import org.crux.ui.theme.PlatformContentColor
import org.crux.ui.theme.PlatformTheme
import org.crux.ui.theme.screenHorizontalPadding
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun UiTimelineComponent(
    item: UiTimelineV2,
    modifier: Modifier = Modifier,
    detailStatusKey: MicroBlogKey? = null,
    hasParentConnector: Boolean = false,
    hasChildConnector: Boolean = false,
) {
    when (item) {
        is UiTimelineV2.Post -> {
            StatusContent(
                data = item.asTimelinePostItem() ?: return,
                detailStatusKey = detailStatusKey,
                hasParentConnector = hasParentConnector,
                hasChildConnector = hasChildConnector,
                modifier = modifier,
            )
        }

        is UiTimelineV2.TimelinePostItem -> {
            StatusContent(
                data = item,
                detailStatusKey = detailStatusKey,
                hasParentConnector = hasParentConnector,
                hasChildConnector = hasChildConnector,
                modifier = modifier,
            )
        }

        is UiTimelineV2.User -> {
            UserContent(
                item,
                modifier =
                    Modifier
                        .padding(horizontal = screenHorizontalPadding)
                        .padding(vertical = 8.dp),
            )
        }

        is UiTimelineV2.UserList -> {
            UserListContent(
                data = item,
                modifier =
                    Modifier
                        .padding(horizontal = screenHorizontalPadding)
                        .padding(vertical = 8.dp),
            )
        }

        is UiTimelineV2.Feed -> {
            FeedComponent(
                data = item,
                modifier = modifier,
            )
        }

        is UiTimelineV2.Message -> {
            TopMessageComponent(
                data = item,
                topMessageOnly = true,
                modifier =
                    Modifier
                        .padding(horizontal = screenHorizontalPadding)
                        .padding(vertical = 8.dp)
                        .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun UserContent(
    item: UiTimelineV2.User,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item.message?.let { message ->
            TopMessageComponent(
                data = message,
                topMessageOnly = false,
                modifier =
                    Modifier
                        .fillMaxWidth(),
            )
        }
        CommonStatusHeaderComponent(
            data = item.value,
            onUserClick = {
                item.value.onClicked.invoke(
                    ClickContext(
                        launcher = {
                            uriHandler.openUri(it)
                        },
                    ),
                )
            },
        )
        if (item.button.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item.button.fastForEach { button ->
                    when ((button.text as? ActionMenu.Item.Text.Localized)?.type) {
                        ActionMenu.Item.Text.Localized.Type.AcceptFollowRequest -> {
                            PlatformFilledTonalButton(
                                onClick = {
                                    button.onClicked.invoke(
                                        ClickContext(
                                            launcher = {
                                                uriHandler.openUri(it)
                                            },
                                        ),
                                    )
                                },
                            ) {
                                FAIcon(
                                    FontAwesomeIcons.Solid.Check,
                                    contentDescription =
                                        stringResource(
                                            Res.string.notification_item_accept_follow_request,
                                        ),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                PlatformText(
                                    text =
                                        stringResource(
                                            Res.string.notification_item_accept_follow_request,
                                        ),
                                )
                            }
                        }

                        ActionMenu.Item.Text.Localized.Type.RejectFollowRequest -> {
                            PlatformButton(
                                onClick = {
                                    button.onClicked.invoke(
                                        ClickContext(
                                            launcher = {
                                                uriHandler.openUri(it)
                                            },
                                        ),
                                    )
                                },
                                content = {
                                    FAIcon(
                                        FontAwesomeIcons.Solid.Xmark,
                                        contentDescription =
                                            stringResource(
                                                Res.string.notification_item_reject_follow_request,
                                            ),
                                        tint = PlatformTheme.colorScheme.error,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    PlatformText(
                                        text =
                                            stringResource(
                                                Res.string.notification_item_reject_follow_request,
                                            ),
                                        color = PlatformTheme.colorScheme.error,
                                    )
                                },
                            )
                        }

                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun UserListContent(
    data: UiTimelineV2.UserList,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val appearance = LocalTimelineAppearance.current
    val platformId = data.post?.platformId ?: data.users.firstOrNull()?.platformId
    val host =
        data.post?.statusKey?.host ?: data.users
            .firstOrNull()
            ?.key
            ?.host ?: data.statusKey.host

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Modernized Grouped Notification Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Stacked Overlapping Actor Avatars
            val displayUsers = data.users.take(5)
            val overflowCount = data.users.size - displayUsers.size
            val avatarSize = 32.dp
            val overlapOffset = 20.dp
            val totalAvatarWidth = (displayUsers.size - 1).coerceAtLeast(0) * 20 + 32 + (if (overflowCount > 0) 24 else 0)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .width(totalAvatarWidth.dp)
                            .height(avatarSize),
                ) {
                    displayUsers.forEachIndexed { index, user ->
                        Box(
                            modifier =
                                Modifier
                                    .offset(x = (index * 20).dp)
                                    .zIndex((displayUsers.size - index).toFloat())
                                    .size(avatarSize)
                                    .border(2.dp, PlatformTheme.colorScheme.card, CircleShape)
                                    .clip(CircleShape)
                                    .clickable {
                                        user.onClicked.invoke(
                                            ClickContext(
                                                launcher = { uriHandler.openUri(it) },
                                            ),
                                        )
                                    },
                        ) {
                            AvatarComponent(
                                data = user.avatar,
                                size = avatarSize,
                            )
                        }
                    }
                    if (overflowCount > 0) {
                        Box(
                            modifier =
                                Modifier
                                    .offset(x = (displayUsers.size * 20).dp)
                                    .zIndex(0f)
                                    .size(avatarSize)
                                    .border(2.dp, PlatformTheme.colorScheme.card, CircleShape)
                                    .clip(CircleShape)
                                    .background(PlatformTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            PlatformText(
                                text = "+$overflowCount",
                                style = PlatformTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                                color = PlatformTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }

            // Origin platform signature badge (Twitter, Bluesky, Mastodon, Instagram, LinkedIn)
            NetworkBadge(
                platformId = platformId,
                host = host,
                badgeSize = 18.dp,
            )
        }

        data.message?.let { message ->
            TopMessageComponent(
                data = message,
                topMessageOnly = false,
                modifier =
                    Modifier
                        .fillMaxWidth(),
            )
        }

        val status = data.post
        if (status != null) {
            CompositionLocalProvider(
                LocalTimelineAppearance provides
                    appearance.copy(
                        postActionStyle = PostActionStyle.Hidden,
                    ),
            ) {
                CommonStatusComponent(
                    item = status,
                    modifier =
                        Modifier
                            .border(
                                CruxDividerDefaults.thickness,
                                color = CruxDividerDefaults.color,
                                shape = PlatformTheme.shapes.medium,
                            ).clip(
                                shape = PlatformTheme.shapes.medium,
                            ).padding(8.dp),
                    isQuote = true,
                )
            }
        }
    }
}

@Composable
private fun StatusContent(
    data: UiTimelineV2.TimelinePostItem,
    detailStatusKey: MicroBlogKey?,
    hasParentConnector: Boolean = false,
    hasChildConnector: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val post = data.displayPost
    val presentation = data.presentation
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (presentation.inlineParents.any()) {
            CompositionLocalProvider(
                LocalTimelineAppearance provides
                    LocalTimelineAppearance.current.copy(
                        fullWidthPost = false,
                    ),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    presentation.inlineParents.fastForEachIndexed { index, parent ->
                        CommonStatusComponent(
                            item = parent.displayPost,
                            quotes = parent.presentation.quotes,
                            isDetail = false,
                            allowMediaCarousel = true,
                            carouselOuterHorizontalPadding = screenHorizontalPadding,
                            hasParentConnector = index > 0,
                            hasChildConnector = true,
                            modifier =
                                Modifier.padding(
                                    horizontal = screenHorizontalPadding,
                                    vertical = 4.dp,
                                ),
                        )
                    }
                }
            }
        }
        Column {
            presentation.message?.let { message ->
                TopMessageComponent(
                    data = message,
                    topMessageOnly = false,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .padding(
                                horizontal = screenHorizontalPadding,
                            ),
                )
            }
            CommonStatusComponent(
                item = post,
                isDetail = post.statusKey == detailStatusKey,
                allowMediaCarousel = true,
                carouselOuterHorizontalPadding = screenHorizontalPadding,
                hasParentConnector =
                    hasParentConnector || presentation.inlineParents.any() ||
                        (post.replyToHandle != null && post.statusKey != detailStatusKey),
                hasChildConnector = hasChildConnector,
                modifier =
                    Modifier
                        .padding(
                            horizontal = screenHorizontalPadding,
                            vertical = 8.dp,
                        ),
                quotes = presentation.quotes,
            )
        }
    }
}

@Composable
private fun TopMessageComponent(
    data: UiTimelineV2.Message,
    topMessageOnly: Boolean,
    modifier: Modifier = Modifier,
) {
    val appearance = LocalTimelineAppearance.current
    val uriHandler = LocalUriHandler.current
    val icon = data.icon.toImageVector()
    val text: String? =
        when (val type = data.type) {
            is UiTimelineV2.Message.Type.Raw -> {
                type.content
            }

            is UiTimelineV2.Message.Type.Unknown -> {
                type.rawType.ifBlank { null }
            }

            // These ids came from networks Crux no longer has. They stay in the model so cached
            // items still load, but there is no text to show for them.
            is UiTimelineV2.Message.Type.Localized -> {
                null
            }
        }

    if (text != null) {
        StatusRetweetHeaderComponent(
            icon = icon,
            user = data.user,
            text = text,
            color =
                if (topMessageOnly) {
                    PlatformContentColor.current
                } else {
                    PlatformTheme.colorScheme.caption
                },
            textStyle =
                if (topMessageOnly) {
                    PlatformTextStyle.current
                } else {
                    PlatformTheme.typography.caption
                },
            modifier =
                Modifier
                    .clickable {
                        data.onClicked.invoke(
                            ClickContext(
                                launcher = {
                                    uriHandler.openUri(it)
                                },
                            ),
                        )
                    }.let {
                        if (!appearance.fullWidthPost && !topMessageOnly) {
                            it.padding(
                                start = AvatarComponentDefaults.size - PlatformTheme.typography.caption.fontSize.value.dp,
                            )
                        } else {
                            it
                        }
                    }.fillMaxWidth()
                    .then(modifier),
        )
    }
}
