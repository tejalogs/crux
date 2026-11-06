package org.crux.ui.presenter.dm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import org.crux.data.datasource.microblog.DirectMessageDataSource
import org.crux.data.repository.AccountRepository
import org.crux.data.repository.accountServiceFlow
import org.crux.di.koinInject
import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiState
import org.crux.ui.model.flattenUiState
import org.crux.ui.presenter.PresenterBase

public class UserDMConversationPresenter(
    private val accountType: AccountType,
    private val userKey: MicroBlogKey,
) : PresenterBase<UserDMConversationPresenter.State>() {
    private val accountRepository: AccountRepository by koinInject()

    @Immutable
    public interface State {
        public val roomKey: UiState<MicroBlogKey>
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val roomKeyFlow by lazy {
        accountServiceFlow(
            accountType = accountType,
            repository = accountRepository,
        ).flatMapLatest {
            require(it is DirectMessageDataSource)
            it.createDirectMessageRoom(userKey)
        }
    }

    @Composable
    override fun body(): State {
        val roomKey by roomKeyFlow.flattenUiState()
        return object : State {
            override val roomKey = roomKey
        }
    }
}
