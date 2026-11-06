package org.crux.ui.presenter.settings

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.first
import org.crux.common.encodeJson
import org.crux.data.database.app.AppDatabase
import org.crux.data.database.app.model.AppDatabaseExport
import org.crux.di.koinInject
import org.crux.ui.presenter.ExportState
import org.crux.ui.presenter.PresenterBase

public class ExportAppDatabasePresenter : PresenterBase<ExportState>() {
    private val appDatabase: AppDatabase by koinInject()

    @Composable
    override fun body(): ExportState =
        object : ExportState {
            override suspend fun export(): String = this@ExportAppDatabasePresenter.export()
        }

    public suspend fun export(): String {
        val export =
            AppDatabaseExport(
                accounts = appDatabase.accountDao().allAccounts().first(),
                keywordFilters = appDatabase.keywordFilterDao().selectAll().first(),
                searchHistories = appDatabase.searchHistoryDao().select().first(),
                rssSources = appDatabase.rssSourceDao().getAll().first(),
                localFollows = appDatabase.localFollowDao().follows().first(),
                localFollowGroups = appDatabase.localFollowDao().groups().first(),
            )
        return export.encodeJson()
    }
}
