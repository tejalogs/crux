package org.crux.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.map
import org.crux.data.datasource.microblog.ComposeDataSource
import org.crux.data.repository.AccountRepository
import org.crux.di.koinInject
import org.crux.ui.model.UiState
import org.crux.ui.model.collectAsUiState
import org.crux.ui.presenter.PresenterBase

public class CanComposePresenter : PresenterBase<CanComposeState>() {
    private val accountRepository: AccountRepository by koinInject()

    private val canComposeFlow by lazy {
        accountRepository.allAccounts.map { accounts ->
            accounts.any { account ->
                runCatching {
                    accountRepository.getOrCreateDataSource(account) is ComposeDataSource
                }.getOrDefault(false)
            }
        }
    }

    @Composable
    override fun body(): CanComposeState {
        val canCompose by canComposeFlow.collectAsUiState()
        return object : CanComposeState {
            override val canCompose: UiState<Boolean> = canCompose
        }
    }
}

@Immutable
public interface CanComposeState {
    public val canCompose: UiState<Boolean>
}
