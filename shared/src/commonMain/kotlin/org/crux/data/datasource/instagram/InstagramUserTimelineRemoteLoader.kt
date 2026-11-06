package org.crux.data.datasource.instagram

import kotlinx.coroutines.flow.Flow
import org.crux.data.datasource.microblog.paging.CacheableRemoteLoader
import org.crux.data.datasource.microblog.paging.PagingRequest
import org.crux.data.datasource.microblog.paging.PagingResult
import org.crux.data.platform.InstagramCredential
import org.crux.di.koinGet
import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiTimelineV2
import kotlin.native.HiddenFromObjC

/**
 * Loads paginated Instagram posts for a specific user profile from local cache.
 */
@HiddenFromObjC
public class InstagramUserTimelineRemoteLoader(
    private val userKey: MicroBlogKey,
    private val accountKey: MicroBlogKey,
    private val service: InstagramFeedService = InstagramFeedService(),
    private val credentialFlow: Flow<InstagramCredential>,
    private val syncService: InstagramSyncService? = null,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "instagram_user_${userKey}_$accountKey"
    override val supportPrepend: Boolean = false
    override val collapseReplyChains: Boolean = false

    private val resolvedSyncService: InstagramSyncService? by lazy {
        syncService ?: runCatching { koinGet<InstagramSyncService>() }.getOrNull()
    }

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val cursor =
            when (request) {
                PagingRequest.Refresh -> null
                is PagingRequest.Prepend -> return PagingResult(endOfPaginationReached = true)
                is PagingRequest.Append -> request.nextKey
            }

        val allUserItems =
            (resolvedSyncService?.ensureLoaded() ?: resolvedSyncService?.getLatestCachedItems().orEmpty()).filter { item ->
                val author = item.user
                author?.resolvedId == userKey.id || author?.username.equals(userKey.id, ignoreCase = true)
            }

        if (allUserItems.isEmpty()) {
            return PagingResult(endOfPaginationReached = true)
        }

        val startIndex = cursor?.toIntOrNull() ?: 0
        val cachedBatch = allUserItems.drop(startIndex).take(pageSize)
        val renderedPosts =
            cachedBatch.mapNotNull { mediaOrAd ->
                InstagramTimelineMapper.render(
                    mediaOrAd = mediaOrAd,
                    accountKey = accountKey,
                )
            }

        val nextKey =
            if (startIndex + renderedPosts.size < allUserItems.size) {
                (startIndex + renderedPosts.size).toString()
            } else {
                null
            }

        return PagingResult(
            endOfPaginationReached = renderedPosts.isEmpty() || nextKey == null,
            data = renderedPosts,
            nextKey = nextKey,
        )
    }
}
