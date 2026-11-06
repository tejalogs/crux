package org.crux.ui.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import org.crux.ui.presenter.home.ActiveAccountPresenter
import org.crux.ui.presenter.home.UserState
import org.crux.web.shared.WebPresenter

@WebPresenter("homeTimelineWithTabs")
public class HomeTimelineWithTabsPresenter : PresenterBase<HomeTimelineWithTabsPresenter.State>() {
    private val settingsRepository by koinInject<SettingsRepository>()

    public interface State : UserState {
        public val tabState: UiState<ImmutableList<UiTimelineTabItem>>

        public fun resolveAppearance(
            tab: UiTimelineTabItem,
            base: TimelineAppearance,
        ): TimelineAppearance
    }

    // Crux has no guest platform: with zero accounts the home tabs are just the saved tabs
    // (live web tabs and RSS), which need no account.
    private val tabsState by lazy {
        settingsRepository.homeTimelineTabs.map { tabs -> tabs.toImmutableList() }
    }

    @Composable
    override fun body(): State {
        val accountState =
            remember {
                ActiveAccountPresenter()
            }.body()

        val tabs by tabsState.collectAsUiState()

        return object : State, UserState by accountState {
            override val tabState = tabs

            override fun resolveAppearance(
                tab: UiTimelineTabItem,
                base: TimelineAppearance,
            ): TimelineAppearance = tab.resolveTimelineAppearance(base)
        }
    }
}
