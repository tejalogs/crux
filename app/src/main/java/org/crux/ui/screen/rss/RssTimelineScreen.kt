package org.crux.ui.screen.rss

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.TableCells
import compose.icons.fontawesomeicons.solid.TableList
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.crux.common.isRefreshing
import org.crux.data.model.BottomBarBehavior
import org.crux.data.model.TimelineDisplayMode
import org.crux.ui.component.BackButton
import org.crux.ui.component.CruxScaffold
import org.crux.ui.component.CruxTopAppBar
import org.crux.ui.component.FAIcon
import org.crux.ui.component.LocalGlobalAppearance
import org.crux.ui.component.LocalTimelineAppearance
import org.crux.ui.component.RefreshContainer
import org.crux.ui.component.status.LazyStatusVerticalStaggeredGrid
import org.crux.ui.component.status.status
import org.crux.ui.model.onSuccess
import org.crux.ui.presenter.home.rss.RssSourcePresenter
import org.crux.ui.presenter.invoke

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RssTimelineScreen(
    id: Int,
    url: String,
    title: String?,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val state by producePresenter("rss_timeline_$id") { presenter(id) }
    val topAppBarScrollBehavior =
        if (LocalGlobalAppearance.current.bottomBarBehavior == BottomBarBehavior.AlwaysShow) {
            TopAppBarDefaults.pinnedScrollBehavior()
        } else {
            TopAppBarDefaults.enterAlwaysScrollBehavior()
        }

    val isPhotoCentric =
        remember(url) {
            url.contains("instagram", ignoreCase = true) ||
                url.contains("pixelfed", ignoreCase = true) ||
                url.contains("flickr", ignoreCase = true) ||
                url.contains("unsplash", ignoreCase = true)
        }
    val currentAppearance = LocalTimelineAppearance.current
    var isGalleryOverride by remember(id, isPhotoCentric) {
        mutableStateOf(isPhotoCentric || currentAppearance.timelineDisplayMode == TimelineDisplayMode.Gallery)
    }

    CruxScaffold(
        topBar = {
            CruxTopAppBar(
                scrollBehavior = topAppBarScrollBehavior,
                title = {
                    state.data.onSuccess {
                        Text(text = it.title ?: it.host)
                    }
                },
                navigationIcon = {
                    BackButton(onBack)
                },
                actions = {
                    IconButton(
                        onClick = { isGalleryOverride = !isGalleryOverride },
                    ) {
                        FAIcon(
                            imageVector =
                                if (isGalleryOverride) {
                                    FontAwesomeIcons.Solid.TableList
                                } else {
                                    FontAwesomeIcons.Solid.TableCells
                                },
                            contentDescription =
                                if (isGalleryOverride) {
                                    "Switch to list view"
                                } else {
                                    "Switch to photo grid"
                                },
                        )
                    }
                },
            )
        },
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) { contentPadding ->
        state.timelineState.onSuccess { state ->
            RefreshContainer(
                onRefresh = {
                    scope.launch {
                        state.refresh()
                    }
                },
                isRefreshing = state.listState.isRefreshing,
                indicatorPadding = contentPadding,
                content = {
                    val effectiveDisplayMode =
                        if (isGalleryOverride) {
                            TimelineDisplayMode.Gallery
                        } else {
                            if (currentAppearance.timelineDisplayMode == TimelineDisplayMode.Gallery) {
                                TimelineDisplayMode.Card
                            } else {
                                currentAppearance.timelineDisplayMode
                            }
                        }
                    CompositionLocalProvider(
                        LocalTimelineAppearance provides
                            currentAppearance.copy(
                                timelineDisplayMode = effectiveDisplayMode,
                            ),
                    ) {
                        LazyStatusVerticalStaggeredGrid(
                            contentPadding = contentPadding,
                            allowGalleryMode = isGalleryOverride,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            status(state.listState)
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun presenter(id: Int) =
    run {
        remember { RssSourcePresenter(id = id) }.invoke()
    }
