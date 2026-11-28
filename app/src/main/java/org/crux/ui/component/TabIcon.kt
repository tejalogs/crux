package org.crux.ui.component

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.Regular
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.brands.Bluesky
import compose.icons.fontawesomeicons.brands.Instagram
import compose.icons.fontawesomeicons.brands.LinkedinIn
import compose.icons.fontawesomeicons.brands.Mastodon
import compose.icons.fontawesomeicons.brands.Pixiv
import compose.icons.fontawesomeicons.brands.RedditAlien
import compose.icons.fontawesomeicons.brands.Threads
import compose.icons.fontawesomeicons.brands.Twitter
import compose.icons.fontawesomeicons.brands.Weibo
import compose.icons.fontawesomeicons.brands.XTwitter
import compose.icons.fontawesomeicons.regular.Bookmark
import compose.icons.fontawesomeicons.regular.CommentDots
import compose.icons.fontawesomeicons.regular.Heart
import compose.icons.fontawesomeicons.regular.Star
import compose.icons.fontawesomeicons.solid.At
import compose.icons.fontawesomeicons.solid.Bell
import compose.icons.fontawesomeicons.solid.Bookmark
import compose.icons.fontawesomeicons.solid.Camera
import compose.icons.fontawesomeicons.solid.Check
import compose.icons.fontawesomeicons.solid.CircleInfo
import compose.icons.fontawesomeicons.solid.CircleUser
import compose.icons.fontawesomeicons.solid.Ellipsis
import compose.icons.fontawesomeicons.solid.EllipsisVertical
import compose.icons.fontawesomeicons.solid.Eye
import compose.icons.fontawesomeicons.solid.Gear
import compose.icons.fontawesomeicons.solid.Globe
import compose.icons.fontawesomeicons.solid.Heart
import compose.icons.fontawesomeicons.solid.House
import compose.icons.fontawesomeicons.solid.List
import compose.icons.fontawesomeicons.solid.MagnifyingGlass
import compose.icons.fontawesomeicons.solid.Message
import compose.icons.fontawesomeicons.solid.Minus
import compose.icons.fontawesomeicons.solid.Pen
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.RectangleList
import compose.icons.fontawesomeicons.solid.Reply
import compose.icons.fontawesomeicons.solid.Retweet
import compose.icons.fontawesomeicons.solid.ShareNodes
import compose.icons.fontawesomeicons.solid.SquarePollHorizontal
import compose.icons.fontawesomeicons.solid.SquareRss
import compose.icons.fontawesomeicons.solid.Star
import compose.icons.fontawesomeicons.solid.Thumbtack
import compose.icons.fontawesomeicons.solid.Trash
import compose.icons.fontawesomeicons.solid.Tv
import compose.icons.fontawesomeicons.solid.UserPlus
import compose.icons.fontawesomeicons.solid.UserSlash
import compose.icons.fontawesomeicons.solid.Users
import compose.icons.fontawesomeicons.solid.VolumeXmark
import org.crux.R
import org.crux.data.model.AvatarShape
import org.crux.data.model.IconType
import org.crux.data.model.tab.UiTimelineTabItem
import org.crux.ui.model.UiIcon
import org.crux.ui.model.UiMedia
import org.crux.ui.model.UiStrings
import org.crux.ui.model.UiText

private val FavIconShape = RoundedCornerShape(4.dp)

@Composable
public fun Text(
    text: UiText,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    style: TextStyle = LocalTextStyle.current,
) {
    MaterialText(
        text =
            when (text) {
                is UiText.Localized -> stringResource(text.string.androidRes)
                is UiText.Raw -> text.string
            },
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        style = style,
        maxLines = 1,
    )
}

@Composable
public fun TabIcon(
    tabItem: UiTimelineTabItem,
    modifier: Modifier = Modifier,
    icon: IconType = tabItem.icon,
    title: UiText = tabItem.title,
    size: Dp = 24.dp,
    iconOnly: Boolean = false,
    color: Color = LocalContentColor.current,
) {
    TabIcon(
        icon = icon,
        title = title,
        modifier = modifier,
        size = size,
        iconOnly = iconOnly,
        color = color,
    )
}

