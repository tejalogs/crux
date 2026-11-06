package org.crux.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import org.crux.data.datasource.microblog.datasource.UserDataSource
import org.crux.data.repository.AccountRepository
import org.crux.data.repository.accountServiceFlow
import org.crux.di.koinInject
import org.crux.model.AccountType
import org.crux.ui.model.UiProfile
import org.crux.ui.model.UiState
import org.crux.ui.model.flattenUiState
import org.crux.ui.model.toUi
import org.crux.ui.presenter.PresenterBase

public class ActiveAccountPresenter : PresenterBase<UserState>() {
    private val accountRepository: AccountRepository by koinInject()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val userFlow by lazy {
        accountRepository.activeAccount.flatMapLatest { accountState ->
            when (accountState) {
                is UiState.Loading -> {
                    flowOf(UiState.Loading())
                }

                is UiState.Error -> {
                    flowOf(UiState.Error(accountState.throwable))
                }

                is UiState.Success -> {
                    val accountKey = accountState.data.accountKey
                    accountServiceFlow(
                        accountType = AccountType.Specific(accountKey),
                        repository = accountRepository,
                    ).flatMapLatest { service ->
                        if (service is UserDataSource) {
                            service.userHandler.userById(accountKey.id).toUi()
                        } else {
                            flowOf(UiState.Error(IllegalStateException("Current account does not support user data")))
                        }
                    }
                }
            }
        }
    }

    @Composable
    override fun body(): UserState {
        val user by userFlow.flattenUiState()
        return object : UserState {
            override val user: UiState<UiProfile> = user
        }
    }
}

@Immutable
public interface UserState {
    public val user: UiState<UiProfile>
}
