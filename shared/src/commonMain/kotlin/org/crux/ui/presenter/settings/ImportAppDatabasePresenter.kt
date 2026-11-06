package org.crux.ui.presenter.settings

import androidx.compose.runtime.Composable
import org.crux.common.decodeJson
import org.crux.data.database.app.AppDatabase
import org.crux.data.database.app.model.AppDatabaseExport
import org.crux.data.database.cache.connect
import org.crux.di.koinInject
import org.crux.model.requireValidPlatformId
import org.crux.ui.presenter.ImportState
import org.crux.ui.presenter.PresenterBase

public class ImportAppDatabasePresenter(
    private val jsonContent: String,
) : PresenterBase<ImportState>() {
    private val appDatabase: AppDatabase by koinInject()

    @Composable
    override fun body(): ImportState =
        object : ImportState {
            override suspend fun import() {
                this@ImportAppDatabasePresenter.import()
            }
        }

    public suspend fun import() {
        // Parse and validate JSON structure
        val export =
            try {
                jsonContent.decodeJson<AppDatabaseExport>()
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid import file format: ${e.message}", e)
            }

        // Validate imported data
        validateImportData(export)

        // Perform import within a transaction (automatically rolls back on error)
        // Note: DAO insert methods use OnConflictStrategy.REPLACE - existing records with matching primary keys will be replaced
        appDatabase.connect {
            export.accounts.forEach { appDatabase.accountDao().insert(it) }

            export.keywordFilters.forEach { appDatabase.keywordFilterDao().insert(it) }

            export.searchHistories.forEach { appDatabase.searchHistoryDao().insert(it) }

            if (export.rssSources.isNotEmpty()) {
                appDatabase.rssSourceDao().insertAll(export.rssSources)
            }

            export.localFollowGroups.forEach { appDatabase.localFollowDao().insertGroup(it) }

            export.localFollows.forEach { appDatabase.localFollowDao().insertFollow(it) }
        }
    }

    private fun validateImportData(export: AppDatabaseExport) {
        // Validate account data
        export.accounts.forEach { account ->
            requireValidPlatformId(account.platformId)
            require(account.credential_json.isNotBlank()) {
                "Invalid account data: credential_json cannot be empty"
            }
        }

        // Validate keyword filters
        export.keywordFilters.forEach { filter ->
            require(filter.keyword.isNotBlank()) {
                "Invalid keyword filter: keyword cannot be empty"
            }
        }

        // Validate search histories
        export.searchHistories.forEach { history ->
            require(history.search.isNotBlank()) {
                "Invalid search history: search term cannot be empty"
            }
        }

        // Validate RSS sources
        export.rssSources.forEach { source ->
            require(source.url.isNotBlank()) {
                "Invalid RSS source: URL cannot be empty"
            }
        }
    }
}
