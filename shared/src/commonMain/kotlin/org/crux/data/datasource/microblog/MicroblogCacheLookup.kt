package org.crux.data.datasource.microblog

import kotlinx.coroutines.flow.firstOrNull
import org.crux.data.database.cache.CacheDatabase
import org.crux.model.AccountType
import org.crux.model.DbAccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiProfile
import org.crux.ui.model.UiTimelineV2
import org.crux.ui.model.contentPostOrNull
import org.koin.core.annotation.Single
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public interface MicroblogCacheLookup {
    public suspend fun findProfiles(keys: Collection<MicroBlogKey>): Map<MicroBlogKey, UiProfile>

    public suspend fun findPost(
        accountType: AccountType,
        statusKey: MicroBlogKey,
    ): UiTimelineV2.Post?
}

@Single(binds = [MicroblogCacheLookup::class])
internal class DatabaseMicroblogCacheLookup(
    private val database: CacheDatabase,
) : MicroblogCacheLookup {
    override suspend fun findProfiles(keys: Collection<MicroBlogKey>): Map<MicroBlogKey, UiProfile> {
        if (keys.isEmpty()) {
            return emptyMap()
        }
        return database
            .userDao()
            .findByKeys(keys.distinct())
            .firstOrNull()
            .orEmpty()
            .associate { it.userKey to it.content }
    }

    override suspend fun findPost(
        accountType: AccountType,
        statusKey: MicroBlogKey,
    ): UiTimelineV2.Post? =
        database
            .statusDao()
            .get(
                statusKey = statusKey,
                accountType = accountType as DbAccountType,
            ).firstOrNull()
            ?.content
            ?.contentPostOrNull()
}
