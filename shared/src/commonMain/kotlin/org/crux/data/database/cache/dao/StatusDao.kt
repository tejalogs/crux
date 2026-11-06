package org.crux.data.database.cache.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow
import org.crux.data.database.cache.model.DbStatus
import org.crux.data.database.cache.model.DbStatusContent
import org.crux.data.database.cache.model.DbStatusWithReference
import org.crux.model.DbAccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiTimelineV2

internal data class DbStatusVersion(
    val id: String,
    val contentFingerprint: Long,
    val renderHash: Int,
)

@Dao
internal interface StatusDao {
    @Upsert
    suspend fun insert(status: DbStatus)

    @Upsert
    suspend fun insertAll(statuses: List<DbStatus>)

    @Insert
    suspend fun insertNew(statuses: List<DbStatus>)

    @Update
    suspend fun updateExisting(statuses: List<DbStatus>)

    @Query("SELECT * FROM DbStatus WHERE statusKey = :statusKey AND accountType = :accountType")
    fun get(
        statusKey: MicroBlogKey,
        accountType: DbAccountType,
    ): Flow<DbStatus?>

    @Transaction
    @Query("SELECT * FROM DbStatus WHERE statusKey = :statusKey AND accountType = :accountType")
    fun getWithReferences(
        statusKey: MicroBlogKey,
        accountType: DbAccountType,
    ): Flow<DbStatusWithReference?>

    @Transaction
    @Query("SELECT * FROM DbStatus WHERE statusKey = :statusKey AND accountType = :accountType")
    suspend fun getWithReferencesSync(
        statusKey: MicroBlogKey,
        accountType: DbAccountType,
    ): DbStatusWithReference?

    @Query("SELECT * FROM DbStatus WHERE accountType = :accountType AND statusKey IN (:statusKeys)")
    suspend fun getByKeys(
        statusKeys: List<MicroBlogKey>,
        accountType: DbAccountType,
    ): List<DbStatus>

    @Query("SELECT id, contentFingerprint, renderHash FROM DbStatus WHERE id IN (:ids)")
    suspend fun getVersions(ids: List<String>): List<DbStatusVersion>

    @Query(
        "UPDATE DbStatus SET content = :content, contentFingerprint = :contentFingerprint, " +
            "renderHash = :renderHash, text = :text " +
            "WHERE statusKey = :statusKey AND accountType = :accountType",
    )
    suspend fun updateStoredContent(
        statusKey: MicroBlogKey,
        accountType: DbAccountType,
        content: DbStatusContent,
        contentFingerprint: Long,
        renderHash: Int,
        text: String?,
    )

    suspend fun update(
        statusKey: MicroBlogKey,
        accountType: DbAccountType,
        content: UiTimelineV2,
        renderHash: Int,
        text: String?,
    ) {
        val storedContent = DbStatusContent.encode(content)
        updateStoredContent(
            statusKey = statusKey,
            accountType = accountType,
            content = storedContent,
            contentFingerprint = storedContent.fingerprint,
            renderHash = renderHash,
            text = text,
        )
    }

    @Query("DELETE FROM DbStatus WHERE statusKey = :statusKey AND accountType = :accountType")
    suspend fun delete(
        statusKey: MicroBlogKey,
        accountType: DbAccountType,
    )

    @Query("DELETE FROM DbStatus WHERE accountType = :accountType")
    suspend fun deleteByAccountType(accountType: DbAccountType)

    @Query("SELECT COUNT(*) FROM DbStatus")
    fun count(): Flow<Long>

    @Query("DELETE FROM DbStatus")
    suspend fun clear()

    @Query(
        "DELETE FROM DbStatus " +
            "WHERE id NOT IN (SELECT statusId FROM DbPagingTimeline) " +
            "AND id NOT IN (SELECT referenceStatusId FROM timeline_item_presentation_reference) " +
            "AND id NOT IN (SELECT referenceStatusId FROM status_reference)",
    )
    suspend fun pruneOrphanedStatuses(): Int
}
