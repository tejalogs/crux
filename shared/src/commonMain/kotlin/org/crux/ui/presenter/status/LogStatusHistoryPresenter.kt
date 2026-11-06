package org.crux.ui.presenter.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import org.crux.data.database.cache.CacheDatabase
import org.crux.data.database.cache.model.DbPagingTimeline
import org.crux.data.database.cache.model.DbStatus
import org.crux.data.repository.STATUS_HISTORY_PAGING_KEY
import org.crux.di.koinInject
import org.crux.model.AccountType
import org.crux.model.DbAccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.presenter.PresenterBase
import kotlin.time.Clock

public class LogStatusHistoryPresenter(
    private val accountType: AccountType,
    private val statusKey: MicroBlogKey,
) : PresenterBase<LogStatusHistoryPresenter.State>() {
    internal companion object {
        const val PAGING_KEY = STATUS_HISTORY_PAGING_KEY
    }

    private val cacheDatabase: CacheDatabase by koinInject()

    @Immutable
    public interface State

    @Composable
    override fun body(): State {
        LaunchedEffect(statusKey, accountType) {
            cacheDatabase.pagingTimelineDao().insertAll(
                listOf(
                    DbPagingTimeline(
                        statusId = DbStatus.createId(accountType as DbAccountType, statusKey),
                        pagingKey = PAGING_KEY,
                        sortId = Clock.System.now().toEpochMilliseconds(),
                    ),
                ),
            )
        }

        return object : State {
        }
    }
}
