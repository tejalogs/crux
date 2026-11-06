package org.crux.ui.component.status

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.crux.compose.ui.Res
import org.crux.compose.ui.profile_open_user
import org.crux.model.MicroBlogKey
import org.crux.ui.component.AvatarComponent
import org.crux.ui.component.AvatarComponentDefaults
import org.crux.ui.component.RichText
import org.crux.ui.component.platform.PlatformText
import org.crux.ui.model.UiProfile
import org.crux.ui.theme.PlatformTheme
import org.jetbrains.compose.resources.stringResource

@Composable
public fun UserCompat(
    user: UiProfile,
    modifier: Modifier = Modifier,
    onUserClick: (MicroBlogKey) -> Unit = {},
    avatarModifier: Modifier = Modifier,
    nameModifier: Modifier = Modifier,
    handleModifier: Modifier = Modifier,
    leading: @Composable (RowScope.() -> Unit)? = {
        Box(avatarModifier, propagateMinConstraints = true) {
            AvatarComponent(
                data = user.avatar,
                platformId = user.platformId,
                host = user.key.host,
                platformIcon = user.platformIcon,
                size = AvatarComponentDefaults.compatSize,
                contentDescription =
                    stringResource(
                        Res.string.profile_open_user,
                        user.handle.canonical,
                    ),
                modifier =
                    Modifier.clickable {
                        onUserClick.invoke(user.key)
                    },
            )
        }
    },
    trailing: @Composable RowScope.() -> Unit = {},
) {
    with(user) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.invoke(this)
            Row(
                modifier =
                    Modifier
                        .weight(1f, fill = false)
                        .clickable {
                            onUserClick.invoke(user.key)
                        },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RichText(
                    text = name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.alignByBaseline().then(nameModifier),
                )
                PlatformText(
                    text = "@${handle.canonical.removePrefix("@")}",
                    style = PlatformTheme.typography.caption,
                    color = PlatformTheme.colorScheme.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alignByBaseline().then(handleModifier),
                )
            }
            trailing.invoke(this)
        }
    }
}
