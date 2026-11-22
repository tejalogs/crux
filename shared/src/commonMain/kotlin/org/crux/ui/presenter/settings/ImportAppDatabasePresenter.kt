package org.crux.ui.presenter.settings

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.crux.common.PlatformDispatchers
import org.crux.common.decodeJson
import org.crux.data.database.app.AppDatabase
import org.crux.data.database.app.model.AppDatabaseExport
import org.crux.data.database.app.model.DbKeywordFilter
import org.crux.data.database.app.model.DbLocalFollow
import org.crux.data.database.app.model.DbRssSources
import org.crux.data.database.cache.connect
import org.crux.data.network.PublicUrlValidator
import org.crux.di.koinInject
import org.crux.model.requireValidPlatformId
import org.crux.ui.presenter.ImportState
import org.crux.ui.presenter.PresenterBase
import org.crux.ui.presenter.home.MAX_TIMELINE_KEYWORD_FILTERS
import org.crux.ui.presenter.home.isSafeKeywordFilter

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

        // Image URLs are checked off the main thread: PublicUrlValidator may resolve host names.
        val rssSources = withContext(PlatformDispatchers.IO) { sanitizeRssSources(export.rssSources) }
        val localFollows = withContext(PlatformDispatchers.IO) { sanitizeLocalFollows(export.localFollows) }
        // keyword is the primary key: a backup must not overwrite (for example expire or disable) a
        // filter the person already has, so only keywords not stored yet are imported.
        val existingKeywords =
            appDatabase
                .keywordFilterDao()
                .selectAll()
                .first()
                .map { it.keyword }
                .toSet()
        val keywordFilters =
            sanitizeKeywordFilters(export.keywordFilters)
                .filterNot { it.keyword in existingKeywords }

        // Perform import within a transaction (automatically rolls back on error)
        // Note: account, search history and local follow inserts use OnConflictStrategy.REPLACE.
        // Keyword filters and RSS sources never replace an existing row (see below).
        appDatabase.connect {
            export.accounts.forEach { appDatabase.accountDao().insert(it) }

            keywordFilters.forEach { appDatabase.keywordFilterDao().insert(it) }

            export.searchHistories.forEach { appDatabase.searchHistoryDao().insert(it) }

            if (rssSources.isNotEmpty()) {
                // Row ids from the file are ignored (id = 0) and feeds already subscribed by address are
                // kept as they are, so a backup can never take over an existing subscription.
                appDatabase.rssSourceDao().insertAllIfNew(rssSources)
            }

            export.localFollowGroups.forEach { appDatabase.localFollowDao().insertGroup(it) }

            localFollows.forEach { appDatabase.localFollowDao().insertFollow(it) }
        }
    }

    private fun sanitizeRssSources(sources: List<DbRssSources>): List<DbRssSources> =
        sources
            .filter { it.url.isHttpUrl() }
            .map { source ->
                source.copy(
                    id = 0,
                    icon = source.icon.takeIf { PublicUrlValidator.isSafePublicHttpUrl(it) },
                )
            }

    private fun String.isHttpUrl(): Boolean =
        trim().let { it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true) }

    private fun sanitizeLocalFollows(follows: List<DbLocalFollow>): List<DbLocalFollow> =
        follows.map { follow ->
            follow.copy(avatar = follow.avatar.takeIf { PublicUrlValidator.isSafePublicHttpUrl(it) })
        }

    private fun sanitizeKeywordFilters(filters: List<DbKeywordFilter>): List<DbKeywordFilter> =
        filters
            .filter { filter ->
                val isRegex = filter.is_regex == 1L
                isSafeKeywordFilter(
                    keyword = filter.keyword,
                    isRegex = isRegex,
                    regex = if (isRegex) filter.keyword.toFilterRegexOrNull() else null,
                )
            }.take(MAX_TIMELINE_KEYWORD_FILTERS)

    // Compiled the same way LocalFilterRepository compiles stored regex filters.
    private fun String.toFilterRegexOrNull(): Regex? =
        try {
            Regex(this, setOf(RegexOption.IGNORE_CASE))
        } catch (_: IllegalArgumentException) {
            null
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
