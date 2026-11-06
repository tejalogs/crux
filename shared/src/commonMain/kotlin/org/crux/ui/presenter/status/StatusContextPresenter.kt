package org.crux.ui.presenter.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import org.crux.data.database.cache.CacheDatabase
import org.crux.data.database.cache.connect
import org.crux.data.database.cache.model.DbPagingTimeline
import org.crux.data.database.cache.model.DbStatus
import org.crux.data.datasource.microblog.datasource.PostDataSource
import org.crux.data.datasource.microblog.paging.CacheableRemoteLoader
import org.crux.data.datasource.microblog.paging.RemoteLoader
import org.crux.data.repository.AccountRepository
import org.crux.data.repository.accountServiceFlow
import org.crux.di.koinInject
import org.crux.model.AccountType
import org.crux.model.DbAccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiState
import org.crux.ui.model.UiTimelineV2
import org.crux.ui.model.flattenUiState
import org.crux.ui.model.onSuccess
import org.crux.ui.model.takeSuccess
import org.crux.ui.model.toUi
import org.crux.ui.presenter.PresenterBase
import org.crux.ui.presenter.home.TimelinePresenter
import org.crux.ui.presenter.home.TimelineState
import org.crux.ui.render.UiDateTime
import org.crux.web.shared.WebPresenter

@OptIn(ExperimentalCoroutinesApi::class)
@WebPresenter("statusContext")
public class StatusContextPresenter(
    private val accountType: AccountType,
    private val statusKey: MicroBlogKey,
) : PresenterBase<StatusContextPresenter.State>() {
    @Immutable
    public interface State : TimelineState {
        public val current: UiState<UiTimelineV2>
    }

    private val database: CacheDatabase by koinInject()
    private val accountRepository: AccountRepository by koinInject()

    private val currentStatusFlow by lazy {
        accountServiceFlow(
            accountType = accountType,
            repository = accountRepository,
        ).flatMapLatest { service ->
            if (service is PostDataSource) {
                service.postHandler.post(statusKey).toUi()
            } else {
                flowOf(null)
            }
        }.mapNotNull {
            it ?: UiState.Error(Exception("Current service does not support post data source"))
        }.distinctUntilChanged()
    }

    private val timelinePresenter by lazy {
        object : TimelinePresenter() {
            override fun allowLongTextTranslationDisplay(loader: RemoteLoader<UiTimelineV2>): Boolean = true

            override val loader: Flow<RemoteLoader<UiTimelineV2>> by lazy {
                currentStatusFlow
                    .map { statusKey }
                    .distinctUntilChanged()
                    .flatMapLatest { key ->
                        accountServiceFlow(
                            accountType = accountType,
                            repository = accountRepository,
                        ).map { service ->
                            val loader = service.context(key)
                            if (loader is CacheableRemoteLoader<UiTimelineV2>) {
                                val pagingKey = loader.pagingKey
                                val exists = database.pagingTimelineDao().existsPaging(accountType as DbAccountType, pagingKey)
                                if (!exists) {
                                    val status = database.statusDao().get(statusKey, accountType).firstOrNull()
                                    status?.let {
                                        database.connect {
                                            database
                                                .pagingTimelineDao()
                                                .insertAll(
                                                    listOf(
                                                        DbPagingTimeline(
                                                            statusId = DbStatus.createId(accountType as DbAccountType, statusKey),
                                                            pagingKey = pagingKey,
                                                            sortId = 0,
                                                        ),
                                                    ),
                                                )
                                        }
                                    }
                                }
                            }
                            loader
                        }
                    }
            }

            override suspend fun transform(data: UiTimelineV2): UiTimelineV2 =
                data.filterDetailParents(
                    statusKey = statusKey,
                    currentCreatedAt = null,
                )
        }
    }

    @Composable
    override fun body(): State {
        val current by currentStatusFlow.flattenUiState()
        val listState = timelinePresenter.body()
        current.onSuccess {
            remember {
                LogStatusHistoryPresenter(
                    accountType = accountType,
                    statusKey = statusKey,
                )
            }.body()
        }
        return object : State, TimelineState by listState {
            override val current = current
        }
    }
}

internal fun UiTimelineV2.filterDetailParents(
    statusKey: MicroBlogKey,
    currentCreatedAt: UiDateTime?,
): UiTimelineV2 = this
