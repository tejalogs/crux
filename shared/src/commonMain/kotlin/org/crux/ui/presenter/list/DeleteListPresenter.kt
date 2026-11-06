package org.crux.ui.presenter.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.crux.data.datasource.microblog.datasource.ListDataSource
import org.crux.data.repository.AccountRepository
import org.crux.data.repository.accountServiceFlow
import org.crux.di.koinInject
import org.crux.model.AccountType
import org.crux.ui.presenter.PresenterBase

/**
 * Presenter for deleting lists.
 */
public class DeleteListPresenter(
    private val accountType: AccountType,
    private val listId: String,
) : PresenterBase<DeleteListState>() {
    private val scope by koinInject<CoroutineScope>()
    private val accountRepository: AccountRepository by koinInject()

    @Composable
    override fun body(): DeleteListState =
        object : DeleteListState {
            override suspend fun deleteList() {
                scope.launch {
                    accountServiceFlow(
                        accountType = accountType,
                        repository = accountRepository,
                    ).map {
                        require(it is ListDataSource)
                        it
                    }.first()
                        .listHandler
                        .delete(listId)
                }
            }
        }
}

@Immutable
public interface DeleteListState {
    public suspend fun deleteList()
}
