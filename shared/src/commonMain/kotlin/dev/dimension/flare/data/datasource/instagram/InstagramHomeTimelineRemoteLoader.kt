package dev.dimension.flare.data.datasource.instagram

import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.platform.InstagramCredential
import dev.dimension.flare.di.koinGet
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

public class InstagramHomeTimelineRemoteLoader(
    private val service: InstagramFeedService,
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

        val candidateItems = mutableListOf<InstagramMediaOrAd>()

        if (response != null) {
            // 1. Check feed_items
            response.feed_items.forEach { feedItem ->
                if (feedItem.is_ad != true && feedItem.invalidation_type == null) {
                    feedItem.media_or_ad?.let { candidateItems.add(it) }
                }
            }

            // 2. Check items if feed_items was empty
            if (candidateItems.isEmpty() && response.items.isNotEmpty()) {
                response.items.forEach { item ->
                    candidateItems.add(item)
                }
            }
        }

        val networkItems = candidateItems.toList()
        val allCached = resolvedSyncService?.getLatestCachedItems().orEmpty()

        val items = if (networkItems.isNotEmpty()) {
            resolvedSyncService?.cacheItems(networkItems)
            networkItems
        } else if (allCached.isNotEmpty()) {
            val startIndex = cursor?.toIntOrNull() ?: 0
            allCached.drop(startIndex).take(pageSize)
        } else {
            emptyList()
        }

        val renderedPosts = items.mapNotNull { mediaOrAd ->
            InstagramTimelineMapper.render(
                mediaOrAd = mediaOrAd,
                accountKey = accountKey,
            )
        }

        val currentStart = cursor?.toIntOrNull() ?: 0
        val nextKey = if (networkItems.isNotEmpty()) {
            response?.next_max_id
        } else if (allCached.isNotEmpty() && currentStart + items.size < allCached.size) {
            (currentStart + items.size).toString()
        } else {
            null
        }

        val endReached = items.isEmpty() || (networkItems.isNotEmpty() && response?.more_available == false) || nextKey == null

        return PagingResult(
            endOfPaginationReached = endReached,
            data = renderedPosts,
            nextKey = nextKey,
        )
    }
}
