package org.crux.data.datasource.rss

import org.crux.data.database.app.model.RssDisplayMode
import org.crux.data.datasource.microblog.paging.CacheableRemoteLoader
import org.crux.data.datasource.microblog.paging.PagingRequest
import org.crux.data.datasource.microblog.paging.PagingResult
import org.crux.data.network.rss.RssService
import org.crux.data.network.rss.model.Feed
import org.crux.ui.model.UiRssSource
import org.crux.ui.model.UiTimelineV2
import org.crux.ui.model.mapper.render
import org.crux.ui.model.mapper.resolvedLink
import org.crux.ui.model.mapper.title

internal class RssTimelineRemoteMediator(
    private val url: String,
    private val fetchFeed: suspend (String) -> Feed = RssService::fetch,
    private val fetchIcon: suspend (Feed, String) -> String? = RssService::iconFor,
    private val fetchSource: suspend (String) -> UiRssSource?,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String
        get() =
            buildString {
                append("rss_")
                append(url)
            }

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> {
        val rssSource = fetchSource(url)
        val response = fetchFeed(url)
        val title = rssSource?.title ?: response.title
        val icon = rssSource?.favIcon ?: fetchIcon(response, url)
        val displayMode = rssSource?.displayMode ?: RssDisplayMode.FULL_CONTENT
        val content =
            when (response) {
                is Feed.Atom -> {
                    response.entries.filter { it.resolvedLink() != null }.map {
                        it.render(
                            sourceName = title,
                            sourceIcon = icon,
                            displayMode = displayMode,
                            sourceLanguage = null,
                        )
                    }
                }

                is Feed.RDF -> {
                    response.items.filter { it.resolvedLink() != null }.map {
                        it.render(
                            sourceName = title,
                            sourceIcon = icon,
                            displayMode = displayMode,
                            sourceLanguage = null,
                        )
                    }
                }

                is Feed.Rss20 -> {
                    response.channel.items.filter { it.resolvedLink() != null }.map {
                        it.render(
                            sourceName = title,
                            sourceIcon = icon,
                            displayMode = displayMode,
                            sourceLanguage = response.channel.language,
                        )
                    }
                }
            }

        return PagingResult(
            endOfPaginationReached = true,
            data = content,
        )
    }
}
