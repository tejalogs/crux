package org.crux.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.crux.data.model.AvatarShape
import org.crux.ui.model.UiIcon
import org.crux.ui.model.UiMedia

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

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        if (!data?.url.isNullOrBlank()) {
            NetworkImage(
                model = data.url,
                contentDescription = contentDescription,
                customHeaders = data.customHeaders,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .then(modifier),
            )

            if (hasBadge) {
                val badgeSize = (size * 0.32f).coerceIn(14.dp, 28.dp)
                NetworkBadge(
                    platformId = platformId,
                    host = host,
                    fallbackIcon = platformIcon,
                    badgeSize = badgeSize,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 1.dp, y = 1.dp),
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
