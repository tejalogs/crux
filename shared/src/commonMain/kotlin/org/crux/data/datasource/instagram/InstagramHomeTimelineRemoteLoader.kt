package org.crux.data.datasource.instagram

import kotlinx.coroutines.flow.Flow
import org.crux.data.database.cache.CacheDatabase
import org.crux.data.datasource.microblog.paging.CacheableRemoteLoader
import org.crux.data.datasource.microblog.paging.PagingRequest
import org.crux.data.datasource.microblog.paging.PagingResult
import org.crux.data.platform.InstagramCredential
import org.crux.di.koinGet
import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiTimelineV2
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