@Composable
public fun TabIcon(
    icon: IconType,
    title: UiText,
    modifier: Modifier = Modifier,
    iconOnly: Boolean = false,
    color: Color = LocalContentColor.current,
    size: Dp = 24.dp,
) {
    val text =
        when (title) {
            is UiText.Localized -> stringResource(title.string.androidRes)
            is UiText.Raw -> title.string
        }
    when (icon) {
        is IconType.Avatar -> {
            AvatarComponent(
                data = null,
                host = icon.accountKey.host,
                size = size,
                modifier = modifier,
            )
        }

        is IconType.Material -> {
            FAIcon(
                imageVector = icon.icon.toImageVector(),
                contentDescription = text,
                modifier = modifier.size(size),
                tint = color,
            )
        }

        is IconType.Mixed -> {
            FAIcon(
                imageVector = icon.icon.toImageVector(),
                contentDescription = text,
                modifier = modifier.size(size),
                tint = color,
            )
        }

        is IconType.Url -> {
            NetworkImage(
                icon.url,
                contentDescription = text,
                modifier = modifier.size(size).clip(FavIconShape),
                contentScale = ContentScale.Fit,
            )
        }

        is IconType.FavIcon -> {
            FavIcon(icon.host, modifier, title, size)
        }
    }
}

@Composable
public fun FavIcon(
    host: String,
    modifier: Modifier = Modifier,
    title: UiText? = null,
    size: Dp = 24.dp,
) {
    val cleanHost = host.trim().removePrefix("https://").removePrefix("http://").substringBefore("/")
    if (cleanHost.isNotEmpty()) {
        NetworkImage(
            "https://$cleanHost/favicon.ico",
            contentDescription =
                when (title) {
                    is UiText.Localized -> stringResource(title.string.androidRes)
                    is UiText.Raw -> title.string
                    null -> null
                },
            modifier = modifier.size(size).clip(FavIconShape),
            contentScale = ContentScale.Fit,
        )
    }
}

@get:StringRes
private val UiStrings.androidRes: Int
    get() =
        when (this) {
            UiStrings.Default -> R.string.tab_settings_default
            UiStrings.Home -> R.string.home_tab_home_title
            UiStrings.Notifications -> R.string.home_tab_notifications_title
            UiStrings.Discover -> R.string.home_tab_discover_title
            UiStrings.Me -> R.string.home_tab_me_title
            UiStrings.Settings -> R.string.settings_title
            UiStrings.Featured -> R.string.home_tab_featured_title
            UiStrings.ForYou -> R.string.home_tab_for_you_title
            UiStrings.Popular -> R.string.home_tab_popular_title
            UiStrings.Bookmark -> R.string.home_tab_bookmarks_title
            UiStrings.Favourite -> R.string.home_tab_favorite_title
            UiStrings.List -> R.string.home_tab_list_title
            UiStrings.Feeds -> R.string.home_tab_feeds_title
            UiStrings.Rss -> R.string.rss_title
            UiStrings.AllRssFeeds -> R.string.all_rss_feeds_title
            UiStrings.Login, UiStrings.Verify -> R.string.login_button
            UiStrings.Cancel -> R.string.action_cancel
            else -> R.string.tab_settings_default
        }

