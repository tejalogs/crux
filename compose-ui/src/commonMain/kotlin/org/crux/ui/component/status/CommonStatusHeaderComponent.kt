package org.crux.ui.component.status

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
public fun CommonStatusHeaderComponent(
    data: UiProfile,
    onUserClick: (MicroBlogKey) -> Unit,
    modifier: Modifier = Modifier,
    avatarModifier: Modifier = Modifier,
    nameModifier: Modifier = Modifier,
    handleModifier: Modifier = Modifier,
    avatarSize: Dp = AvatarComponentDefaults.size,
    leadingContent: @Composable (RowScope.() -> Unit)? = {
        Box(avatarModifier, propagateMinConstraints = true) {
            AvatarComponent(
                data = data.avatar,
                platformId = data.platformId,
                host = data.key.host,
                platformIcon = data.platformIcon,
                size = avatarSize,
                contentDescription =
                    stringResource(
                        Res.string.profile_open_user,
                        data.handle.canonical,
                    ),
                modifier =
                    Modifier
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable {
                            onUserClick(data.key)
                        },
            )
        }
    },
    trailing: @Composable RowScope.() -> Unit = {},
) {
    ListComponent(
        leadingContent = leadingContent,
        headlineContent = {
            RichText(
                text = data.name,
                fontWeight = FontWeight.Bold,
                modifier =
                    nameModifier
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            onUserClick(data.key)
                        },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            PlatformText(
                text = "@${data.handle.canonical.removePrefix("@")}",
                style = PlatformTheme.typography.caption,
                color = PlatformTheme.colorScheme.caption,
                modifier =
                    handleModifier
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            onUserClick(data.key)
                        },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = trailing,
        modifier = modifier,
    )
}

@Composable
public fun ListComponent(
    headlineContent: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    leadingContent: @Composable (RowScope.() -> Unit)? = null,
    supportingContent: @Composable ColumnScope.() -> Unit = {},
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingContent != null) {
            leadingContent.invoke(this)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Column(
            modifier =
                Modifier
                    .weight(1f),
        ) {
            headlineContent.invoke(this)
            supportingContent.invoke(this)
        }
        trailingContent.invoke(this)
    }
}
