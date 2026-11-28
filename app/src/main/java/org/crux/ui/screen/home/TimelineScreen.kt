package org.crux.ui.screen.home

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Sliders
import moe.tlaster.precompose.molecule.producePresenter
import org.crux.R
import org.crux.data.model.BottomBarBehavior
import org.crux.data.model.tab.UiTimelineTabItem
import org.crux.ui.component.BackButton
import org.crux.ui.component.CruxLargeFlexibleTopAppBar
import org.crux.ui.component.CruxScaffold
import org.crux.ui.component.CruxTopAppBar
import org.crux.ui.component.FAIcon
import org.crux.ui.component.LocalGlobalAppearance
import org.crux.ui.component.TabIcon
import org.crux.ui.model.onSuccess
import org.crux.ui.presenter.home.HomeTabItemPresenter
import org.crux.ui.presenter.invoke
import org.crux.ui.route.Route
import org.crux.ui.screen.rss.NativeRssFeedList
import org.crux.ui.screen.rss.NativeRssTabTarget
import org.crux.ui.screen.rss.nativeRssTabTargetForId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeckTimelineScreen(
    id: String,
    toQuickMenu: () -> Unit,
    toLogin: () -> Unit,
    toTabSettings: () -> Unit,
    onNavigate: (Route) -> Unit = {},
) {
    val state by producePresenter("deck_timeline_$id") {
        val changeLogState = changeLogPresenter()
        val tabState = remember { HomeTabItemPresenter(id = id) }.invoke()
        object : ChangeLogState by changeLogState, HomeTabItemPresenter.State by tabState {}
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
                        org.crux.ui.component.Text(text = tabItem.title)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = toQuickMenu) {
                        state.tabItem.onSuccess { tabItem -> TabIcon(tabItem) }
                    }
                },
                scrollBehavior = topAppBarScrollBehavior,
                actions = {
                    IconButton(onClick = toTabSettings) {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.Sliders,
                            contentDescription = stringResource(R.string.edit_tab_title),
                        )
                    }
                },
            )
        },
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) { contentPadding ->
        state.tabItem.onSuccess { tabItem ->
            val site = tabItem.liveWebSite()
            if (site != null) {
                LiveWebTab(
                    site = site,
                    isCurrentlyVisible = true,
                    contentPadding = contentPadding,
                )
            } else {
                val rssTarget = nativeRssTabTargetForId(tabItem.id)
                NativeRssFeedList(
                    feedUrl = (rssTarget as? NativeRssTabTarget.SingleFeed)?.feedUrl,
                    contentPadding = contentPadding,
                    onOpenArticle = { detail -> onNavigate(detail) },
                    onManageFeeds = { onNavigate(Route.Rss.Sources) },
                    onAddFeed = { onNavigate(Route.Rss.Create()) },
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
    onNavigate: (Route) -> Unit = {},
) {
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    CruxScaffold(
        topBar = {
            CruxLargeFlexibleTopAppBar(
                title = { org.crux.ui.component.Text(tabItem.title) },
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = { if (onBack != null) BackButton(onBack) },
            )
        },
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) { contentPadding ->
        val site = tabItem.liveWebSite()
        if (site != null) {
            LiveWebTab(
                site = site,
                isCurrentlyVisible = true,
                contentPadding = contentPadding,
            )
        } else {
            val rssTarget = nativeRssTabTargetForId(tabItem.id)
            NativeRssFeedList(
                feedUrl = (rssTarget as? NativeRssTabTarget.SingleFeed)?.feedUrl,
                contentPadding = contentPadding,
                onOpenArticle = { detail -> onNavigate(detail) },
                onManageFeeds = { onNavigate(Route.Rss.Sources) },
                onAddFeed = { onNavigate(Route.Rss.Create()) },
            )
        }
    }
}
