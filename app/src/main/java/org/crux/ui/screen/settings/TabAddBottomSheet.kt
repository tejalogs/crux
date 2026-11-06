package org.crux.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationConstants
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Bars
import compose.icons.fontawesomeicons.solid.CircleMinus
import compose.icons.fontawesomeicons.solid.CirclePlus
import compose.icons.fontawesomeicons.solid.Pen
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.Trash
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.crux.R
import org.crux.data.model.tab.UiTimelineTabItem
import org.crux.ui.common.itemsIndexed
import org.crux.ui.component.AvatarComponent
import org.crux.ui.component.FAIcon
import org.crux.ui.component.RichText
import org.crux.ui.component.TabIcon
import org.crux.ui.component.TabRowIndicator
import org.crux.ui.component.listCard
import org.crux.ui.model.asText
import org.crux.ui.model.onSuccess
import org.crux.ui.theme.screenHorizontalPadding
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import org.crux.ui.component.Text as UiText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TabAddBottomSheet(
    tabs: ImmutableList<UiTimelineTabItem>,
    allTabs: AllTabsPresenter.State,
    onDismissRequest: () -> Unit,
    onAddTab: (UiTimelineTabItem) -> Unit,
    onDeleteTab: (String) -> Unit,
    toAddRssSource: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
    ) {
        @Composable
        fun TabItem(
            tabItem: UiTimelineTabItem,
            modifier: Modifier = Modifier,
        ) {
            ListTabItem(
                data = tabItem,
                isAdded = tabs.any { tab -> tabItem.id == tab.id },
                modifier =
                    modifier.clickable {
                        if (tabs.any { tab -> tabItem.id == tab.id }) {
                            onDeleteTab(tabItem.id)
                        } else {
                            onAddTab(tabItem)
                        }
                    },
            )
        }
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            allTabs.accountTabs.onSuccess { accountTabGroups ->
                val pagerState =
                    rememberPagerState {
                        accountTabGroups.size + 1
                    }
                val scope = rememberCoroutineScope()
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = screenHorizontalPadding),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    item {
                        val isSelected = pagerState.currentPage == 0
                        ToggleButton(
                            checked = isSelected,
                            onCheckedChange = {
                                scope.launch {
                                    pagerState.animateScrollToPage(0)
                                }
                            },
                            colors =
                                ToggleButtonDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    checkedContainerColor = MaterialTheme.colorScheme.primary,
                                    checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = stringResource(id = R.string.rss_title),
                                style =
                                    MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    ),
                            )
                        }
                    }
                    itemsIndexed(accountTabGroups) { index, tab ->
                        val isSelected = pagerState.currentPage == index + 1
                        ToggleButton(
                            checked = isSelected,
                            onCheckedChange = {
                                scope.launch {
                                    pagerState.animateScrollToPage(index + 1)
                                }
                            },
                            colors =
                                ToggleButtonDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    checkedContainerColor = MaterialTheme.colorScheme.primary,
                                    checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            AvatarComponent(
                                tab.profile.avatar,
                                size = 20.dp,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = tab.profile.handle.canonical,
                                style =
                                    MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxHeight(),
                    verticalAlignment = Alignment.Top,
                ) {
                    if (it == 0) {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = screenHorizontalPadding),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            itemsIndexed(
                                allTabs.rssTabs,
                            ) { index, it ->
                                TabItem(
                                    it,
                                    modifier =
                                        Modifier
                                            .listCard(
                                                index = index,
                                                totalCount = allTabs.rssTabs.size,
                                            ),
                                )
                            }
                            if (allTabs.rssTabs.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }
                            item {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Button(
                                        onClick = toAddRssSource,
                                    ) {
                                        FAIcon(
                                            FontAwesomeIcons.Solid.Plus,
                                            contentDescription = stringResource(R.string.add_rss_source),
                                        )
                                        Text(stringResource(R.string.add_rss_source))
                                    }
                                }
                            }
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val accountTabs = accountTabGroups[it - 1]
                            var selectedIndex by remember { mutableIntStateOf(0) }
                            if (accountTabs.tabs.size > 1) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    accountTabs.tabs.forEachIndexed { index, section ->
                                        FilterChip(
                                            selected = selectedIndex == index,
                                            onClick = { selectedIndex = index },
                                            label = {
                                                UiText(section.title.asText())
                                            },
                                        )
                                    }
                                }
                            }
                            when (selectedIndex) {
                                in accountTabs.tabs.indices -> {
                                    val section = accountTabs.tabs[selectedIndex]
                                    LazyColumn(
                                        contentPadding = PaddingValues(horizontal = screenHorizontalPadding),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        itemsIndexed(section.data) { index, totalCount, item ->
                                            TabItem(
                                                item,
                                                modifier =
                                                    Modifier
                                                        .listCard(
                                                            index = index,
                                                            totalCount = totalCount,
                                                        ),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ListTabItem(
    data: UiTimelineTabItem,
    isAdded: Boolean,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier,
        leadingContent = {
            TabIcon(data)
        },
        trailingContent = {
            if (isAdded) {
                FAIcon(
                    FontAwesomeIcons.Solid.CircleMinus,
                    contentDescription = stringResource(id = R.string.tab_settings_remove),
                )
            } else {
                FAIcon(
                    FontAwesomeIcons.Solid.CirclePlus,
                    contentDescription = stringResource(id = R.string.tab_settings_add),
                )
            }
        },
        elevation = ListItemDefaults.elevation(),
        content = {
            UiText(data.title)
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LazyItemScope.TabCustomItem(
    item: UiTimelineTabItem,
    isEditing: Boolean,
    deleteTab: (UiTimelineTabItem) -> Unit,
    editTab: (UiTimelineTabItem) -> Unit,
    reorderableLazyColumnState: ReorderableLazyListState,
    canSwipeToDelete: Boolean,
    modifier: Modifier = Modifier,
    shapes: ListItemShapes = ListItemDefaults.shapes(),
) {
    val haptics = LocalHapticFeedback.current
    val swipeState =
        rememberSwipeToDismissBoxState()
    LaunchedEffect(swipeState.settledValue) {
        if (swipeState.settledValue != SwipeToDismissBoxValue.Settled) {
            delay(AnimationConstants.DefaultDurationMillis.toLong())
            swipeState.reset()
            deleteTab(item)
        }
    }
    ReorderableItem(reorderableLazyColumnState, key = item.id, modifier = modifier) { isDragging ->
        AnimatedVisibility(
            visible = swipeState.settledValue == SwipeToDismissBoxValue.Settled,
            exit =
                shrinkVertically(
                    animationSpec = tween(),
                    shrinkTowards = Alignment.Top,
                ) + fadeOut(),
        ) {
            val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp)
            val isSwiping =
                swipeState.dismissDirection != SwipeToDismissBoxValue.Settled
            SwipeToDismissBox(
                state = swipeState,
                enableDismissFromEndToStart = canSwipeToDelete,
                enableDismissFromStartToEnd = canSwipeToDelete,
                backgroundContent = {
                    val alignment =
                        when (swipeState.dismissDirection) {
                            SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                            SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                            SwipeToDismissBoxValue.Settled -> Alignment.Center
                        }
                    if (swipeState.dismissDirection != SwipeToDismissBoxValue.Settled) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.error, shape = shapes.draggedShape)
                                    .padding(16.dp),
                        ) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.Trash,
                                contentDescription = stringResource(id = R.string.tab_settings_remove),
                                modifier =
                                    Modifier
                                        .align(alignment),
                                tint = MaterialTheme.colorScheme.onError,
                            )
                        }
                    }
                },
            ) {
                SegmentedListItem(
                    elevation = ListItemDefaults.elevation(elevation),
                    selected = isDragging || isSwiping || isEditing,
                    onClick = {},
                    shapes = shapes,
                    content = {
                        UiText(item.title)
                    },
                    leadingContent = {
                        TabIcon(
                            item,
                        )
                    },
                    trailingContent = {
                        Row {
                            IconButton(
                                onClick = {
                                    editTab.invoke(item)
                                },
                            ) {
                                FAIcon(
                                    FontAwesomeIcons.Solid.Pen,
                                    contentDescription = stringResource(id = R.string.tab_settings_edit),
                                )
                            }
                            IconButton(
                                modifier =
                                    Modifier.draggableHandle(
                                        onDragStarted = {
                                            haptics.performHapticFeedback(
                                                HapticFeedbackType.Confirm,
                                            )
                                        },
                                        onDragStopped = {
                                            haptics.performHapticFeedback(
                                                HapticFeedbackType.Confirm,
                                            )
                                        },
                                    ),
                                onClick = {},
                            ) {
                                FAIcon(
                                    FontAwesomeIcons.Solid.Bars,
                                    contentDescription = stringResource(id = R.string.tab_settings_drag),
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}
