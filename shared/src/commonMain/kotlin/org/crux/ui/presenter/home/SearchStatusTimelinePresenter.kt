package org.crux.ui.presenter.home

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import org.crux.data.datasource.microblog.paging.PagingRequest
import org.crux.data.datasource.microblog.paging.PagingResult
import org.crux.data.datasource.microblog.paging.RemoteLoader
import org.crux.data.repository.AccountRepository
import org.crux.data.repository.accountServiceFlow
import org.crux.di.koinInject
import org.crux.model.AccountType
import org.crux.ui.model.UiTimelineV2

public class SearchStatusTimelinePresenter(
    private val accountType: AccountType,
    private val queryFlow: MutableStateFlow<String> = MutableStateFlow(""),
) : TimelinePresenter() {
    public constructor(
        accountType: AccountType,
        initialQuery: String,
    ) : this(
        accountType = accountType,
        queryFlow = MutableStateFlow(initialQuery),
    )

    private val accountRepository: AccountRepository by koinInject()

    @OptIn(ExperimentalCoroutinesApi::class)
    override val loader: Flow<RemoteLoader<UiTimelineV2>> by lazy {
        accountServiceFlow(
            accountType = accountType,
            repository = accountRepository,
        ).flatMapLatest { service ->
            queryFlow.map { query ->
                if (query.isEmpty()) {
                    object : RemoteLoader<UiTimelineV2> {
                        override suspend fun load(
                            pageSize: Int,
                            request: PagingRequest,
                        ): PagingResult<UiTimelineV2> = PagingResult(data = emptyList())
                    }
                } else {
                    service.searchStatus(query)
                }
            }
        }
    }

    public fun setQuery(query: String) {
        queryFlow.value = query
    }
}
