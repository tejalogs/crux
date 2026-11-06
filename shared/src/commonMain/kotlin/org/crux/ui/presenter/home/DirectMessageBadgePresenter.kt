package org.crux.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import org.crux.common.collectAsState
import org.crux.data.datasource.microblog.DirectMessageDataSource
import org.crux.data.repository.AccountRepository
import org.crux.data.repository.accountServiceProvider
import org.crux.di.koinInject
import org.crux.model.AccountType
import org.crux.ui.model.UiState
import org.crux.ui.model.flatMap
import org.crux.ui.model.map
import org.crux.ui.model.onSuccess
import org.crux.ui.model.toUi
import org.crux.ui.presenter.PresenterBase
import kotlin.time.Duration.Companion.minutes

public class DirectMessageBadgePresenter(
    private val accountType: AccountType,
    private val autoRefresh: Boolean = true,
) : PresenterBase<NotificationBadgeState>() {
    private val accountRepository: AccountRepository by koinInject()

    @Composable
    override fun body(): NotificationBadgeState {
        val serviceState = accountServiceProvider(accountType = accountType, repository = accountRepository)
        val countState =
            serviceState.map { service ->
                remember(service) {
                    require(service is DirectMessageDataSource)
                    service.directMessageBadgeCount
                }.collectAsState()
            }
        if (autoRefresh) {
            countState.onSuccess {
                LaunchedEffect(Unit) {
                    delay(1.minutes)
                    it.refresh()
                }
            }
        }
        return object : NotificationBadgeState {
            override val count: UiState<Int> = countState.flatMap { it.toUi() }

            override fun refresh() {
                countState.onSuccess {
                    it.refresh()
                }
            }
        }
    }
}
