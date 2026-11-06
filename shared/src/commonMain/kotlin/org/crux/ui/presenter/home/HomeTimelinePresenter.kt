package org.crux.ui.presenter.home

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.crux.data.datasource.microblog.paging.RemoteLoader
import org.crux.data.repository.AccountRepository
import org.crux.data.repository.accountServiceFlow
import org.crux.di.koinInject
import org.crux.model.AccountType
import org.crux.ui.model.UiTimelineV2

public class HomeTimelinePresenter(
    private val accountType: AccountType,
) : TimelinePresenter() {
    private val accountRepository: AccountRepository by koinInject()

    override val loader: Flow<RemoteLoader<UiTimelineV2>> by lazy {
        accountServiceFlow(
            accountType = accountType,
            repository = accountRepository,
        ).map {
            it.homeTimeline()
        }
    }
}
