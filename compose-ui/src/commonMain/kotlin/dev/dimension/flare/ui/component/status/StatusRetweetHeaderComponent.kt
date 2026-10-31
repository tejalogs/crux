package dev.dimension.flare.ui.component.status

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.NetworkBadge
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.model.ClickContext
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.theme.PlatformContentColor
import dev.dimension.flare.ui.theme.PlatformTheme

@Composable
internal fun StatusRetweetHeaderComponent(
    icon: ImageVector,
    user: UiProfile?,
    text: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = PlatformTheme.typography.caption,
    color: androidx.compose.ui.graphics.Color = PlatformContentColor.current,
) {
    val uriHandler = LocalUriHandler.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top,
    ) {
        FAIcon(
            icon,
            contentDescription = null,
            modifier =
                Modifier
                    .padding(top = 2.dp)
                    .size(
                        textStyle.fontSize.value.dp
                            .coerceAtLeast(14.dp),
                    ),
            tint = color,
        )
        Spacer(modifier = Modifier.width(6.dp))
        if (user != null) {
            Box(
                modifier =
                    Modifier
                        .size(20.dp)
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
                    size = 20.dp,
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            NetworkBadge(
                platformId = user.platformId,
                host = user.key.host,
                badgeSize = 13.dp,
                modifier = Modifier.padding(top = 3.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Column(
            modifier = Modifier.weight(1f, fill = false),
        ) {
            if (user != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RichText(
                        text = user.name,
                        layoutDirection = LocalLayoutDirection.current,
                        textStyle = textStyle.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        color = color,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    PlatformText(
                        text = text,
                        style = textStyle,
                        color = color,
                        maxLines = 1,
                    )
                }
            } else {
                PlatformText(
                    text = text,
                    style = textStyle,
                    color = color,
                )
            }
        }
    }
}
