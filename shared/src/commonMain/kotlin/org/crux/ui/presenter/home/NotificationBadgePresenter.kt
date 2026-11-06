package org.crux.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import org.crux.common.collectAsState
import org.crux.data.datasource.microblog.datasource.NotificationDataSource
import org.crux.data.repository.AccountRepository
import org.crux.data.repository.accountServiceFlow
import org.crux.di.koinInject
import org.crux.model.AccountType
import org.crux.ui.model.UiState
import org.crux.ui.model.collectAsUiState
import org.crux.ui.model.flatMap
import org.crux.ui.model.onSuccess
import org.crux.ui.model.toUi
import org.crux.ui.presenter.PresenterBase
import kotlin.time.Duration.Companion.minutes

public class NotificationBadgePresenter(
    private val accountType: AccountType,
    private val autoRefresh: Boolean = true,
) : PresenterBase<NotificationBadgeState>() {
    private val accountRepository: AccountRepository by koinInject()

    private val countFlow by lazy {
        accountServiceFlow(
            accountType = accountType,
            repository = accountRepository,
        ).filterIsInstance<NotificationDataSource>()
            .map {
                it.notificationHandler.notificationBadgeCount
            }
    }

    @Composable
    override fun body(): NotificationBadgeState {
        val countState by countFlow.collectAsUiState()
        val count = countState.flatMap { it.collectAsState().toUi() }
        if (autoRefresh) {
            countState.onSuccess {
                LaunchedEffect(Unit) {
                    delay(1.minutes)
                    it.refresh()
                }
            }
        }
        return object : NotificationBadgeState {
            override val count = count

            override fun refresh() {
                countState.onSuccess {
                    it.refresh()
                }
            }
        }
    }
}

@Immutable
public interface NotificationBadgeState {
    public val count: UiState<Int>

    public fun refresh()
}
