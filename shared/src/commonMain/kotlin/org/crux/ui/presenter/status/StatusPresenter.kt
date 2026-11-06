package org.crux.ui.presenter.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import org.crux.common.collectAsState
import org.crux.data.datasource.microblog.datasource.PostDataSource
import org.crux.data.repository.AccountRepository
import org.crux.data.repository.accountServiceProvider
import org.crux.di.koinInject
import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiState
import org.crux.ui.model.UiTimelineV2
import org.crux.ui.model.flatMap
import org.crux.ui.model.toUi
import org.crux.ui.presenter.PresenterBase

public class StatusPresenter(
    private val accountType: AccountType,
    private val statusKey: MicroBlogKey,
) : PresenterBase<StatusState>() {
    private val accountRepository: AccountRepository by koinInject()

    @Composable
    override fun body(): StatusState {
        val serviceState = accountServiceProvider(accountType = accountType, repository = accountRepository)
        val accountServiceState =
            serviceState.flatMap { service ->
                remember(service, statusKey) {
                    (service as PostDataSource).postHandler.post(statusKey)
                }.collectAsState().toUi()
            }
        remember { LogStatusHistoryPresenter(accountType = accountType, statusKey = statusKey) }.body()

        return object : StatusState {
            override val status: UiState<UiTimelineV2> = accountServiceState
        }
    }
}

@Immutable
public interface StatusState {
    public val status: UiState<UiTimelineV2>
}
