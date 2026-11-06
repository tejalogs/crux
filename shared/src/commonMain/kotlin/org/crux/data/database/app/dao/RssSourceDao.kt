package org.crux.data.database.app.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow
import org.crux.data.database.app.model.DbRssSources
import org.crux.data.database.app.model.SubscriptionType

@Dao
internal interface RssSourceDao {
    @Insert(onConflict = androidx.room3.OnConflictStrategy.REPLACE)
    suspend fun insert(data: DbRssSources)

    @Insert(onConflict = androidx.room3.OnConflictStrategy.REPLACE)
    suspend fun insertAll(data: List<DbRssSources>)

    @Query("SELECT * FROM DbRssSources")
    fun getAll(): Flow<List<DbRssSources>>

    @Query("SELECT * FROM DbRssSources")
    suspend fun getAllOnce(): List<DbRssSources>

    @Query("SELECT * FROM DbRssSources WHERE type = :type")
    fun getAllByType(type: SubscriptionType): Flow<List<DbRssSources>>

    // Rows left behind by the removed Mastodon subscription types: nothing can load them any more.
    @Query("DELETE FROM DbRssSources WHERE type <> :type")
    suspend fun deleteAllExceptType(type: SubscriptionType)

    @Query("DELETE FROM DbRssSources WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("SELECT * FROM DbRssSources WHERE id = :id")
    fun get(id: Int): Flow<DbRssSources>

    @Query("SELECT * FROM DbRssSources WHERE url = :url")
    suspend fun getByUrl(url: String): List<DbRssSources>

    @Query("SELECT * FROM DbRssSources WHERE url = :url AND type = :type")
    suspend fun getByUrlAndType(
        url: String,
        type: SubscriptionType,
    ): List<DbRssSources>

    /**
     * Inserts [data] unless another row of the same type already holds the same feed address, then
     * returns the row that now represents it. Check and insert share one transaction, so two
     * concurrent saves (an OPML import racing a manual add) cannot both insert.
     */
    @Transaction
    suspend fun insertIfNew(data: DbRssSources): DbRssSources {
        val key = normalizeFeedUrl(data.url)
        val existing =
            data.takeIf { it.id == 0 }?.let {
                getAllOnce().firstOrNull { row -> row.type == data.type && normalizeFeedUrl(row.url) == key }
            }
        if (existing != null) return existing
        insert(data)
        return data
    }

    /** [insertIfNew] for many rows at once; duplicates inside [data] collapse too. */
    @Transaction
    suspend fun insertAllIfNew(data: List<DbRssSources>): List<DbRssSources> = data.map { insertIfNew(it) }
}

private val FEED_URL_PARTS = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*)://([^/?#]*)([^?]*)(?:\\?(.*))?$")

/**
 * A key for comparing feed addresses: scheme and host in lower case, no fragment, no default port
 * and no trailing slash on the path. Anything that is not a plain URL is only trimmed.
 */
internal fun normalizeFeedUrl(url: String): String {
    val trimmed = url.trim().substringBefore('#')
    val match = FEED_URL_PARTS.matchEntire(trimmed) ?: return trimmed
    val scheme = match.groupValues[1].lowercase()
    var authority = match.groupValues[2].lowercase()
    if ((scheme == "http" && authority.endsWith(":80")) || (scheme == "https" && authority.endsWith(":443"))) {
        authority = authority.substringBeforeLast(':')
    }
    val path = match.groupValues[3].trimEnd('/')
    val query = match.groupValues[4]
    return buildString {
        append(scheme).append("://").append(authority).append(path)
        if (query.isNotEmpty()) append('?').append(query)
    }
}
