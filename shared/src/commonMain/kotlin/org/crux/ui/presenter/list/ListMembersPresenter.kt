package org.crux.ui.presenter.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import org.crux.common.PagingState
import org.crux.common.emptyFlow
import org.crux.common.toPagingState
import org.crux.data.datasource.microblog.datasource.ListDataSource
import org.crux.data.repository.AccountRepository
import org.crux.data.repository.accountServiceFlow
import org.crux.di.koinInject
import org.crux.model.AccountType
import org.crux.ui.model.UiProfile
import org.crux.ui.presenter.PresenterBase

@Immutable
public interface ListMembersState {
    public val memberInfo: PagingState<UiProfile>
}

public class ListMembersPresenter(
    private val accountType: AccountType,
    private val listId: String,
) : PresenterBase<ListMembersState>() {
    private val accountRepository: AccountRepository by koinInject()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val membersFlow by lazy {
        accountServiceFlow(accountType, accountRepository).flatMapLatest { service ->
            if (service is ListDataSource) {
                service.listMemberHandler.listMembers(listId)
            } else {
                PagingData.emptyFlow<UiProfile>()
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Composable
    override fun body(): ListMembersState {
        val scope = rememberCoroutineScope()
        val memberInfo =
            remember {
                membersFlow.cachedIn(scope)
            }.collectAsLazyPagingItems().toPagingState()

        return object : ListMembersState {
            override val memberInfo: PagingState<UiProfile> = memberInfo
        }
    }
}
