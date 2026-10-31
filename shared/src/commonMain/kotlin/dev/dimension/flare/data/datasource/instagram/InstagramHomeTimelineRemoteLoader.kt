package dev.dimension.flare.data.datasource.instagram

import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.platform.InstagramCredential
import dev.dimension.flare.di.koinGet
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.flow.Flow
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public class InstagramHomeTimelineRemoteLoader(
    private val service: InstagramFeedService = InstagramFeedService(),
    private val accountKey: MicroBlogKey,
    private val credentialFlow: Flow<InstagramCredential>,
    private val syncService: InstagramSyncService? = null,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "home_$accountKey"
    override val supportPrepend: Boolean = false
    override val collapseReplyChains: Boolean = false

    private val resolvedSyncService: InstagramSyncService? by lazy {
        syncService ?: runCatching { koinGet<InstagramSyncService>() }.getOrNull()
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
