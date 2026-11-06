package org.crux.ui.presenter.gallery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import org.crux.common.PagingState
import org.crux.common.emptyFlow
import org.crux.common.toPagingState
import org.crux.data.datasource.microblog.datasource.GalleryDataSource
import org.crux.data.datasource.microblog.datasource.GalleryDetail
import org.crux.data.datasource.microblog.paging.toTimelinePagingSource
import org.crux.data.datasource.microblog.pagingConfig
import org.crux.data.repository.AccountRepository
import org.crux.data.repository.accountServiceFlow
import org.crux.di.koinInject
import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiState
import org.crux.ui.model.UiTimelineV2
import org.crux.ui.model.flattenUiState
import org.crux.ui.model.toUi
import org.crux.ui.presenter.PresenterBase
import org.crux.ui.presenter.status.LogStatusHistoryPresenter

@OptIn(ExperimentalCoroutinesApi::class)
public class GalleryDetailPresenter(
    private val accountType: AccountType,
    private val statusKey: MicroBlogKey,
) : PresenterBase<GalleryDetailPresenter.State>() {
    private val accountRepository: AccountRepository by koinInject()

    private val serviceFlow by lazy {
        accountServiceFlow(
            accountType = accountType,
            repository = accountRepository,
        )
    }

    private val detailCacheFlow by lazy {
        serviceFlow.flatMapLatest { service ->
            val galleryDataSource =
                service as? GalleryDataSource
                    ?: error("Current service does not support gallery data source")
            galleryDataSource.galleryDetail(statusKey).toUi()
        }
    }

    private val commentsFlow by lazy {
        serviceFlow.flatMapLatest { service ->
            runCatching {
                val galleryDataSource =
                    service as? GalleryDataSource
                        ?: error("Current service does not support gallery data source")
                Pager(config = pagingConfig) {
                    galleryDataSource.galleryComments(statusKey).toTimelinePagingSource()
                }.flow
            }.getOrElse {
                PagingData.emptyFlow(isError = true)
            }
        }
    }

    private val recommendationsFlow by lazy {
        serviceFlow.flatMapLatest { service ->
            runCatching {
                val galleryDataSource =
                    service as? GalleryDataSource
                        ?: error("Current service does not support gallery data source")
                Pager(config = pagingConfig) {
                    galleryDataSource.galleryRecommendations(statusKey).toTimelinePagingSource()
                }.flow
            }.getOrElse {
                PagingData.emptyFlow(isError = true)
            }
        }
    }

    @Immutable
    public interface State {
        public val detail: UiState<GalleryDetail>
        public val comments: PagingState<UiTimelineV2>
        public val recommendations: PagingState<UiTimelineV2>
    }

    @Composable
    override fun body(): State {
        val scope = rememberCoroutineScope()
        val detail by detailCacheFlow.flattenUiState()
        val comments =
            remember(commentsFlow, scope) {
                commentsFlow.cachedIn(scope)
            }.collectAsLazyPagingItems()
                .toPagingState()
        val recommendations =
            remember(recommendationsFlow, scope) {
                recommendationsFlow.cachedIn(scope)
            }.collectAsLazyPagingItems()
                .toPagingState()

        remember { LogStatusHistoryPresenter(accountType = accountType, statusKey = statusKey) }.body()

        return object : State {
            override val detail: UiState<GalleryDetail> = detail
            override val comments: PagingState<UiTimelineV2> = comments
            override val recommendations: PagingState<UiTimelineV2> = recommendations
        }
    }
}
