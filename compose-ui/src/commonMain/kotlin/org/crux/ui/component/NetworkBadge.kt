package org.crux.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.brands.Instagram
import compose.icons.fontawesomeicons.brands.LinkedinIn
import compose.icons.fontawesomeicons.brands.Threads
import compose.icons.fontawesomeicons.brands.Twitter
import compose.icons.fontawesomeicons.solid.Camera
import compose.icons.fontawesomeicons.solid.Globe
import compose.icons.fontawesomeicons.solid.SquareRss
import org.crux.ui.component.platform.PlatformText
import org.crux.ui.model.UiIcon
import org.crux.ui.theme.PlatformTheme
import org.crux.ui.theme.isLightTheme

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
            platform.contains("pixelfed") || h.contains("pixelfed") -> Color(0xFF6366F1)
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
            platform.contains("instagram") || h.contains("instagram.com") -> FontAwesomeIcons.Brands.Instagram
            platform.contains("linkedin") || h.contains("linkedin.com") -> FontAwesomeIcons.Brands.LinkedinIn
            platform.contains("threads") || h.contains("threads.net") -> FontAwesomeIcons.Brands.Threads
            platform.contains("pixelfed") || h.contains("pixelfed") -> FontAwesomeIcons.Solid.Camera
            platform.contains("rss") || platform.contains("atom") -> FontAwesomeIcons.Solid.SquareRss
            platform.contains("twitter") || platform.contains("xqt") -> FontAwesomeIcons.Brands.Twitter
            fallbackIcon != null -> fallbackIcon.toImageVector()
            else -> FontAwesomeIcons.Solid.Globe
        }
    }

    public fun getPlatformDisplayName(
        platformId: String?,
        host: String? = null,
    ): String {
        val platform = platformId?.lowercase() ?: ""
        val h = host?.lowercase() ?: ""
        return when {
            platform.contains("instagram") || h.contains("instagram.com") -> "Instagram"
            platform.contains("linkedin") || h.contains("linkedin.com") -> "LinkedIn"
            platform.contains("threads") || h.contains("threads.net") -> "Threads"
            platform.contains("pixelfed") || h.contains("pixelfed") -> "Pixelfed"
            platform.contains("rss") || platform.contains("atom") -> "RSS"
            platform.contains("twitter") || platform.contains("xqt") -> "X"
            platformId.isNullOrBlank() -> h.ifBlank { "Federated" }
            else -> platformId.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
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

@Composable
public fun PlatformProvenancePill(
    platformId: String?,
    host: String? = null,
    fallbackIcon: UiIcon? = null,
    modifier: Modifier = Modifier,
) {
    val brandColor = NetworkBadgeDefaults.getBadgeColor(platformId, host)
    val isIg = NetworkBadgeDefaults.isInstagram(platformId, host)
    val platformName = NetworkBadgeDefaults.getPlatformDisplayName(platformId, host)
    val icon = NetworkBadgeDefaults.getBadgeIcon(platformId, host, fallbackIcon)
    val isDark = !isLightTheme()

    val tintColor =
        when {
            isIg -> {
                Color(0xFFE1306C)
            }

            platformId?.lowercase()?.contains("threads") == true || host?.lowercase()?.contains("threads.net") == true -> {
                PlatformTheme.colorScheme.text
            }

            else -> {
                brandColor
            }
        }

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (isIg) {
                        Color(0xFFE1306C).copy(alpha = if (isDark) 0.16f else 0.09f)
                    } else {
                        tintColor.copy(alpha = if (isDark) 0.16f else 0.09f)
                    },
                ).border(
                    width = 0.5.dp,
                    color = tintColor.copy(alpha = if (isDark) 0.32f else 0.20f),
                    shape = RoundedCornerShape(6.dp),
                ).padding(horizontal = 5.dp, vertical = 1.5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.5.dp),
        ) {
            FAIcon(
                imageVector = icon,
                contentDescription = platformName,
                tint = tintColor,
                modifier = Modifier.size(9.5.dp),
            )
            PlatformText(
                text = platformName,
                style =
                    PlatformTheme.typography.caption.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                color = tintColor,
                maxLines = 1,
            )
        }
    }
}
