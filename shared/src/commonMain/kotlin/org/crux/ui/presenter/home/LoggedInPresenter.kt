package org.crux.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.map
import org.crux.data.repository.AccountRepository
import org.crux.di.koinInject
import org.crux.ui.model.UiState
import org.crux.ui.model.collectAsUiState
import org.crux.ui.presenter.PresenterBase

public class LoggedInPresenter : PresenterBase<LoggedInState>() {
    private val accountRepository: AccountRepository by koinInject()

    private val loggedInFlow by lazy {
        accountRepository.allAccounts.map { accounts -> accounts.any { it.platformAvailable } }
    }

    @Composable
    override fun body(): LoggedInState {
        val isLoggedIn by loggedInFlow.collectAsUiState()
        return object : LoggedInState {
            override val isLoggedIn: UiState<Boolean> = isLoggedIn
        }
    }
}

@Immutable
public interface LoggedInState {
    public val isLoggedIn: UiState<Boolean>
}
