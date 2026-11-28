package org.crux.ui.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map
import org.crux.data.model.appearance.TimelineAppearance
import org.crux.data.model.tab.UiTimelineTabItem
import org.crux.data.model.tab.resolveTimelineAppearance
import org.crux.data.repository.SettingsRepository
import org.crux.di.koinInject
import org.crux.ui.model.UiState
import org.crux.ui.model.collectAsUiState

public class HomeTimelineWithTabsPresenter : PresenterBase<HomeTimelineWithTabsPresenter.State>() {
    private val settingsRepository by koinInject<SettingsRepository>()

    public interface State {
        public val tabState: UiState<ImmutableList<UiTimelineTabItem>>

        public fun resolveAppearance(
            tab: UiTimelineTabItem,
            base: TimelineAppearance,
        ): TimelineAppearance
    }

    private val tabsState by lazy {
        settingsRepository.homeTimelineTabs.map { tabs -> tabs.toImmutableList() }
    }

    @Composable
    override fun body(): State {
        val tabs by tabsState.collectAsUiState()

        return object : State {
            override val tabState = tabs

            override fun resolveAppearance(
                tab: UiTimelineTabItem,
                base: TimelineAppearance,
            ): TimelineAppearance = tab.resolveTimelineAppearance(base)
        }
    }
}
