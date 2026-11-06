package org.crux.data.datasource.linkedin

import kotlinx.coroutines.flow.Flow
import org.crux.data.database.cache.CacheDatabase
import org.crux.data.datasource.microblog.paging.CacheableRemoteLoader
import org.crux.data.datasource.microblog.paging.PagingRequest
import org.crux.data.datasource.microblog.paging.PagingResult
import org.crux.data.platform.LinkedInCredential
import org.crux.di.koinGet
import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiTimelineV2
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public class LinkedInHomeTimelineRemoteLoader(
    private val service: LinkedInFeedService = LinkedInFeedService(),
    private val accountKey: MicroBlogKey,
    private val credentialFlow: Flow<LinkedInCredential>,
    private val syncService: LinkedInSyncService? = null,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "home_$accountKey"
    override val supportPrepend: Boolean = false
    override val collapseReplyChains: Boolean = false

    private val resolvedSyncService: LinkedInSyncService? by lazy {
        syncService ?: runCatching { koinGet<LinkedInSyncService>() }.getOrNull()
    }

    private val database: CacheDatabase? by lazy {
        runCatching { koinGet<CacheDatabase>() }.getOrNull()
    }

    // These platforms are live browser tabs now, so they add nothing to merged feeds.
    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> = PagingResult(endOfPaginationReached = true)
}
