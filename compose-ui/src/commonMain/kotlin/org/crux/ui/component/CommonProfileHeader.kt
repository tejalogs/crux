package org.crux.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.crux.compose.ui.Res
import org.crux.compose.ui.profile_banner
import org.crux.compose.ui.profile_picture
import org.crux.model.MicroBlogKey
import org.crux.ui.component.platform.PlatformText
import org.crux.ui.component.platform.isBigScreen
import org.crux.ui.model.UiHandle
import org.crux.ui.model.UiIcon
import org.crux.ui.model.UiMedia
import org.crux.ui.render.UiRichText
import org.crux.ui.theme.PlatformTheme
import org.crux.ui.theme.screenHorizontalPadding
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun CommonProfileHeader(
    banner: UiMedia.Image?,
    avatar: UiMedia.Image?,
    displayName: UiRichText,
    userKey: MicroBlogKey,
    handle: UiHandle,
    modifier: Modifier = Modifier,
    platformId: String? = null,
    host: String? = null,
    platformIcon: UiIcon? = null,
    onAvatarClick: (() -> Unit)? = null,
    onBannerClick: (() -> Unit)? = null,
    headerTrailing: @Composable RowScope.() -> Unit = {},
    handleTrailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val isBigScreen = isBigScreen()
    val profileName = displayName.raw.ifBlank { handle.canonical }
    val statusBarHeight =
        with(LocalDensity.current) {
            WindowInsets.statusBars.getTop(this).toDp()
        }
    val actualBannerHeight =
        remember(statusBarHeight) {
            ProfileHeaderConstants.BANNER_HEIGHT.dp + statusBarHeight
        }
    Box(
        modifier =
            modifier
                .padding(bottom = 8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(actualBannerHeight),
        ) {
            banner?.let {
                NetworkImage(
                    model = it.url,
                    contentDescription = stringResource(Res.string.profile_banner, profileName),
                    customHeaders = it.customHeaders,
                    modifier =
                        Modifier
                            .clipToBounds()
                            .fillMaxWidth()
                            .height(actualBannerHeight)
                            .let {
                                if (onBannerClick != null) {
                                    it.clickable {
                                        onBannerClick.invoke()
                                    }
                                } else {
                                    it
                                }
                            },
                )
            } ?: Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(actualBannerHeight)
                        .background(PlatformTheme.colorScheme.card),
            )

            // Top soft scrim for status bar legibility
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(statusBarHeight + 36.dp)
                        .background(
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        Color.Black.copy(alpha = 0.40f),
                                        Color.Transparent,
                                    ),
                            ),
                        ),
            )

            // Bottom soft scrim for depth
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.35f),
                                    ),
                            ),
                        ),
            )
        }

        // avatar & user info
        Column(
            modifier =
                Modifier
                    .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = screenHorizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .padding(
                                top = (actualBannerHeight - ProfileHeaderConstants.AVATAR_SIZE.dp / 2),
                            ).clip(CircleShape)
                            .border(3.5.dp, PlatformTheme.colorScheme.card, CircleShape),
                ) {
                    AvatarComponent(
                        data = avatar,
                        platformId = platformId,
                        host = host ?: userKey.host,
                        platformIcon = platformIcon,
                        size = ProfileHeaderConstants.AVATAR_SIZE.dp,
                        contentDescription = stringResource(Res.string.profile_picture, profileName),
                        modifier =
                            Modifier
                                .let {
                                    if (onAvatarClick != null) {
                                        it.clickable {
                                            onAvatarClick.invoke()
                                        }
                                    } else {
                                        it
                                    }
                                },
                    )
                }
                Spacer(
                    modifier =
                        Modifier
                            .weight(1f),
                )
                Row(
                    modifier =
                        Modifier
                            .padding(top = actualBannerHeight),
                ) {
                    headerTrailing()
                }
            }
            Column(
                modifier =
                    Modifier
                        .let {
                            if (isBigScreen) {
                                it
                            } else {
                                it
                                    .padding(horizontal = screenHorizontalPadding)
                                    .padding(bottom = 8.dp)
                                    .listCard()
                                    .background(PlatformTheme.colorScheme.card)
                                    .fillMaxWidth()
                            }
                        }.padding(horizontal = screenHorizontalPadding, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SelectionContainer {
                    Column {
                        RichText(
                            text = displayName,
                            textStyle = PlatformTheme.typography.title,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PlatformText(
                                text = handle.canonical,
                                style = PlatformTheme.typography.body,
                                color = PlatformTheme.colorScheme.caption,
                            )
                            handleTrailing.invoke(this)
                        }
                    }
                }
                content()
            }
        }
    }
}

internal object ProfileHeaderConstants {
    const val BANNER_HEIGHT = 150
    const val AVATAR_SIZE = 96
}
