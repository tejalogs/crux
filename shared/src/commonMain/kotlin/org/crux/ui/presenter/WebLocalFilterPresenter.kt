package org.crux.ui.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.crux.data.repository.LocalFilterRepository
import org.crux.di.koinInject
import org.crux.ui.model.UiState
import org.crux.ui.model.collectAsUiState
import org.crux.ui.model.map
import org.crux.web.shared.WebPresenter

@WebPresenter("localFilter")
public class WebLocalFilterPresenter : PresenterBase<WebLocalFilterPresenter.State>() {
    private val repository by koinInject<LocalFilterRepository>()

    @Immutable
    public interface State {
        public val items: UiState<ImmutableList<WebKeywordFilter>>

        public fun add(
            keyword: String,
            forTimeline: Boolean,
            forNotification: Boolean,
            forSearch: Boolean,
            isRegex: Boolean,
        )

        public fun update(
            keyword: String,
            forTimeline: Boolean,
            forNotification: Boolean,
            forSearch: Boolean,
            isRegex: Boolean,
        )

        public fun delete(keyword: String)
    }

    @Composable
    override fun body(): State {
        val all by remember { repository.getAllFlow() }.collectAsUiState()
        return object : State {
            override val items: UiState<ImmutableList<WebKeywordFilter>> =
                all.map { items ->
                    items
                        .map {
                            WebKeywordFilter(
                                keyword = it.keyword,
                                forTimeline = it.forTimeline,
                                forNotification = it.forNotification,
                                forSearch = it.forSearch,
                                isRegex = it.isRegex,
                            )
                        }.toImmutableList()
                }

            override fun add(
                keyword: String,
                forTimeline: Boolean,
                forNotification: Boolean,
                forSearch: Boolean,
                isRegex: Boolean,
            ) {
                repository.add(
                    keyword = keyword.trim(),
                    forTimeline = forTimeline,
                    forNotification = forNotification,
                    forSearch = forSearch,
                    expiredAt = null,
                    isRegex = isRegex,
                )
            }

            override fun update(
                keyword: String,
                forTimeline: Boolean,
                forNotification: Boolean,
                forSearch: Boolean,
                isRegex: Boolean,
            ) {
                repository.update(
                    keyword = keyword.trim(),
                    forTimeline = forTimeline,
                    forNotification = forNotification,
                    forSearch = forSearch,
                    expiredAt = null,
                    isRegex = isRegex,
                )
            }

            override fun delete(keyword: String) {
                repository.delete(keyword)
            }
        }
    }
}

@Immutable
public data class WebKeywordFilter(
    val keyword: String,
    val forTimeline: Boolean,
    val forNotification: Boolean,
    val forSearch: Boolean,
    val isRegex: Boolean,
)
