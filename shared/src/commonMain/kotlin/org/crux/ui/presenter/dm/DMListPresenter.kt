package org.crux.ui.presenter.dm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.compose.collectAsLazyPagingItems
import org.crux.common.PagingState
import org.crux.common.isRefreshing
import org.crux.common.onSuccess
import org.crux.common.toPagingState
import org.crux.data.datasource.microblog.DirectMessageDataSource
import org.crux.data.repository.AccountRepository
import org.crux.data.repository.accountServiceProvider
import org.crux.di.koinInject
import org.crux.model.AccountType
import org.crux.ui.model.UiDMRoom
import org.crux.ui.model.map
import org.crux.ui.presenter.PresenterBase

public class DMListPresenter(
    private val accountType: AccountType,
) : PresenterBase<DMListState>() {
    private val accountRepository: AccountRepository by koinInject()

    @Composable
    override fun body(): DMListState {
        val scope = rememberCoroutineScope()
        val serviceState = accountServiceProvider(accountType = accountType, repository = accountRepository)
        val items =
            serviceState
                .map { service ->
                    require(service is DirectMessageDataSource)
                    remember(service) {
                        service.directMessageList(scope = scope)
                    }.collectAsLazyPagingItems()
                }.toPagingState()
        return object : DMListState {
            override val items = items

            override val isRefreshing = items.isRefreshing

            override suspend fun refreshSuspend() {
                items.onSuccess {
                    refreshSuspend()
                }
            }
        }
    }
}

@Immutable
public interface DMListState {
    public val items: PagingState<UiDMRoom>
    public val isRefreshing: Boolean

    public suspend fun refreshSuspend()
}
