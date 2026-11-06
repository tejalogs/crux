package org.crux.data.database.cache.dao

import androidx.paging.PagingSource
import androidx.room3.Dao
import androidx.room3.DaoReturnTypeConverters
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.paging.PagingSourceDaoReturnTypeConverter
import kotlinx.coroutines.flow.Flow
import org.crux.data.database.cache.model.DbList
import org.crux.data.database.cache.model.DbListMember
import org.crux.data.database.cache.model.DbListMemberWithContent
import org.crux.data.database.cache.model.DbListPaging
import org.crux.data.database.cache.model.DbListWithContent
import org.crux.data.database.cache.model.DbUserWithListMembership
import org.crux.model.DbAccountType
import org.crux.model.MicroBlogKey

@Dao
@DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter::class)
internal interface ListDao {
    @Transaction
    @Query(
        "SELECT * FROM DbListPaging WHERE pagingKey = :pagingKey",
    )
    fun getPagingSource(pagingKey: String): PagingSource<Int, DbListWithContent>

    @Transaction
    @Query("SELECT * FROM DbListPaging WHERE pagingKey = :pagingKey")
    fun getListKeysFlow(pagingKey: String): Flow<List<DbListWithContent>>

    @Query("SELECT * FROM DbList WHERE listKey = :listKey AND accountType = :accountType")
    fun getList(
        listKey: MicroBlogKey,
        accountType: DbAccountType,
    ): Flow<DbList?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllPaging(timelines: List<DbListPaging>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllList(lists: List<DbList>)

    @Query("DELETE FROM DbListPaging WHERE pagingKey = :pagingKey")
    suspend fun deleteByPagingKey(pagingKey: String)

    @Query("DELETE FROM DbList WHERE accountType = :accountType")
    suspend fun deleteByAccountType(accountType: String)

    @Query("DELETE FROM DbList WHERE listKey = :listKey AND accountType = :accountType")
    suspend fun deleteByListKey(
        listKey: MicroBlogKey,
        accountType: DbAccountType,
    )

    @Query("DELETE FROM DbListPaging WHERE listKey = :listKey AND accountType = :accountType")
    suspend fun deletePagingByListKey(
        listKey: MicroBlogKey,
        accountType: DbAccountType,
    )

    @Query("DELETE FROM DbList")
    suspend fun clearAllLists()

    @Query("DELETE FROM DbListPaging")
    suspend fun clearAllListPaging()

    @Query("UPDATE DbList SET content = :content WHERE listKey = :listKey AND accountType = :accountType")
    suspend fun updateListContent(
        listKey: MicroBlogKey,
        accountType: DbAccountType,
        content: DbList.ListContent,
    )

    @Transaction
    @Query(
        "SELECT DbListMember.* FROM DbListMember " +
            "INNER JOIN DbUser ON DbUser.userKey = DbListMember.memberKey " +
            "WHERE DbListMember.listKey = :listKey",
    )
    fun getListMembers(listKey: MicroBlogKey): PagingSource<Int, DbListMemberWithContent>

    @Transaction
    @Query(
        "SELECT DbListMember.* FROM DbListMember " +
            "INNER JOIN DbUser ON DbUser.userKey = DbListMember.memberKey " +
            "WHERE DbListMember.listKey = :listKey",
    )
    fun getListMembersFlow(listKey: MicroBlogKey): Flow<List<DbListMemberWithContent>>

    @Query("SELECT memberKey FROM DbListMember WHERE listKey = :listKey")
    fun getListMemberKeysFlow(listKey: MicroBlogKey): Flow<List<MicroBlogKey>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllMember(members: List<DbListMember>)

    @Query("DELETE FROM DbListMember WHERE listKey = :listKey")
    suspend fun deleteMembersByListKey(listKey: MicroBlogKey)

    @Query("DELETE FROM DbListMember WHERE memberKey = :memberKey AND listKey = :listKey")
    suspend fun deleteMemberFromList(
        memberKey: MicroBlogKey,
        listKey: MicroBlogKey,
    )

    @Transaction
    @Query("SELECT * FROM DbUser WHERE userKey = :userKey")
    fun getUserByKey(userKey: MicroBlogKey): PagingSource<Int, DbUserWithListMembership>

    @Transaction
    @Query("SELECT * FROM DbUser WHERE userKey = :userKey")
    fun getUserByKeyFlow(userKey: MicroBlogKey): Flow<DbUserWithListMembership>
}
