package org.crux.ui.presenter.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.map
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.crux.common.PagingState
import org.crux.common.emptyFlow
import org.crux.common.toPagingState
import org.crux.data.datasource.microblog.datasource.ListDataSource
import org.crux.data.datasource.microblog.paging.toPagingSource
import org.crux.data.datasource.microblog.pagingConfig
import org.crux.data.repository.AccountRepository
import org.crux.data.repository.accountServiceFlow
import org.crux.data.repository.accountServiceProvider
import org.crux.di.koinInject
import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiProfile
import org.crux.ui.model.onSuccess
import org.crux.ui.presenter.PresenterBase

@Immutable
public interface EditListMemberState {
    public val users: PagingState<Pair<UiProfile, Boolean>>

    public fun setFilter(value: String)

    public fun addMember(userKey: MicroBlogKey)

    public fun removeMember(userKey: MicroBlogKey)
}

public class EditListMemberPresenter(
    private val accountType: AccountType,
    private val listId: String,
) : PresenterBase<EditListMemberState>() {
    private val accountRepository: AccountRepository by koinInject()
    private val filterFlow = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    private val serviceFlow by lazy {
        accountServiceFlow(accountType, accountRepository)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val membersFlow by lazy {
        serviceFlow.flatMapLatest { service ->
            if (service is ListDataSource) {
                service.listMemberHandler.listMemberKeysFlow(listId)
            } else {
                flowOf(emptySet<MicroBlogKey>())
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val usersFlow by lazy {
        combine(
            serviceFlow,
            filterFlow,
            membersFlow,
        ) { service, filter, members ->
            Triple(service, filter, members)
        }.flatMapLatest { (service, filter, members) ->
            if (service !is ListDataSource || filter.isEmpty()) {
                PagingData.emptyFlow<Pair<UiProfile, Boolean>>()
            } else {
                Pager(config = pagingConfig) {
                    service.searchUser(filter).toPagingSource()
                }.flow.map { pagingData ->
                    pagingData.map { user ->
                        user to (user.key in members)
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Composable
    override fun body(): EditListMemberState {
        val scope = rememberCoroutineScope()
        val serviceState = accountServiceProvider(accountType = accountType, repository = accountRepository)
        val cachedUsersFlow =
            remember(usersFlow) {
                usersFlow.cachedIn(scope)
            }
        val usersState =
            cachedUsersFlow
                .collectAsLazyPagingItems()
                .toPagingState()

        return object : EditListMemberState {
            override val users: PagingState<Pair<UiProfile, Boolean>> = usersState

            override fun setFilter(value: String) {
                filterFlow.value = value
            }

            override fun addMember(userKey: MicroBlogKey) {
                serviceState.onSuccess { service ->
                    scope.launch {
                        if (service is ListDataSource) {
                            service.listMemberHandler.addMember(listId, userKey)
                        }
                    }
                }
            }

            override fun removeMember(userKey: MicroBlogKey) {
                serviceState.onSuccess { service ->
                    scope.launch {
                        if (service is ListDataSource) {
                            service.listMemberHandler.removeMember(listId, userKey)
                        }
                    }
                }
            }
        }
    }
}
