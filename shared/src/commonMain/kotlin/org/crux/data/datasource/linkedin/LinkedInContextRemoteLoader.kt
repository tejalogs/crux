package org.crux.data.datasource.linkedin

import kotlinx.coroutines.flow.Flow
import org.crux.data.datasource.microblog.loader.PostLoader
import org.crux.data.datasource.microblog.paging.PagingRequest
import org.crux.data.datasource.microblog.paging.PagingResult
import org.crux.data.datasource.microblog.paging.PostContextLoader
import org.crux.data.platform.LinkedInCredential
import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiTimelineV2
import kotlin.native.HiddenFromObjC

/**
 * Loads a LinkedIn post along with its context from local cache.
 */
@HiddenFromObjC
public class LinkedInContextRemoteLoader(
    override val statusKey: MicroBlogKey,
    override val accountKey: MicroBlogKey,
    private val service: LinkedInFeedService = LinkedInFeedService(),
    private val credentialFlow: Flow<LinkedInCredential>,
    private val postLoader: PostLoader,
    public val syncService: LinkedInSyncService? = null,
) : PostContextLoader {
    override val pagingKey: String = "linkedin_context_${statusKey}_$accountKey"
    override val supportPrepend: Boolean = false
    override val collapseReplyChains: Boolean = false

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        return when (request) {
            is PagingRequest.Prepend -> {
                PagingResult(endOfPaginationReached = true)
            }

            PagingRequest.Refresh -> {
                val rawFocalItem =
                    runCatching { postLoader.status(statusKey) }.getOrNull()
                        ?: return PagingResult(endOfPaginationReached = true)

                // Ensure focalItem statusKey exactly matches statusKey for PostContextLoader anchor requirement
                val focalItem =
                    if (rawFocalItem.statusKey != statusKey && rawFocalItem is UiTimelineV2.TimelinePostItem) {
                        rawFocalItem.copy(post = rawFocalItem.post.copy(statusKey = statusKey))
                    } else {
                        rawFocalItem
                    }

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
