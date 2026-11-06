package org.crux.ui.presenter.status

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import org.crux.data.database.cache.CacheDatabase
import org.crux.data.database.cache.model.DbUserHistory
import org.crux.di.koinInject
import org.crux.model.AccountType
import org.crux.model.DbAccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.presenter.PresenterBase
import kotlin.time.Clock

public class LogUserHistoryPresenter(
    private val accountType: AccountType,
    private val userKey: MicroBlogKey,
) : PresenterBase<LogUserHistoryPresenter.State>() {
    internal companion object {
        const val PAGING_KEY = "user_history"
    }

    private val cacheDatabase: CacheDatabase by koinInject()

    @Immutable
    public interface State

    @Composable
    override fun body(): State {
        LaunchedEffect(Unit) {
            cacheDatabase.userDao().insertHistory(
                DbUserHistory(
                    userKey = userKey,
                    accountType = accountType as DbAccountType,
                    lastVisit = Clock.System.now().toEpochMilliseconds(),
                ),
            )
        }

        return object : State {
        }
    }
}
