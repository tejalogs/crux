package dev.dimension.flare.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.brands.Bluesky
import compose.icons.fontawesomeicons.brands.Instagram
import compose.icons.fontawesomeicons.brands.Mastodon
import compose.icons.fontawesomeicons.brands.Pixiv
import compose.icons.fontawesomeicons.brands.Threads
import compose.icons.fontawesomeicons.brands.Twitter
import compose.icons.fontawesomeicons.brands.Weibo
import compose.icons.fontawesomeicons.solid.Camera
import compose.icons.fontawesomeicons.solid.Globe
import compose.icons.fontawesomeicons.solid.SquareRss
import dev.dimension.flare.ui.icons.Misskey
import dev.dimension.flare.ui.icons.Nostr
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.theme.PlatformTheme

public object NetworkBadgeDefaults {
    public fun getBadgeColor(platformId: String?, host: String? = null): Color {
        val platform = platformId?.lowercase() ?: ""
        val h = host?.lowercase() ?: ""
        return when {
            platform.contains("bluesky") || h.contains("bsky") -> Color(0xFF0085FF)
            platform.contains("instagram") || h.contains("instagram.com") -> Color(0xFFE1306C)
            platform.contains("threads") || h.contains("threads.net") -> Color(0xFF101010)
            platform.contains("pixelfed") || h.contains("pixelfed") -> Color(0xFF6366F1)
            platform.contains("mastodon") || h.contains("mastodon") -> Color(0xFF6364FF)
            platform.contains("misskey") -> Color(0xFF86B300)
            platform.contains("nostr") -> Color(0xFF8E44AD)
            platform.contains("rss") || platform.contains("atom") -> Color(0xFFFF8800)
            platform.contains("weibo") || platform.contains("vvo") -> Color(0xFFE6162D)
            platform.contains("twitter") || platform.contains("xqt") -> Color(0xFF1DA1F2)
            platform.contains("pixiv") || platform.contains("fanbox") -> Color(0xFF0096FA)
            else -> Color(0xFF6364FF)
        }
    }

    public fun isInstagram(platformId: String?, host: String? = null): Boolean {
        val platform = platformId?.lowercase() ?: ""
        val h = host?.lowercase() ?: ""
        return platform.contains("instagram") || h.contains("instagram.com")
    }

    public val instagramBrush: Brush =
        Brush.linearGradient(
            colors =
                listOf(
                    Color(0xFF833AB4),
                    Color(0xFFFD1D1D),
                    Color(0xFFFCB045),
                ),
        )

    public fun getBadgeIcon(
        platformId: String?,
        host: String? = null,
        fallbackIcon: UiIcon? = null,
    ): ImageVector {
        val platform = platformId?.lowercase() ?: ""
        val h = host?.lowercase() ?: ""
        return when {
            platform.contains("bluesky") || h.contains("bsky") -> FontAwesomeIcons.Brands.Bluesky
            platform.contains("instagram") || h.contains("instagram.com") -> FontAwesomeIcons.Brands.Instagram
            platform.contains("threads") || h.contains("threads.net") -> FontAwesomeIcons.Brands.Threads
            platform.contains("pixelfed") || h.contains("pixelfed") -> FontAwesomeIcons.Solid.Camera
            platform.contains("mastodon") || h.contains("mastodon") -> FontAwesomeIcons.Brands.Mastodon
            platform.contains("misskey") -> FontAwesomeIcons.Brands.Misskey
            platform.contains("nostr") -> FontAwesomeIcons.Brands.Nostr
            platform.contains("rss") || platform.contains("atom") -> FontAwesomeIcons.Solid.SquareRss
            platform.contains("weibo") || platform.contains("vvo") -> FontAwesomeIcons.Brands.Weibo
            platform.contains("twitter") || platform.contains("xqt") -> FontAwesomeIcons.Brands.Twitter
            platform.contains("pixiv") || platform.contains("fanbox") -> FontAwesomeIcons.Brands.Pixiv
            fallbackIcon != null -> fallbackIcon.toImageVector()
            else -> FontAwesomeIcons.Solid.Globe
        }
    }
}

@Composable
public fun NetworkBadge(
    platformId: String?,
    host: String? = null,
    fallbackIcon: UiIcon? = null,
    modifier: Modifier = Modifier,
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
                .background(PlatformTheme.colorScheme.card)
                .padding(if (badgeSize > 20.dp) 2.dp else 1.5.dp)
                .clip(CircleShape)
                .then(
                    if (isIg) {
                        Modifier.background(NetworkBadgeDefaults.instagramBrush)
                    } else {
                        Modifier.background(color)
                    },
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
