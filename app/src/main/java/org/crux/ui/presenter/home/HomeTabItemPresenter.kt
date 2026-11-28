package org.crux.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.map
import org.crux.data.model.tab.UiTimelineTabItem
import org.crux.data.repository.SettingsRepository
import org.crux.di.koinInject
import org.crux.ui.model.UiState
import org.crux.ui.model.flattenUiState
import org.crux.ui.presenter.PresenterBase

public class HomeTabItemPresenter(
    private val id: String,
) : PresenterBase<HomeTabItemPresenter.State>() {
    private val settingsRepository: SettingsRepository by koinInject()

    public interface State {
        public val tabItem: UiState<UiTimelineTabItem>
    }

    @Composable
    override fun body(): State {
        val tabItem by
            remember(id) {
                settingsRepository.homeTimelineTab(id).map {
                    if (it == null) {
                        UiState.Error<UiTimelineTabItem>(Exception("Tab not found"))
                    } else {
                        UiState.Success(it)
                    }
                }
            }.flattenUiState()

        return object : State {
            override val tabItem = tabItem
        }
    }
}
