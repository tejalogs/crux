package org.crux.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import org.crux.data.datasource.microblog.AuthenticatedMicroblogDataSource
import org.crux.data.datasource.microblog.datasource.UserDataSource
import org.crux.data.repository.AccountRepository
import org.crux.data.repository.NoActiveAccountException
import org.crux.data.repository.accountServiceFlow
import org.crux.di.koinInject
import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiState
import org.crux.ui.model.flattenUiState
import org.crux.ui.model.toUi
import org.crux.ui.presenter.PresenterBase

public open class UserPresenter(
    private val accountType: AccountType,
    private val userKey: MicroBlogKey?,
) : PresenterBase<UserState>() {
    private val accountRepository: AccountRepository by koinInject()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dataFlow by lazy {
        accountServiceFlow(accountType, accountRepository)
            .flatMapLatest { service ->
                val userId =
                    userKey?.id
                        ?: if (service is AuthenticatedMicroblogDataSource) {
                            service.accountKey.id
                        } else {
                            null
                        }
                if (userId == null || service !is UserDataSource) {
                    flowOf(UiState.Error(NoActiveAccountException))
                } else {
                    service.userHandler.userById(userId).toUi()
                }
            }
    }

    @Composable
    override fun body(): UserState {
        val user by dataFlow.flattenUiState()

        return object : UserState {
            override val user = user
        }
    }
}
