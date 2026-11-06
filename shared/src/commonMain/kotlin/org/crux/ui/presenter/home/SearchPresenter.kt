package org.crux.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import org.crux.common.PagingState
import org.crux.common.combineLatestFlowLists
import org.crux.common.emptyFlow
import org.crux.common.refreshSuspend
import org.crux.common.toPagingState
import org.crux.data.datasource.microblog.AuthenticatedMicroblogDataSource
import org.crux.data.datasource.microblog.datasource.UserDataSource
import org.crux.data.datasource.microblog.paging.toPagingSource
import org.crux.data.datasource.microblog.pagingConfig
import org.crux.data.repository.AccountRepository
import org.crux.data.repository.accountServiceFlow
import org.crux.data.repository.allAccountServicesFlow
import org.crux.di.koinInject
import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiProfile
import org.crux.ui.model.UiState
import org.crux.ui.model.UiTimelineV2
import org.crux.ui.model.collectAsUiState
import org.crux.ui.model.onSuccess
import org.crux.ui.model.takeSuccess
import org.crux.ui.model.toUi
import org.crux.ui.presenter.PresenterBase
import org.crux.web.shared.WebIgnore
import org.crux.web.shared.WebPresenter

@OptIn(ExperimentalCoroutinesApi::class)
@WebPresenter("search")
public class SearchPresenter(
    private val accountType: AccountType,
    private val initialQuery: String = "",
) : PresenterBase<SearchState>() {
    private val accountRepository: AccountRepository by koinInject()

    private val accountsFlow by lazy {
        allAccountServicesFlow(accountRepository)
            .map {
                it
                    .filterIsInstance<UserDataSource>()
                    .filterIsInstance<AuthenticatedMicroblogDataSource>()
                    .toImmutableList()
            }.map {
                it.map { dataSource ->
                    val authenticated = dataSource as UserDataSource
                    val accountKey = dataSource.accountKey
                    authenticated.userHandler.userById(accountKey.id).toUi()
                }
            }.combineLatestFlowLists()
            .map {
                it
                    .mapNotNull {
                        it.takeSuccess()
                    }.toImmutableList()
            }
    }

    private val queryFlow by lazy {
        MutableStateFlow(initialQuery)
    }

    private val selectedAccountFlow by lazy {
        MutableStateFlow<UiProfile?>(null)
    }

    private val selectedAccountTypeFlow: Flow<AccountType> by lazy {
        selectedAccountFlow.map { profile ->
            profile?.let {
                AccountType.Specific(it.key)
            } ?: accountType
        }
    }

    private val usersFlow by lazy {
        selectedAccountTypeFlow.flatMapLatest { accountType ->
            accountServiceFlow(accountType = accountType, repository = accountRepository).flatMapLatest { dataSource ->
                queryFlow.flatMapLatest { query ->
                    runCatching {
                        if (query.isEmpty()) {
                            PagingData.emptyFlow(isError = true)
                        } else {
                            Pager(config = pagingConfig) {
                                dataSource.searchUser(query).toPagingSource()
                            }.flow
                        }
                    }.getOrElse { PagingData.emptyFlow(isError = true) }
                }
            }
        }
    }

    private fun statusFlow(scope: CoroutineScope) =
        selectedAccountTypeFlow.flatMapLatest { accountType ->
            SearchStatusTimelinePresenter(
                accountType = accountType,
                queryFlow = queryFlow,
            ).createPager(
                scope = scope,
            )
        }

    @Composable
    override fun body(): SearchState {
        val scope = rememberCoroutineScope()
        val users =
            remember(usersFlow, scope) {
                usersFlow.cachedIn(scope)
            }.collectAsLazyPagingItems()
                .toPagingState()
        val status =
            remember {
                statusFlow(scope)
            }.collectAsLazyPagingItems()
                .toPagingState()
        val accounts by accountsFlow.collectAsUiState()
        val query by queryFlow.collectAsState()
        val selectedAccount by selectedAccountFlow.collectAsState()

        accounts.onSuccess {
            LaunchedEffect(it.size) {
                if (selectedAccount == null) {
                    selectedAccountFlow.value =
                        if (accountType is AccountType.Specific) {
                            it.find { profile -> profile.key == accountType.accountKey }
                        } else {
                            null
                        }
                }
            }
        }

        return object : SearchState {
            override val users = users
            override val status = status
            override val searching = query.isNotEmpty()
            override val accounts = accounts
            override val selectedAccount = selectedAccount

            override fun search(query: String) {
                queryFlow.value = query
            }

            override suspend fun refreshSuspend() {
                users.refreshSuspend()
                status.refreshSuspend()
            }

            override fun setAccount(profile: UiProfile) {
                selectedAccountFlow.value = profile
            }

            override fun setAccountKey(key: MicroBlogKey) {
                accounts.takeSuccess()?.firstOrNull { it.key == key }?.let {
                    selectedAccountFlow.value = it
                }
            }
        }
    }
}

@Immutable
public interface SearchState {
    public val users: PagingState<UiProfile>
    public val status: PagingState<UiTimelineV2>
    public val searching: Boolean
    public val accounts: UiState<ImmutableList<UiProfile>>
    public val selectedAccount: UiProfile?

    public fun search(query: String)

    public suspend fun refreshSuspend()

    @WebIgnore
    public fun setAccount(profile: UiProfile)

    public fun setAccountKey(key: MicroBlogKey)
}
