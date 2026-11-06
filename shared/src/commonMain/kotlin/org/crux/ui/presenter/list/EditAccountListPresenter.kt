package org.crux.ui.presenter.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.filter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.crux.common.PagingState
import org.crux.common.toPagingState
import org.crux.data.datasource.microblog.datasource.ListDataSource
import org.crux.data.repository.AccountRepository
import org.crux.data.repository.accountServiceFlow
import org.crux.data.repository.accountServiceProvider
import org.crux.di.koinInject
import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiList
import org.crux.ui.model.UiState
import org.crux.ui.model.flattenUiState
import org.crux.ui.model.map
import org.crux.ui.model.onSuccess
import org.crux.ui.model.toUi
import org.crux.ui.presenter.PresenterBase

/**
 * Presenter for editing lists for a user.
 * This presenter should be used for managing lists for a user.
 */
public class EditAccountListPresenter(
    private val accountType: AccountType,
    private val userKey: MicroBlogKey,
) : PresenterBase<EditAccountListState>() {
    private val accountRepository: AccountRepository by koinInject()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val userListFlow by lazy {
        accountServiceFlow(accountType, accountRepository)
            .flatMapLatest { service ->
                require(service is ListDataSource)
                service.listMemberHandler.userLists(userKey).toUi()
            }.map {
                it.map {
                    it.toImmutableList()
                }
            }
    }

    @Composable
    override fun body(): EditAccountListState {
        val scope = rememberCoroutineScope()
        val serviceState = accountServiceProvider(accountType = accountType, repository = accountRepository)
        val allList =
            serviceState
                .map { service ->
                    require(service is ListDataSource)
                    remember(service) {
                        service.listHandler.data.cachedIn(scope).map {
                            it.filter {
                                !it.readonly
                            }
                        }
                    }.collectAsLazyPagingItems()
                }.toPagingState()
        val userLists by userListFlow.flattenUiState()
        return object : EditAccountListState {
            override val lists = allList
            override val userLists = userLists

            override fun addList(list: UiList) {
                serviceState.onSuccess {
                    require(it is ListDataSource)
                    scope.launch {
                        it.listMemberHandler.addMember(list.id, userKey = userKey)
                    }
                }
            }

            override fun removeList(list: UiList) {
                serviceState.onSuccess {
                    require(it is ListDataSource)
                    scope.launch {
                        it.listMemberHandler.removeMember(list.id, userKey = userKey)
                    }
                }
            }

            override fun isInList(list: UiList): UiState<Boolean> =
                userLists.map { item ->
                    item.any {
                        it.id == list.id
                    }
                }
        }
    }
}

@Immutable
public interface EditAccountListState {
    /**
     * All lists.
     */
    public val lists: PagingState<UiList.List>

    /**
     * Lists that the user is a member of.
     */
    public val userLists: UiState<ImmutableList<UiList>>

    public fun addList(list: UiList)

    public fun removeList(list: UiList)

    public fun isInList(list: UiList): UiState<Boolean>
}
