package dev.dimension.flare.data.datasource.linkedin

import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.platform.LinkedInCredential
import dev.dimension.flare.di.koinGet
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

public class LinkedInHomeTimelineRemoteLoader(
    private val service: LinkedInFeedService,
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

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val credential = credentialFlow.firstOrNull() ?: return PagingResult(endOfPaginationReached = true)
        val cookies = credential.cookies
        if (cookies.isBlank()) {
            return PagingResult(endOfPaginationReached = true)
        }

        val cursor = when (request) {
            PagingRequest.Refresh -> null
            is PagingRequest.Prepend -> return PagingResult(endOfPaginationReached = true)
            is PagingRequest.Append -> request.nextKey
        }

        val response = service.getHomeTimeline(
            cookies = cookies,
            cursor = cursor,
        )

        val networkUpdates = response?.elements.orEmpty()
        val allCached = resolvedSyncService?.getLatestCachedUpdates().orEmpty()

        val updates = if (networkUpdates.isNotEmpty()) {
            resolvedSyncService?.cacheUpdates(networkUpdates)
            networkUpdates
        } else if (allCached.isNotEmpty()) {
            val startIndex = cursor?.toIntOrNull() ?: 0
            allCached.drop(startIndex).take(pageSize)
        } else {
            emptyList()
        }

        val renderedPosts = updates.mapNotNull { update ->
            LinkedInTimelineMapper.render(
                update = update,
                accountKey = accountKey,
            )
        }

        val currentStart = cursor?.toIntOrNull() ?: 0
        val nextKey = if (networkUpdates.isNotEmpty()) {
            response?.metadata?.paginationToken ?: (currentStart + updates.size).toString()
        } else if (allCached.isNotEmpty() && currentStart + updates.size < allCached.size) {
            (currentStart + updates.size).toString()
        } else {
            null
        }

        val endReached = updates.isEmpty() || nextKey == null

        return PagingResult(
            endOfPaginationReached = endReached,
            data = renderedPosts,
            nextKey = nextKey,
        )
    }
}
