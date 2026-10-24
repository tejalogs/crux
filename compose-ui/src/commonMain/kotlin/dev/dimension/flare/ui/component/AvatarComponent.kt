package dev.dimension.flare.ui.component

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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dimension.flare.data.model.AvatarShape
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiMedia

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

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        NetworkImage(
            model = data?.url,
            contentDescription = contentDescription,
            customHeaders = data?.customHeaders,
            modifier =
                Modifier
                    .fillMaxSize()
                    .clip(
                        when (appearanceSettings.avatarShape) {
                            AvatarShape.CIRCLE -> CircleShape
                            AvatarShape.SQUARE -> RoundedCornerShape(8.dp)
                        },
                    ).then(modifier),
        )

        if (hasBadge) {
            val badgeSize = (size * 0.40f).coerceIn(14.dp, 20.dp)
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
    }
}

public object AvatarComponentDefaults {
    public val size: Dp = 44.dp
    public val compatSize: Dp = 20.dp
}
