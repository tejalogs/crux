package org.crux.ui.component.dm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CircleExclamation
import compose.icons.fontawesomeicons.solid.CircleUser
import compose.icons.fontawesomeicons.solid.List
import org.crux.common.PagingState
import org.crux.compose.ui.Res
import org.crux.compose.ui.dm_list_empty
import org.crux.compose.ui.dm_list_error
import org.crux.model.MicroBlogKey
import org.crux.ui.common.itemsIndexed
import org.crux.ui.component.AvatarComponent
import org.crux.ui.component.AvatarComponentDefaults
import org.crux.ui.component.DateTimeText
import org.crux.ui.component.FAIcon
import org.crux.ui.component.ItemPlaceHolder
import org.crux.ui.component.RichText
import org.crux.ui.component.listCard
import org.crux.ui.component.platform.PlatformSegmentedListItem
import org.crux.ui.component.platform.PlatformText
import org.crux.ui.model.UiDMRoom
import org.crux.ui.theme.PlatformTheme
import org.jetbrains.compose.resources.stringResource
import kotlin.math.min

public fun LazyListScope.dmList(
    data: PagingState<UiDMRoom>,
    onItemClicked: (MicroBlogKey) -> Unit,
) {
    itemsIndexed(
        data,
        emptyContent = {
            Box(
                modifier = Modifier.fillParentMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.List,
                        contentDescription = stringResource(Res.string.dm_list_empty),
                        modifier = Modifier.size(48.dp),
                    )
                    PlatformText(
                        text = stringResource(Res.string.dm_list_empty),
                        style = PlatformTheme.typography.headline,
                    )
                }
            }
        },
        loadingContent = { index, itemCount ->
            ItemPlaceHolder(
                modifier =
                    Modifier
                        .listCard(
                            index = index,
                            totalCount = itemCount,
                        ),
            )
        },
        errorContent = {
            Box(
                modifier = Modifier.fillParentMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.CircleExclamation,
                        contentDescription = stringResource(Res.string.dm_list_error),
                        modifier = Modifier.size(48.dp),
                    )
                    PlatformText(
                        text = stringResource(Res.string.dm_list_error),
                        style = PlatformTheme.typography.headline,
                    )
                    PlatformText(
                        text = it.message.orEmpty(),
                        style = PlatformTheme.typography.headline,
                    )
                }
            }
        },
        itemContent = { index, itemCount, item ->
            PlatformSegmentedListItem(
                onClick = {
                    onItemClicked.invoke(item.key)
                },
                index = index,
                totalCount = itemCount,
                headlineContent = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (item.hasUser) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f),
                            ) {
                                item.users.fastForEach { user ->
                                    RichText(
                                        text = user.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (item.users.size == 1) {
                                        PlatformText(
                                            text = user.handle.canonical,
                                            style = PlatformTheme.typography.caption,
                                            color = PlatformTheme.colorScheme.caption,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                        val lastMessage = item.lastMessage
                        if (lastMessage != null) {
                            DateTimeText(
                                lastMessage.timestamp,
                                style = PlatformTheme.typography.caption,
                                color = PlatformTheme.colorScheme.caption,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                leadingContent = {
                    if (!item.hasUser) {
                        FAIcon(
                            FontAwesomeIcons.Solid.CircleUser,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(AvatarComponentDefaults.size),
                            tint = PlatformTheme.colorScheme.primary,
                        )
                    } else {
                        Box(
                            modifier =
                                Modifier
                                    .size(AvatarComponentDefaults.size),
                        ) {
                            repeat(
                                min(item.users.size, 2),
                            ) {
                                val avatar = item.users[it].avatar
                                if (item.users.size == 1) {
                                    AvatarComponent(avatar)
                                } else {
                                    Box(
                                        modifier =
                                            Modifier
                                                .offset(
                                                    x = (it * 12).dp,
                                                    y = (it * 12).dp,
                                                ),
                                    ) {
                                        AvatarComponent(
                                            avatar,
                                            size = AvatarComponentDefaults.compatSize,
                                        )
                                    }
                                }
                            }
                            if (item.users.size > 1) {
                                PlatformText(
                                    item.users.size.toString(),
                                    modifier =
                                        Modifier
                                            .align(Alignment.BottomEnd)
                                            .background(
                                                PlatformTheme.colorScheme.card,
                                                shape = PlatformTheme.shapes.small,
                                            ).padding(horizontal = 4.dp),
                                )
                            }
                        }
                    }
                },
                supportingContent = {
                    PlatformText(
                        text = item.lastMessageText,
                        style = PlatformTheme.typography.caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingContent = {
                    if (item.unreadCount > 0) {
                        PlatformText(
                            text = item.unreadCount.toString(),
                            modifier =
                                Modifier
                                    .background(
                                        PlatformTheme.colorScheme.primary,
                                        shape = CircleShape,
                                    ).padding(horizontal = 4.dp),
                        )
                    }
                },
            )
        },
    )
}
