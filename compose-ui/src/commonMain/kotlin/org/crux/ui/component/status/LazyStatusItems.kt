package org.crux.ui.component.status

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.File
import org.crux.common.PagingState
import org.crux.common.onEmpty
import org.crux.common.onEndOfList
import org.crux.common.onError
import org.crux.common.onLoading
import org.crux.common.onSuccess
import org.crux.compose.ui.Res
import org.crux.compose.ui.status_empty
import org.crux.compose.ui.status_loadmore_end
import org.crux.data.model.TimelineDisplayMode
import org.crux.model.MicroBlogKey
import org.crux.ui.component.ErrorContent
import org.crux.ui.component.FAIcon
import org.crux.ui.component.HorizontalDivider
import org.crux.ui.component.placeholder
import org.crux.ui.component.platform.PlatformLinearProgressIndicator
import org.crux.ui.component.platform.PlatformText
import org.crux.ui.model.UiTimelineV2
import org.crux.ui.theme.PlatformTheme
import org.crux.ui.theme.screenHorizontalPadding
import org.jetbrains.compose.resources.stringResource

public fun LazyStaggeredGridScope.status(
    pagingState: PagingState<UiTimelineV2>,
    detailStatusKey: MicroBlogKey? = null,
): Unit =
    with(pagingState) {
        onSuccess {
            items(
                itemCount,
                key =
                    itemKey {
                        it.stableItemKey
                    },
                contentType =
                    itemContentType {
                        it.itemType
                    },
            ) { index ->
                val item = get(index)
                val mode = LocalEffectiveTimelineDisplayMode.current
                if (mode == TimelineDisplayMode.Gallery) {
                    GalleryTimelineItem(item = item)
                } else {
                    val isReplyInDetail = detailStatusKey != null && index >= 1
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (detailStatusKey != null && index == 1) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = screenHorizontalPadding, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                PlatformText(
                                    text = "Replies & Discussion",
                                    style = PlatformTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                                    color = PlatformTheme.colorScheme.caption,
                                )
                                HorizontalDivider(
                                    color = PlatformTheme.colorScheme.outline.copy(alpha = 0.20f),
                                    thickness = 0.6.dp,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        AdaptiveCard(
                            modifier = Modifier,
                            index = index,
                            totalCount = itemCount,
                            respectTimelineMode = true,
                        ) {
                            SwipeToActPost(item = item) {
                                StatusItem(
                                    item,
                                    detailStatusKey = detailStatusKey,
                                    hasParentConnector = isReplyInDetail,
                                    hasChildConnector = isReplyInDetail && index < itemCount - 1,
                                )
                            }
                        }
                    }
                }
            }
            appendStateUI(this@onSuccess)
        }
        onError {
            item(
                span = StaggeredGridItemSpan.FullLine,
            ) {
                OnError(
                    modifier = Modifier.fillMaxSize().animateItem(),
                    error = it,
                    onRetry = onRetry,
                )
            }
        }
        onLoading {
            statusLoadingPlaceholders()
        }
        onEmpty {
            item(
                span = StaggeredGridItemSpan.FullLine,
            ) {
                ListEmptyView(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .animateItem()
                            .clickable {
                                refresh()
                            },
                )
            }
        }
    }

public fun LazyStaggeredGridScope.statusLoadingPlaceholders() {
    val placeholderCount = 10
    items(placeholderCount) { index ->
        AdaptiveCard(
            index = index,
            totalCount = placeholderCount,
            respectTimelineMode = true,
            content = {
                OnLoading()
            },
        )
    }
}

public fun <T : Any> LazyStaggeredGridScope.appendStateUI(success: PagingState.Success<T>) {
    success.appendState
        .onError {
            item(
                span = StaggeredGridItemSpan.FullLine,
            ) {
                OnError(
//                            modifier = Modifier.animateItem(),
                    error = it,
                    onRetry = { success.retry() },
                )
            }
        }.onLoading {
            item(
                span = StaggeredGridItemSpan.FullLine,
            ) {
                PlatformLinearProgressIndicator(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = screenHorizontalPadding, vertical = 8.dp),
                )
            }
        }.onEndOfList {
            item(
                span = StaggeredGridItemSpan.FullLine,
            ) {
                Column(
                    modifier =
                        Modifier
//                                    .animateItem()
                            .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    PlatformText(
                        text = stringResource(Res.string.status_loadmore_end),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
}

@Composable
public fun ListEmptyView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FAIcon(
            imageVector = FontAwesomeIcons.Solid.File,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
        PlatformText(
            text = stringResource(resource = Res.string.status_empty),
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun OnLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
    ) {
        StatusPlaceholder(
            modifier =
                Modifier
                    .padding(
                        horizontal = screenHorizontalPadding,
                        vertical = 8.dp,
                    ),
        )
    }
}

@Composable
private fun OnError(
    error: Throwable,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ErrorContent(
        error = error,
        onRetry = onRetry,
        modifier = modifier,
    )
}

@Composable
public fun StatusItem(
    item: UiTimelineV2?,
    modifier: Modifier = Modifier,
    detailStatusKey: MicroBlogKey? = null,
    hasParentConnector: Boolean = false,
    hasChildConnector: Boolean = false,
) {
    if (item == null) {
        Column(
            modifier =
                modifier.padding(
                    horizontal = screenHorizontalPadding,
                    vertical = 8.dp,
                ),
        ) {
            StatusPlaceholder()
        }
    } else {
        UiTimelineComponent(
            item = item,
            detailStatusKey = detailStatusKey,
            hasParentConnector = hasParentConnector,
            hasChildConnector = hasChildConnector,
            modifier = modifier,
        )
    }
}

@Composable
public fun StatusPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
    ) {
        Spacer(modifier = Modifier.height(4.dp))
        UserPlaceholder()
        Spacer(modifier = Modifier.height(8.dp))
        PlatformText(
            text =
                "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Donec euismod, nisl eget ultricies" +
                    " ultrices, nisl nisl aliquet nisl, nec aliquam nisl nisl nec.",
            modifier =
                Modifier
                    .placeholder(true),
        )
    }
}

@Composable
public fun UserPlaceholder(modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .placeholder(true),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            PlatformText(
                text = "Placeholder",
                modifier =
                    Modifier
                        .placeholder(true),
            )
            Spacer(modifier = Modifier.height(4.dp))
            PlatformText(
                text = "username@Placeholder",
                style = PlatformTheme.typography.caption,
                color = PlatformTheme.colorScheme.caption,
                modifier =
                    Modifier
                        .placeholder(true),
            )
        }
    }
}
