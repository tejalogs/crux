package dev.dimension.flare.data.datasource.instagram

import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.PostContextLoader
import dev.dimension.flare.data.platform.InstagramCredential
import dev.dimension.flare.di.koinGet
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.flow.Flow
import kotlin.native.HiddenFromObjC

/**
 * Loads an Instagram post along with its context from local cache.
 */
@HiddenFromObjC
public class InstagramContextRemoteLoader(
    override val statusKey: MicroBlogKey,
    override val accountKey: MicroBlogKey,
    private val service: InstagramFeedService = InstagramFeedService(),
    private val credentialFlow: Flow<InstagramCredential>,
    private val syncService: InstagramSyncService? = null,
) : PostContextLoader {
    override val pagingKey: String = "instagram_context_${statusKey}_$accountKey"

    private val resolvedSyncService: InstagramSyncService? by lazy {
        syncService ?: runCatching { koinGet<InstagramSyncService>() }.getOrNull()
    }

    private fun findCachedMedia(targetId: String): InstagramMediaOrAd? {
        val cleanId = targetId.substringBefore("#")
        val numericTarget = InstagramFeedService.resolveNumericMediaId(cleanId)
        return resolvedSyncService?.getLatestCachedItems()?.firstOrNull { item ->
            item.id == cleanId ||
                item.pk == cleanId ||
                item.code == cleanId ||
                item.pk == numericTarget ||
                item.id.substringBefore("_") == numericTarget
        }
    }

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val cleanTargetId = statusKey.id.substringBefore("#")

        return when (request) {
            is PagingRequest.Prepend -> {
                PagingResult(endOfPaginationReached = true)
            }

            PagingRequest.Refresh -> {
                val cachedMedia =
                    findCachedMedia(cleanTargetId)
                        ?: return PagingResult(endOfPaginationReached = true)

                val focalItem =
                    InstagramTimelineMapper.render(
                        mediaOrAd = cachedMedia,
                        accountKey = accountKey,
                        statusKeyOverride = statusKey,
                    ) ?: return PagingResult(endOfPaginationReached = true)

                PagingResult(
                    endOfPaginationReached = true,
                    data = listOf(focalItem),
                    nextKey = null,
                )
            }

            is PagingRequest.Append -> {
                PagingResult(endOfPaginationReached = true)
            }
        }
    }
}
