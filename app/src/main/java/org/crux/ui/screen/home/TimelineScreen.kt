package org.crux.ui.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Sliders
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.crux.R
import org.crux.data.model.BottomBarBehavior
import org.crux.data.model.tab.UiTimelineTabItem
import org.crux.data.model.tab.resolveTimelineAppearance
import org.crux.ui.component.BackButton
import org.crux.ui.component.CruxLargeFlexibleTopAppBar
import org.crux.ui.component.CruxScaffold
import org.crux.ui.component.CruxTopAppBar
import org.crux.ui.component.FAIcon
import org.crux.ui.component.LocalGlobalAppearance
import org.crux.ui.component.LocalTimelineAppearance
import org.crux.ui.component.TabIcon
import org.crux.ui.model.onSuccess
import org.crux.ui.model.takeSuccess
import org.crux.ui.presenter.home.HomeTabItemPresenter
import org.crux.ui.presenter.home.LoggedInPresenter
import org.crux.ui.presenter.home.LoggedInState
import org.crux.ui.presenter.invoke

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeckTimelineScreen(
//    tabItem: UiTimelineTabItem,
    id: String,
    toQuickMenu: () -> Unit,
    toLogin: () -> Unit,
    toTabSettings: () -> Unit,
) {
    val state by producePresenter("deck_timeline_$id") {
        val loginState = remember { LoggedInPresenter() }.invoke()
        val changeLogState = changeLogPresenter()
        val tabState = remember { HomeTabItemPresenter(id = id) }.invoke()
        object : LoggedInState by loginState, ChangeLogState by changeLogState, HomeTabItemPresenter.State by tabState {
        }
    }
    val topAppBarScrollBehavior =
        if (LocalGlobalAppearance.current.bottomBarBehavior == BottomBarBehavior.AlwaysShow) {
            TopAppBarDefaults.pinnedScrollBehavior()
        } else {
            TopAppBarDefaults.enterAlwaysScrollBehavior()
        }
    CruxScaffold(
        topBar = {
            CruxTopAppBar(
                title = {
                    state.tabItem.onSuccess { tabItem ->
                        org.crux.ui.component
                            .Text(text = tabItem.title)
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            toQuickMenu.invoke()
                        },
                    ) {
                        state.tabItem.onSuccess { tabItem ->
                            TabIcon(tabItem)
                        }
                    }
                },
                scrollBehavior = topAppBarScrollBehavior,
                actions = {
                    IconButton(
                        onClick = {
                            toTabSettings.invoke()
                        },
                    ) {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.Sliders,
                            contentDescription = stringResource(R.string.edit_tab_title),
                        )
                    }
                    if (state.isLoggedIn.takeSuccess() == false) {
                        TextButton(onClick = toLogin) {
                            Text(text = stringResource(id = R.string.login_button))
                        }
                    }
                },
            )
        },
        modifier =
            Modifier
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) { contentPadding ->
        state.tabItem.onSuccess { tabItem ->
            val timelineAppearance = LocalTimelineAppearance.current
            CompositionLocalProvider(
                LocalTimelineAppearance provides
                    remember(
                        tabItem.appearancePatch,
                        timelineAppearance,
                    ) {
                        tabItem.resolveTimelineAppearance(timelineAppearance)
                    },
            ) {
                TimelineItemContent(
                    item = tabItem,
                    contentPadding = contentPadding,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimelineScreen(
    tabItem: UiTimelineTabItem,
    onBack: (() -> Unit)?,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyStaggeredGridState()
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    CruxScaffold(
        topBar = {
            CruxLargeFlexibleTopAppBar(
                title = {
                    org.crux.ui.component
                        .Text(tabItem.title)
                },
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    if (onBack != null) {
                        BackButton(onBack)
                    }
                },
                modifier =
                    Modifier
                        .clickable(
                            onClick = {
                                scope.launch {
                                    listState.animateScrollToItem(0)
                                }
                            },
                            indication = null,
                            interactionSource =
                                remember {
                                    androidx.compose.foundation.interaction
                                        .MutableInteractionSource()
                                },
                        ),
            )
        },
        modifier =
            Modifier
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) { contentPadding ->

        val timelineAppearance = LocalTimelineAppearance.current
        CompositionLocalProvider(
            LocalTimelineAppearance provides
                remember(
                    tabItem.appearancePatch,
                    timelineAppearance,
                ) {
                    tabItem.resolveTimelineAppearance(timelineAppearance)
                },
        ) {
            TimelineItemContent(
                item = tabItem,
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxSize(),
                lazyStaggeredGridState = listState,
            )
        }
    }
}
