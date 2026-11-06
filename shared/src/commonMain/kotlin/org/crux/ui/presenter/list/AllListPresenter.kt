package org.crux.ui.presenter.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.launch
import org.crux.common.PagingState
import org.crux.common.isRefreshing
import org.crux.common.refreshSuspend
import org.crux.common.toPagingState
import org.crux.data.datasource.microblog.datasource.ListDataSource
import org.crux.data.repository.AccountRepository
import org.crux.data.repository.accountServiceProvider
import org.crux.di.koinInject
import org.crux.model.AccountType
import org.crux.ui.model.UiList
import org.crux.ui.model.map
import org.crux.ui.presenter.PresenterBase

/**
 * Retrieving lists.
 * This presenter should be used for displaying lists.
 */
public class AllListPresenter(
    private val accountType: AccountType,
) : PresenterBase<AllListState>() {
    private val accountRepository: AccountRepository by koinInject()

    @Composable
    override fun body(): AllListState {
        val scope = rememberCoroutineScope()
        val serviceState = accountServiceProvider(accountType = accountType, repository = accountRepository)
        val items =
            serviceState
                .map { service ->
                    remember(service) {
                        require(service is ListDataSource)
                        service.listHandler.data.cachedIn(scope)
                    }.collectAsLazyPagingItems()
                }.toPagingState()
        return object : AllListState {
            override val items = items

            override val isRefreshing = items.isRefreshing

            override fun refresh() {
                scope.launch {
                    items.refreshSuspend()
                }
            }

            override suspend fun refreshSuspend() {
                items.refreshSuspend()
            }
        }
    }
}

@Immutable
public interface AllListState {
    public val items: PagingState<UiList.List>
    public val isRefreshing: Boolean

    public fun refresh()

    public suspend fun refreshSuspend()
}
