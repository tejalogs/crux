package org.crux.ui.presenter.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.collections.immutable.ImmutableList
import org.crux.data.repository.LocalFilterRepository
import org.crux.di.koinInject
import org.crux.ui.model.UiKeywordFilter
import org.crux.ui.model.UiState
import org.crux.ui.model.collectAsUiState
import org.crux.ui.presenter.PresenterBase

public class LocalFilterPresenter : PresenterBase<LocalFilterState>() {
    private val repository by koinInject<LocalFilterRepository>()

    @Composable
    override fun body(): LocalFilterState {
        val all by remember { repository.getAllFlow() }.collectAsUiState()
        return object : LocalFilterState {
            override val items = all

            override fun add(item: UiKeywordFilter) {
                repository.add(
                    keyword = item.keyword,
                    forTimeline = item.forTimeline,
                    forNotification = item.forNotification,
                    forSearch = item.forSearch,
                    expiredAt = item.expiredAt,
                    isRegex = item.isRegex,
                )
            }

            override fun delete(keyword: String) {
                repository.delete(keyword)
            }

            override fun update(item: UiKeywordFilter) {
                repository.update(
                    keyword = item.keyword,
                    forTimeline = item.forTimeline,
                    forNotification = item.forNotification,
                    forSearch = item.forSearch,
                    expiredAt = item.expiredAt,
                    isRegex = item.isRegex,
                )
            }
        }
    }
}

@Immutable
public interface LocalFilterState {
    public val items: UiState<ImmutableList<UiKeywordFilter>>

    public fun delete(keyword: String)

    public fun add(item: UiKeywordFilter)

    public fun update(item: UiKeywordFilter)
}