@Composable
public fun AvatarComponent(
    data: UiMedia.Image?,
    modifier: Modifier = Modifier,
    size: Dp = AvatarComponentDefaults.size,
    contentDescription: String? = null,
    platformId: String? = null,
    host: String? = null,
    platformIcon: UiIcon? = null,
    showNetworkBadge: Boolean = true,
) {
    val appearanceSettings = LocalTimelineAppearance.current
    val hasBadge = showNetworkBadge && (platformId != null || host != null || platformIcon != null)
    val shape =
        when (appearanceSettings.avatarShape) {
            AvatarShape.CIRCLE -> CircleShape
            AvatarShape.SQUARE -> RoundedCornerShape(8.dp)
        }
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        if (!data?.url.isNullOrBlank()) {
            NetworkImage(
                model = data.url,
                contentDescription = contentDescription,
                customHeaders = data.customHeaders,
                modifier = Modifier.fillMaxSize().clip(shape).then(modifier),
            )
            if (hasBadge) {
                val badgeSize = (size * 0.32f).coerceIn(14.dp, 28.dp)
                NetworkBadge(
                    platformId = platformId,
                    host = host,
                    fallbackIcon = platformIcon,
                    badgeSize = badgeSize,
                    modifier = Modifier.align(Alignment.BottomEnd).offset(x = 1.dp, y = 1.dp),
                )
            }
        } else {
            val isInsta = NetworkBadgeDefaults.isInstagram(platformId, host)
            val badgeIcon = NetworkBadgeDefaults.getBadgeIcon(platformId, host, platformIcon)
            val iconSize = (size * 0.52f).coerceAtLeast(16.dp)
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .then(
                            if (isInsta) {
                                Modifier.background(NetworkBadgeDefaults.instagramBrush)
                            } else {
                                Modifier.background(NetworkBadgeDefaults.getBadgeColor(platformId, host))
                            },
                        ).then(modifier),
                contentAlignment = Alignment.Center,
            ) {
                FAIcon(
                    imageVector = badgeIcon,
                    contentDescription = contentDescription ?: platformId,
                    tint = Color.White,
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}

public object AvatarComponentDefaults {
    public val size: Dp = 44.dp
    public val compatSize: Dp = 20.dp
}

public object NetworkBadgeDefaults {
    public fun getBadgeColor(
        platformId: String?,
        host: String? = null,
    ): Color {
        val platform = platformId?.lowercase() ?: ""
        val h = host?.lowercase() ?: ""
        return when {
            platform.contains("instagram") || h.contains("instagram.com") -> Color(0xFFE1306C)
            platform.contains("linkedin") || h.contains("linkedin.com") -> Color(0xFF0A66C2)
            platform.contains("threads") || h.contains("threads.net") -> Color(0xFF101010)
            platform.contains("rss") || platform.contains("atom") -> Color(0xFFFF8800)
            platform.contains("twitter") || platform.contains("xqt") -> Color(0xFF1DA1F2)
            else -> Color(0xFF6364FF)
        }
    }

    public fun isInstagram(
        platformId: String?,
        host: String? = null,
    ): Boolean {
        val platform = platformId?.lowercase() ?: ""
        val h = host?.lowercase() ?: ""
        return platform.contains("instagram") || h.contains("instagram.com")
    }

    public val instagramBrush: Brush =
        Brush.linearGradient(colors = listOf(Color(0xFF833AB4), Color(0xFFFD1D1D), Color(0xFFFCB045)))

    public fun getBadgeIcon(
        platformId: String?,
        host: String? = null,
        fallbackIcon: UiIcon? = null,
    ): ImageVector {
        val platform = platformId?.lowercase() ?: ""
        val h = host?.lowercase() ?: ""
        return when {
            platform.contains("instagram") || h.contains("instagram.com") -> FontAwesomeIcons.Brands.Instagram
            platform.contains("linkedin") || h.contains("linkedin.com") -> FontAwesomeIcons.Brands.LinkedinIn
            platform.contains("threads") || h.contains("threads.net") -> FontAwesomeIcons.Brands.Threads
            platform.contains("rss") || platform.contains("atom") -> FontAwesomeIcons.Solid.SquareRss
            platform.contains("twitter") || platform.contains("xqt") -> FontAwesomeIcons.Brands.Twitter
            fallbackIcon != null -> fallbackIcon.toImageVector()
            else -> FontAwesomeIcons.Solid.Globe
        }
    }
}

@Composable
public fun NetworkBadge(
    platformId: String?,
    modifier: Modifier = Modifier,
    host: String? = null,
    fallbackIcon: UiIcon? = null,
    badgeSize: Dp = 15.dp,
) {
    val iconSize = (badgeSize * 0.62f).coerceAtLeast(8.dp)
    val isIg = NetworkBadgeDefaults.isInstagram(platformId, host)
    val color = NetworkBadgeDefaults.getBadgeColor(platformId, host)
    val icon = NetworkBadgeDefaults.getBadgeIcon(platformId, host, fallbackIcon)
    Box(
        modifier =
            modifier
                .size(badgeSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .padding(if (badgeSize > 20.dp) 2.dp else 1.5.dp)
                .clip(CircleShape)
                .then(
                    if (isIg) Modifier.background(NetworkBadgeDefaults.instagramBrush) else Modifier.background(color),
                ),
        contentAlignment = Alignment.Center,
    ) {
        FAIcon(
            imageVector = icon,
            contentDescription = platformId ?: "Network",
            tint = Color.White,
            modifier = Modifier.size(iconSize),
        )
    }
}

public fun UiIcon.toImageVector(): ImageVector =
    when (this) {
        UiIcon.Like -> FontAwesomeIcons.Regular.Heart
        UiIcon.Unlike -> FontAwesomeIcons.Solid.Heart
        UiIcon.Retweet, UiIcon.Unretweet -> FontAwesomeIcons.Solid.Retweet
        UiIcon.Reply, UiIcon.Quote -> FontAwesomeIcons.Solid.Reply
        UiIcon.Comment -> FontAwesomeIcons.Regular.CommentDots
        UiIcon.Bookmark -> FontAwesomeIcons.Regular.Bookmark
        UiIcon.Unbookmark -> FontAwesomeIcons.Solid.Bookmark
        UiIcon.More -> FontAwesomeIcons.Solid.Ellipsis
        UiIcon.Delete -> FontAwesomeIcons.Solid.Trash
        UiIcon.Report, UiIcon.Info -> FontAwesomeIcons.Solid.CircleInfo
        UiIcon.React -> FontAwesomeIcons.Solid.Plus
        UiIcon.UnReact -> FontAwesomeIcons.Solid.Minus
        UiIcon.Share -> FontAwesomeIcons.Solid.ShareNodes
        UiIcon.MoreVerticel -> FontAwesomeIcons.Solid.EllipsisVertical
        UiIcon.List -> FontAwesomeIcons.Solid.List
        UiIcon.ChatMessage, UiIcon.Messages -> FontAwesomeIcons.Solid.Message
        UiIcon.Mute, UiIcon.UnMute -> FontAwesomeIcons.Solid.VolumeXmark
        UiIcon.Block, UiIcon.UnBlock -> FontAwesomeIcons.Solid.UserSlash
        UiIcon.Follow -> FontAwesomeIcons.Solid.UserPlus
        UiIcon.Favourite -> FontAwesomeIcons.Solid.Star
        UiIcon.UnFavourite -> FontAwesomeIcons.Regular.Star
        UiIcon.Mention -> FontAwesomeIcons.Solid.At
        UiIcon.Poll -> FontAwesomeIcons.Solid.SquarePollHorizontal
        UiIcon.Edit -> FontAwesomeIcons.Solid.Pen
        UiIcon.Eye -> FontAwesomeIcons.Solid.Eye
        UiIcon.Pin -> FontAwesomeIcons.Solid.Thumbtack
        UiIcon.Check -> FontAwesomeIcons.Solid.Check
        UiIcon.Home -> FontAwesomeIcons.Solid.House
        UiIcon.Notification -> FontAwesomeIcons.Solid.Bell
        UiIcon.Search -> FontAwesomeIcons.Solid.MagnifyingGlass
        UiIcon.Profile -> FontAwesomeIcons.Solid.CircleUser
        UiIcon.Settings -> FontAwesomeIcons.Solid.Gear
        UiIcon.Local -> FontAwesomeIcons.Solid.Users
        UiIcon.World, UiIcon.Misskey, UiIcon.Nostr -> FontAwesomeIcons.Solid.Globe
        UiIcon.Featured -> FontAwesomeIcons.Solid.RectangleList
        UiIcon.Feeds, UiIcon.Rss -> FontAwesomeIcons.Solid.SquareRss
        UiIcon.Channel -> FontAwesomeIcons.Solid.Tv
        UiIcon.Heart -> FontAwesomeIcons.Solid.Heart
        UiIcon.Mastodon -> FontAwesomeIcons.Brands.Mastodon
        UiIcon.Bluesky -> FontAwesomeIcons.Brands.Bluesky
        UiIcon.Pixiv, UiIcon.Fanbox -> FontAwesomeIcons.Brands.Pixiv
        UiIcon.Twitter -> FontAwesomeIcons.Brands.Twitter
        UiIcon.X -> FontAwesomeIcons.Brands.XTwitter
        UiIcon.Weibo -> FontAwesomeIcons.Brands.Weibo
        UiIcon.Pixelfed -> FontAwesomeIcons.Solid.Camera
        UiIcon.Instagram -> FontAwesomeIcons.Brands.Instagram
        UiIcon.Threads -> FontAwesomeIcons.Brands.Threads
        UiIcon.LinkedIn -> FontAwesomeIcons.Brands.LinkedinIn
        UiIcon.Reddit -> FontAwesomeIcons.Brands.RedditAlien
    }
