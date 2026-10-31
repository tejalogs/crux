package dev.dimension.flare.data.datasource.linkedin

import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.platform.LinkedInCredential
import dev.dimension.flare.di.koinGet
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.flow.Flow
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
