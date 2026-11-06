package org.crux.ui.presenter.dm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.crux.data.datasource.microblog.DirectMessageDataSource
import org.crux.data.datasource.microblog.MicroblogDataSource
import org.crux.data.datasource.microblog.datasource.UserDataSource
import org.crux.data.repository.AccountRepository
import org.crux.data.repository.allAccountServicesFlow
import org.crux.di.koinInject
import org.crux.ui.presenter.PresenterBase

public class DirectMessageAvailabilityPresenter : PresenterBase<DirectMessageAvailabilityPresenter.State>() {
    private val accountRepository: AccountRepository by koinInject()

    private val hasAvailableAccountFlow by lazy {
        allAccountServicesFlow(accountRepository)
            .map { services ->
                services.any { it.isDirectMessageCapableAccountService() }
            }.distinctUntilChanged()
    }

    @Composable
    override fun body(): State {
        val hasAvailableAccount by hasAvailableAccountFlow.collectAsState(false)
        return StateImpl(
            hasAvailableAccount = hasAvailableAccount,
        )
    }

    @Immutable
    public interface State {
        public val hasAvailableAccount: Boolean
    }

    private data class StateImpl(
        override val hasAvailableAccount: Boolean,
    ) : State
}

internal fun MicroblogDataSource.isDirectMessageCapableAccountService(): Boolean = this is DirectMessageDataSource && this is UserDataSource
