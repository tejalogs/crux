package org.crux.ui.presenter

import androidx.compose.runtime.Composable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.crux.ui.model.UiState

/**
 * Crux has one top-level destination: Home (its feed pills). Discover and Notifications were
 * built on native API accounts, which no longer exist.
 */
public class HomeTabsPresenter : PresenterBase<HomeTabsPresenter.State>() {
    public interface State {
        public val tabs: UiState<ImmutableList<HomeTabs>>

        public enum class HomeTabs {
            Home,
        }
    }

    @Composable
    override fun body(): State =
        object : State {
            override val tabs: UiState<ImmutableList<State.HomeTabs>> = UiState.Success(persistentListOf(State.HomeTabs.Home))
        }
}
