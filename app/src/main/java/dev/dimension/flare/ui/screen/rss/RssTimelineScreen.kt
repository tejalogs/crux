package dev.dimension.flare.ui.screen.rss

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
import dev.dimension.flare.common.isRefreshing
import dev.dimension.flare.data.model.BottomBarBehavior
import dev.dimension.flare.data.model.TimelineDisplayMode
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.component.LocalGlobalAppearance
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.rss.RssSourcePresenter
import dev.dimension.flare.ui.presenter.invoke
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter

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
                url.contains("pixiv", ignoreCase = true) ||
                url.contains("flickr", ignoreCase = true) ||
                url.contains("unsplash", ignoreCase = true)
        }
    val currentAppearance = LocalTimelineAppearance.current
    var isGalleryOverride by remember(id, isPhotoCentric) {
        mutableStateOf(isPhotoCentric || currentAppearance.timelineDisplayMode == TimelineDisplayMode.Gallery)
    }

    FlareScaffold(
        topBar = {
            FlareTopAppBar(
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
