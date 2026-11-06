package org.crux.ui.presenter.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import org.crux.common.refreshSuspend
import org.crux.data.datasource.microblog.datasource.ListDataSource
import org.crux.data.datasource.microblog.list.ListMetaData
import org.crux.data.datasource.microblog.list.ListMetaDataType
import org.crux.data.repository.AccountRepository
import org.crux.data.repository.accountServiceProvider
import org.crux.di.koinInject
import org.crux.model.AccountType
import org.crux.ui.model.UiState
import org.crux.ui.model.map
import org.crux.ui.model.onSuccess
import org.crux.ui.presenter.PresenterBase

/**
 * Presenter for editing lists.
 */
public class ListEditPresenter(
    private val accountType: AccountType,
    private val listId: String,
) : PresenterBase<ListEditPresenter.State>() {
    @Immutable
    public interface State :
        EditListMemberState,
        ListMembersState,
        ListInfoState {
        public val supportedMetaData: UiState<ImmutableList<ListMetaDataType>>

        public fun refresh()

        public suspend fun updateList(listMetaData: ListMetaData)
    }

    private val accountRepository: AccountRepository by koinInject()

    @Composable
    override fun body(): State {
        val scope = rememberCoroutineScope()
        val serviceState = accountServiceProvider(accountType = accountType, repository = accountRepository)
        val listInfoState =
            remember(
                accountType,
                listId,
            ) {
                ListInfoPresenter(accountType, listId)
            }.body()
        val state =
            remember(
                accountType,
                listId,
            ) {
                EditListMemberPresenter(accountType, listId)
            }.body()
        val memberState =
            remember(
                accountType,
                listId,
            ) {
                ListMembersPresenter(accountType, listId)
            }.body()
        return object :
            State,
            EditListMemberState by state,
            ListMembersState by memberState,
            ListInfoState by listInfoState {
            override val supportedMetaData =
                serviceState.map {
                    require(it is ListDataSource)
                    it.listHandler.supportedMetaData
                }

            override fun refresh() {
                scope.launch {
                    memberInfo.refreshSuspend()
                }
            }

            override suspend fun updateList(listMetaData: ListMetaData) {
                serviceState.onSuccess {
                    require(it is ListDataSource)
                    it.listHandler.update(listId, listMetaData)
                }
            }
        }
    }
}
