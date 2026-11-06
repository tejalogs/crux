package org.crux.data.database.app.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow
import org.crux.data.database.app.model.DbSearchHistory

@Dao
internal interface SearchHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(searchHistory: DbSearchHistory)

    @Query("SELECT * FROM DbSearchHistory ORDER BY created_at DESC")
    fun select(): Flow<List<DbSearchHistory>>

    @Query("DELETE FROM DbSearchHistory WHERE search = :search")
    suspend fun delete(search: String)

    @Query("DELETE FROM DbSearchHistory")
    suspend fun deleteAll()
}
