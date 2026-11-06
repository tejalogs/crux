package org.crux.data.database.app.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow
import org.crux.data.database.app.model.DbLocalFollow
import org.crux.data.database.app.model.DbLocalFollowGroup
import org.crux.model.MicroBlogKey

@Dao
internal interface LocalFollowDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFollow(follow: DbLocalFollow)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: DbLocalFollowGroup)

    @Query("SELECT * FROM DbLocalFollow ORDER BY createdAt DESC")
    fun follows(): Flow<List<DbLocalFollow>>

    @Query("SELECT * FROM DbLocalFollow WHERE groupId = :groupId ORDER BY createdAt DESC")
    fun followsInGroup(groupId: String): Flow<List<DbLocalFollow>>

    @Query("SELECT * FROM DbLocalFollow WHERE accountKey = :accountKey AND userKey = :userKey")
    fun follow(
        accountKey: MicroBlogKey,
        userKey: MicroBlogKey,
    ): Flow<DbLocalFollow?>

    @Query("SELECT * FROM DbLocalFollowGroup ORDER BY createdAt ASC")
    fun groups(): Flow<List<DbLocalFollowGroup>>

    @Query("DELETE FROM DbLocalFollow WHERE accountKey = :accountKey AND userKey = :userKey")
    suspend fun deleteFollow(
        accountKey: MicroBlogKey,
        userKey: MicroBlogKey,
    )

    @Query("UPDATE DbLocalFollowGroup SET name = :name WHERE id = :id")
    suspend fun renameGroup(
        id: String,
        name: String,
    )

    @Query("UPDATE DbLocalFollow SET groupId = NULL WHERE groupId = :id")
    suspend fun ungroupFollows(id: String)

    @Query("DELETE FROM DbLocalFollowGroup WHERE id = :id")
    suspend fun deleteGroupRow(id: String)

    // Deleting a group keeps its accounts; they just become ungrouped.
    @Transaction
    suspend fun deleteGroup(id: String) {
        ungroupFollows(id)
        deleteGroupRow(id)
    }
}
