package org.crux.ui.presenter.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import org.crux.common.collectAsState
import org.crux.data.datasource.microblog.datasource.ListDataSource
import org.crux.data.repository.AccountRepository
import org.crux.data.repository.accountServiceProvider
import org.crux.di.koinInject
import org.crux.model.AccountType
import org.crux.ui.model.UiList
import org.crux.ui.model.UiState
import org.crux.ui.model.flatMap
import org.crux.ui.model.toUi
import org.crux.ui.presenter.PresenterBase

/**
 * Presenter for retrieving list information.
 */
public class ListInfoPresenter(
    private val accountType: AccountType,
    private val listId: String,
) : PresenterBase<ListInfoState>() {
    private val accountRepository: AccountRepository by koinInject()

    @Composable
    override fun body(): ListInfoState {
        val serviceState = accountServiceProvider(accountType = accountType, repository = accountRepository)
        val listInfo =
            serviceState.flatMap {
                remember(it) {
                    require(it is ListDataSource)
                    it.listHandler.listInfo(listId)
                }.collectAsState().toUi()
            }

        return object : ListInfoState {
            override val listInfo = listInfo
        }
    }
}

@Immutable
public interface ListInfoState {
    public val listInfo: UiState<UiList.List>
}
