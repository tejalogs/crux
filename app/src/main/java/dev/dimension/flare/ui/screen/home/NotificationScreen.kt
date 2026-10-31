package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Bell
import dev.dimension.flare.R
import dev.dimension.flare.common.isRefreshing
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.model.BottomBarBehavior
import dev.dimension.flare.data.model.TimelineDisplayMode
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.component.LocalGlobalAppearance
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.platform.isCompatScreen
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.haptic.FlareHapticEvent
import dev.dimension.flare.ui.haptic.rememberFlareHapticFeedback
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.AllNotificationPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.theme.isLightTheme
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
internal fun NotificationScreen() {
    val state by producePresenter(key = "notification") {
        presenter()
    }
    val lazyListState = rememberLazyStaggeredGridState()
    RegisterTabCallback(
        lazyListState = lazyListState,
        onRefresh = {
            state.refresh()
        },
    )
    val isCompatScreen = isCompatScreen()
    val haptics = rememberFlareHapticFeedback(LocalGlobalAppearance.current.hapticsEnabled)
    val topAppBarScrollBehavior =
        if (LocalGlobalAppearance.current.bottomBarBehavior == BottomBarBehavior.AlwaysShow) {
            TopAppBarDefaults.pinnedScrollBehavior()
        } else {
            TopAppBarDefaults.enterAlwaysScrollBehavior()
        }
    val isPlain = LocalTimelineAppearance.current.timelineDisplayMode == TimelineDisplayMode.Plain && isLightTheme() && isCompatScreen
    val screenBgColor = if (isPlain) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background
    FlareScaffold(
        containerColor = screenBgColor,
        topBar = {
            if (state.notifications.size > 1) {
                FlareTopAppBar(
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = screenBgColor,
                            scrolledContainerColor = screenBgColor,
                            actionIconContentColor = MaterialTheme.colorScheme.primary,
                        ),
                    title = {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            item(key = "unified") {
                                ToggleButton(
                                    checked = state.isUnified,
                                    onCheckedChange = {
                                        haptics.perform(FlareHapticEvent.SelectionChange)
                                        state.setAllAccounts()
                                    },
                                    colors =
                                        ToggleButtonDefaults.colors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            checkedContainerColor = MaterialTheme.colorScheme.primary,
                                            checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                                        ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Box(contentAlignment = Alignment.BottomEnd) {
                                        FAIcon(
                                            imageVector = FontAwesomeIcons.Solid.Bell,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        if (state.totalBadgeCount > 0) {
                                            Badge {
                                                Text(
                                                    text = state.totalBadgeCount.toString(),
                                                    maxLines = 1,
                                                    style = MaterialTheme.typography.labelSmall,
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = stringResource(id = R.string.notification_tab_unified),
                                        maxLines = 1,
                                        style =
                                            MaterialTheme.typography.labelLarge.copy(
                                                fontWeight = if (state.isUnified) FontWeight.Bold else FontWeight.Medium,
                                            ),
                                        modifier = Modifier.padding(start = 6.dp),
                                    )
                                }
                            }
                            items(state.notifications, key = { it.profile.key.toString() }) { item ->
                                val account = item.profile
                                val badge = item.badge
                                val isSelected = state.selectedAccount?.key == account.key
                                ToggleButton(
                                    checked = isSelected,
                                    onCheckedChange = {
                                        haptics.perform(FlareHapticEvent.SelectionChange)
                                        state.setAccount(account)
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
                                    Box(contentAlignment = Alignment.BottomEnd) {
                                        AvatarComponent(
                                            account.avatar,
                                            size = 20.dp,
                                        )
                                        if (badge > 0) {
                                            Badge {
                                                Text(
                                                    text = badge.toString(),
                                                    maxLines = 1,
                                                    style = MaterialTheme.typography.labelSmall,
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = account.handle.canonical,
                                        maxLines = 1,
                                        style =
                                            MaterialTheme.typography.labelLarge.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            ),
                                        modifier = Modifier.padding(start = 6.dp),
                                    )
                                }
                            }
                        }
                    },
                    scrollBehavior = topAppBarScrollBehavior,
                    actions = {
                        if (!isCompatScreen) {
                            state.supportedNotificationFilters.onSuccess { filters ->
                                if (filters.size > 1) {
                                    NotificationFilterSelector(
                                        filters,
                                        state.selectedFilter,
                                        onFilterChanged = {
                                            state.setFilter(it)
                                        },
                                        modifier =
                                            Modifier
                                                .padding(horizontal = screenHorizontalPadding),
                                    )
                                }
                            }
                        }
                    },
                )
            } else {
                FlareLargeFlexibleTopAppBar(
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = screenBgColor,
                            scrolledContainerColor = screenBgColor,
                            actionIconContentColor = MaterialTheme.colorScheme.primary,
                        ),
                    title = {
                        Text(
                            text = stringResource(id = R.string.home_tab_notifications_title),
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        )
                    },
                    scrollBehavior = topAppBarScrollBehavior,
                    actions = {
                        if (!isCompatScreen) {
                            state.supportedNotificationFilters.onSuccess { filters ->
                                if (filters.size > 1) {
                                    NotificationFilterSelector(
                                        filters,
                                        state.selectedFilter,
                                        onFilterChanged = {
                                            state.setFilter(it)
                                        },
                                        modifier =
                                            Modifier
                                                .padding(horizontal = screenHorizontalPadding),
                                    )
                                }
                            }
                        }
                    },
                )
            }
        },
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) { contentPadding ->
        RefreshContainer(
            indicatorPadding = contentPadding,
            onRefresh = state::refresh,
            isRefreshing = state.timeline.isRefreshing,
            content = {
                LazyStatusVerticalStaggeredGrid(
                    state = lazyListState,
                    contentPadding = contentPadding,
                ) {
                    if (isCompatScreen) {
                        state.supportedNotificationFilters.onSuccess {
                            if (it.size > 1) {
                                item(
                                    span = StaggeredGridItemSpan.FullLine,
                                ) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(
                                                    horizontal = screenHorizontalPadding,
                                                    vertical = 6.dp,
                                                ),
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        NotificationFilterSelector(
                                            it,
                                            state.selectedFilter,
                                            onFilterChanged = {
                                                state.setFilter(it)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    status(state.timeline)
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NotificationFilterSelector(
    filters: ImmutableList<NotificationFilter>,
    selectedFilter: NotificationFilter?,
    onFilterChanged: (NotificationFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberFlareHapticFeedback(LocalGlobalAppearance.current.hapticsEnabled)
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(filters) { filter ->
            val isSelected = filter == selectedFilter
            ToggleButton(
                checked = isSelected,
                onCheckedChange = {
                    haptics.perform(FlareHapticEvent.SelectionChange)
                    onFilterChanged(filter)
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
                    text = stringResource(id = filter.title),
                    style =
                        MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        ),
                )
            }
        }
    }
}

private val NotificationFilter.title: Int
    get() =
        when (this) {
            NotificationFilter.All -> R.string.notification_tab_all_title
            NotificationFilter.Mention -> R.string.notification_tab_mentions_title
            NotificationFilter.Comment -> R.string.notification_tab_comments_title
            NotificationFilter.Like -> R.string.notification_tab_likes_title
        }

@Composable
private fun presenter() =
    run {
        val scope = rememberCoroutineScope()
        val state =
            remember {
                AllNotificationPresenter()
            }.invoke()
        object : AllNotificationPresenter.State by state {
            fun refresh() {
                scope.launch {
                    state.refreshSuspend()
                }
            }
        }
    }
