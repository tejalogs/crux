package org.crux.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import org.crux.common.ImmutableListWrapper
import org.crux.data.repository.SearchHistoryRepository
import org.crux.di.koinInject
import org.crux.ui.model.UiSearchHistory
import org.crux.ui.model.UiState
import org.crux.ui.model.collectAsUiState
import org.crux.ui.presenter.PresenterBase

public class SearchHistoryPresenter : PresenterBase<SearchHistoryState>() {
    private val repository: SearchHistoryRepository by koinInject()

    @Composable
    override fun body(): SearchHistoryState {
        val searchHistories by remember {
            repository.allSearchHistory
        }.collectAsUiState()

        return object : SearchHistoryState {
            override val searchHistories = searchHistories

            override fun addSearchHistory(keyword: String) {
                repository.addSearchHistory(keyword)
            }

            override fun deleteSearchHistory(keyword: String) {
                repository.deleteSearchHistory(keyword)
            }
        }
    }
}

@Immutable
public interface SearchHistoryState {
    public val searchHistories: UiState<ImmutableListWrapper<UiSearchHistory>>

    public fun addSearchHistory(keyword: String)

    public fun deleteSearchHistory(keyword: String)
}
