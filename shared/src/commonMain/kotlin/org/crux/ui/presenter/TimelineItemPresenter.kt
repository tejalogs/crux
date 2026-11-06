package org.crux.ui.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.crux.common.PagingState
import org.crux.common.isRefreshing
import org.crux.data.model.tab.TimelinePresenterFactory
import org.crux.data.model.tab.TimelineResolver
import org.crux.data.model.tab.UiTimelineTabItem
import org.crux.di.koinInject
import org.crux.ui.model.UiTimelineV2
import org.crux.web.shared.WebPresenter

public class TimelineItemPresenter(
    private val timelineTabItem: UiTimelineTabItem,
    private val isHomeTimeline: Boolean = false,
) : PresenterBase<TimelineItemPresenter.State>() {
    private val timelinePresenterFactory by koinInject<TimelinePresenterFactory>()

    public interface State {
        public val listState: PagingState<UiTimelineV2>

        public fun refreshSync()

        public suspend fun refreshSuspend()

        public val isRefreshing: Boolean
    }

    private val timelinePresenter by lazy {
        timelinePresenterFactory.create(timelineTabItem, isHomeTimeline)
    }

    @Composable
    override fun body(): State {
        val state = timelinePresenter.body()
        val scope = rememberCoroutineScope()
        return object : State {
            override val listState = state.listState
            override val isRefreshing = listState.isRefreshing

            override fun refreshSync() {
                scope.launch {
                    state.refresh()
                }
            }

            override suspend fun refreshSuspend() {
                state.refresh()
            }
        }
    }
}

@WebPresenter("timelineItem")
public class WebTimelineItemPresenter(
    private val loaderKey: String,
) : PresenterBase<TimelineItemPresenter.State>() {
    private val timelineResolver by koinInject<TimelineResolver>()

    private val delegate by lazy {
        TimelineItemPresenter(timelineResolver.toTabItem(loaderKey))
    }

    @Composable
    override fun body(): TimelineItemPresenter.State = delegate.body()
}
