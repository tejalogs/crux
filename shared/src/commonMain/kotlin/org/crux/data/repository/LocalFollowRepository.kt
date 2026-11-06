package org.crux.data.repository

import kotlinx.coroutines.flow.Flow
import org.crux.data.database.app.AppDatabase
import org.crux.data.database.app.model.DbLocalFollow
import org.crux.data.database.app.model.DbLocalFollowGroup
import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiProfile
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Local follows and groups. Everything stays in app.db; nothing here touches the network. */
@Single
internal class LocalFollowRepository(
    private val database: AppDatabase,
) {
    private val dao get() = database.localFollowDao()

    fun groups(): Flow<List<DbLocalFollowGroup>> = dao.groups()

    fun follows(): Flow<List<DbLocalFollow>> = dao.follows()

    fun follows(groupId: String?): Flow<List<DbLocalFollow>> = if (groupId == null) dao.follows() else dao.followsInGroup(groupId)

    fun follow(
        accountKey: MicroBlogKey,
        userKey: MicroBlogKey,
    ): Flow<DbLocalFollow?> = dao.follow(accountKey, userKey)

    suspend fun save(
        accountKey: MicroBlogKey,
        profile: UiProfile,
        groupId: String?,
    ) {
        dao.insertFollow(
            DbLocalFollow(
                accountKey = accountKey,
                userKey = profile.key,
                platformId = profile.platformId,
                handle = profile.handle.canonical,
                displayName = profile.name.innerText,
                avatar = profile.avatar?.url,
                groupId = groupId,
                createdAt = now(),
            ),
        )
    }

    suspend fun remove(
        accountKey: MicroBlogKey,
        userKey: MicroBlogKey,
    ) {
        dao.deleteFollow(accountKey, userKey)
    }

    @OptIn(ExperimentalUuidApi::class)
    suspend fun createGroup(name: String): String {
        val id = Uuid.random().toString()
        dao.insertGroup(DbLocalFollowGroup(id = id, name = name.trim(), createdAt = now()))
        return id
    }

    suspend fun renameGroup(
        id: String,
        name: String,
    ) {
        dao.renameGroup(id, name.trim())
    }

    suspend fun deleteGroup(id: String) {
        dao.deleteGroup(id)
    }

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()
}
