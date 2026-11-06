package org.crux.ui.presenter.home.rss

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.crux.data.database.app.model.RssDisplayMode
import org.crux.data.network.rss.RssService
import org.crux.data.network.rss.model.Feed
import org.crux.data.network.rss.toRssWebUrl
import org.crux.ui.model.UiRssSource
import org.crux.ui.model.UiState
import org.crux.ui.model.flattenUiState
import org.crux.ui.model.mapper.title
import org.crux.ui.presenter.PresenterBase
import org.crux.ui.presenter.home.rss.CheckRssSourcePresenter.State.RssState
import org.crux.ui.render.toUi
import kotlin.time.Instant

public class CheckRssSourcePresenter internal constructor(
    private val url: String,
    private val fetchFeed: suspend (String) -> Feed,
    private val fetchIcon: suspend (String) -> String?,
    private val detectLinkSources: suspend (String) -> List<String>,
) : PresenterBase<CheckRssSourcePresenter.State>() {
    public constructor(url: String) : this(url, RssService::fetch, RssService::fetchIcon, RssService::detectLinkSources)

    internal val feeds: MutableMap<String, Feed> = mutableMapOf()

    @Immutable
    public interface State {
        public val state: UiState<RssState>

        public sealed interface RssState {
            public data object RssHub : RssState

            public data class RssFeed(
                val title: String,
                val url: String,
                val icon: String?,
            ) : RssState

            public data class RssSources(
                val sources: ImmutableList<UiRssSource>,
            ) : RssState
        }
    }

    internal val flow: Flow<UiState<RssState>> by lazy {
        flow {
            emit(UiState.Loading())
            delay(666)
            if (url.isBlank()) return@flow
            rssCheck {
                val instagramUser = extractInstagramUsername(url)
                if (url.startsWith("rsshub://", ignoreCase = true) || instagramUser != null) {
                    State.RssState.RssHub
                } else {
                    val actualUrl = url.toRssWebUrl()
                    val feed =
                        rssCheck {
                            fetchFeed(actualUrl).also { feeds[actualUrl] = it }
                        }
                    if (feed.isSuccess) {
                        val icon =
                            rssCheck {
                                fetchIcon(actualUrl)
                            }.getOrNull()
                        RssState.RssFeed(
                            feed.getOrThrow().title.trim(),
                            actualUrl,
                            icon,
                        )
                    } else {
                        val linkSources =
                            rssCheck {
                                coroutineScope {
                                    detectLinkSources(actualUrl)
                                        .map {
                                            async {
                                                val icon =
                                                    rssCheck {
                                                        fetchIcon(it)
                                                    }.getOrNull()
                                                val feed = fetchFeed(it)
                                                UiRssSource(
                                                    id = 0,
                                                    url = it,
                                                    title = feed.title.trim(),
                                                    lastUpdate = Instant.DISTANT_PAST.toUi(),
                                                    favIcon = icon,
                                                    displayMode = RssDisplayMode.FULL_CONTENT,
                                                ) to feed
                                            }
                                        }.awaitAll()
                                        .map { (source, feed) ->
                                            feeds[source.url] = feed
                                            source
                                        }.toImmutableList()
                                }
                            }.getOrNull()

                        if (!linkSources.isNullOrEmpty()) {
                            RssState.RssSources(linkSources)
                        } else {
                            throw IllegalArgumentException("URL is not a valid RSS feed: $url")
                        }
                    }
                }
            }.onFailure {
                emit(UiState.Error(it))
            }.onSuccess {
                emit(UiState.Success(it))
            }
        }
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    @Composable
    override fun body(): State {
        val state by flow.flattenUiState()
        return object : State {
            override val state: UiState<RssState>
                get() = state
        }
    }
}

private inline fun <T> rssCheck(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }

internal fun extractInstagramUsername(url: String): String? {
    val trimmed = url.trim()
    val regex = Regex("""^(?:https?://)?(?:www\.)?instagram\.com/([a-zA-Z0-9._]+)/?$""", RegexOption.IGNORE_CASE)
    val match = regex.matchEntire(trimmed) ?: return null
    val username = match.groupValues[1]
    val reserved =
        setOf("p", "reel", "reels", "stories", "explore", "accounts", "direct", "tv", "developer", "about", "legal", "terms", "privacy")
    if (reserved.contains(username.lowercase())) return null
    return username
}
