package org.crux.data.datasource.subscription

import io.ktor.http.Url
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import org.crux.common.Locale
import org.crux.data.database.app.model.RssDisplayMode
import org.crux.data.database.app.model.SubscriptionType
import org.crux.data.datasource.microblog.paging.CacheableRemoteLoader
import org.crux.data.datasource.rss.RssTimelineRemoteMediator
import org.crux.data.model.tab.RssTimelineData
import org.crux.data.model.tab.SubscriptionTimelineData
import org.crux.data.network.FaviconService
import org.crux.data.network.rss.DocumentData
import org.crux.data.network.rss.Readability
import org.crux.data.network.rss.RssService
import org.crux.data.network.rss.toRssWebUrl
import org.crux.data.platform.RssTimelineSpecs
import org.crux.data.repository.SettingsRepository
import org.crux.data.repository.SubscriptionRepository
import org.crux.data.repository.SubscriptionSourceInput
import org.crux.di.koinInject
import org.crux.model.PlatformRegistry
import org.crux.ui.model.UiRssSource
import org.crux.ui.model.UiTimelineV2
import org.crux.ui.model.mapper.title
import org.crux.ui.render.toUi
import kotlin.native.HiddenFromObjC
import kotlin.time.Instant

@HiddenFromObjC
public interface SubscriptionDataSource {
    public suspend fun listSources(): List<UiRssSource>

    public suspend fun detectSource(input: String): SubscriptionSourceDetection

    public fun createTimelineLoader(
        type: SubscriptionType,
        url: String,
    ): CacheableRemoteLoader<UiTimelineV2>

    public suspend fun saveSource(input: SubscriptionSourceInput): UiRssSource

    public suspend fun deleteSource(id: Int): UiRssSource?

    public suspend fun loadRssArticle(
        url: String,
        descriptionHtml: String? = null,
        descriptionTitle: String? = null,
        fallbackHtml: String? = null,
    ): DocumentData
}

@HiddenFromObjC
public sealed interface SubscriptionSourceDetection {
    @HiddenFromObjC
    public data object RssHub : SubscriptionSourceDetection

    @HiddenFromObjC
    public data class RssFeed(
        val title: String,
        val url: String,
        val icon: String?,
    ) : SubscriptionSourceDetection

    @HiddenFromObjC
    public data class RssSources(
        val sources: List<UiRssSource>,
    ) : SubscriptionSourceDetection

    @HiddenFromObjC
    public data class SubscriptionInstance(
        val host: String,
        val instanceName: String?,
        val icon: String?,
        val availableTimelines: List<SubscriptionType>,
    ) : SubscriptionSourceDetection
}

@HiddenFromObjC
public object KoinSubscriptionDataSource :
    SubscriptionDataSource {
    private val subscriptionRepository: SubscriptionRepository by koinInject()
    private val settingsRepository: SettingsRepository by koinInject()
    private val platformRegistry: PlatformRegistry by koinInject()
    private val readability = Readability()

    override suspend fun listSources(): List<UiRssSource> = subscriptionRepository.observeAll().first()

    override suspend fun detectSource(input: String): SubscriptionSourceDetection {
        val url = input.trim()
        require(url.isNotBlank()) {
            "Subscription URL or host is required."
        }
        if (url.startsWith("rsshub://", ignoreCase = true)) {
            return SubscriptionSourceDetection.RssHub
        }

        val actualUrl = url.toRssWebUrl()
        val feed =
            runCatching {
                RssService.fetch(actualUrl)
            }
        if (feed.isSuccess) {
            val fetched = feed.getOrThrow()
            return SubscriptionSourceDetection.RssFeed(
                title = fetched.title.trim(),
                url = actualUrl,
                icon =
                    runCatching {
                        RssService.iconFor(fetched, actualUrl)
                    }.getOrNull(),
            )
        }

        val linkSources =
            runCatching {
                coroutineScope {
                    RssService
                        .detectLinkSources(actualUrl)
                        .map { sourceUrl ->
                            async {
                                val fetched = RssService.fetch(sourceUrl)
                                UiRssSource(
                                    id = 0,
                                    url = sourceUrl,
                                    title = fetched.title.trim(),
                                    lastUpdate = Instant.DISTANT_PAST.toUi(),
                                    favIcon =
                                        runCatching {
                                            RssService.iconFor(fetched, sourceUrl)
                                        }.getOrNull(),
                                    displayMode = RssDisplayMode.FULL_CONTENT,
                                )
                            }
                        }.awaitAll()
                }
            }.getOrNull()

        if (!linkSources.isNullOrEmpty()) {
            return SubscriptionSourceDetection.RssSources(linkSources)
        }

        val host =
            runCatching {
                Url(actualUrl).host
            }.getOrNull() ?: throw IllegalArgumentException("Invalid URL: $input")
        return detectSubscriptionInstance(host)
            ?: throw IllegalArgumentException("URL is not a valid RSS feed or supported subscription instance: $input")
    }

    override fun createTimelineLoader(
        type: SubscriptionType,
        url: String,
    ): CacheableRemoteLoader<UiTimelineV2> =
        when (type) {
            SubscriptionType.RSS -> {
                RssTimelineRemoteMediator(
                    url = url,
                    fetchSource = {
                        subscriptionRepository.findByUrl(it).firstOrNull()
                    },
                )
            }

            else -> {
                platformRegistry
                    .requireSubscriptionTimelineSpec(type)
                    .createLoader(
                        host = url,
                        locale = Locale.language,
                    )
            }
        }

    override suspend fun saveSource(input: SubscriptionSourceInput): UiRssSource = subscriptionRepository.upsert(input)

    override suspend fun deleteSource(id: Int): UiRssSource? {
        val source = subscriptionRepository.delete(id)
        source?.let {
            settingsRepository.removeHomeTimelineTabBySourceId(it.timelineSourceId())
        }
        return source
    }

    override suspend fun loadRssArticle(
        url: String,
        descriptionHtml: String?,
        descriptionTitle: String?,
        fallbackHtml: String?,
    ): DocumentData = readability.load(url, descriptionHtml, descriptionTitle, fallbackHtml)

    private suspend fun detectSubscriptionInstance(host: String): SubscriptionSourceDetection.SubscriptionInstance? =
        coroutineScope {
            val timelines =
                platformRegistry
                    .subscriptionTimelineSpecs
                    .map { spec ->
                        async {
                            if (spec.isAvailable(host, Locale.language)) {
                                spec.type
                            } else {
                                null
                            }
                        }
                    }.awaitAll()
                    .filterNotNull()

            if (timelines.isEmpty()) {
                return@coroutineScope null
            }

            SubscriptionSourceDetection.SubscriptionInstance(
                host = host,
                instanceName = host,
                icon =
                    runCatching {
                        FaviconService.fetchIcon("https://$host/")
                    }.getOrNull(),
                availableTimelines = timelines,
            )
        }
}

private fun UiRssSource.timelineSourceId(): String =
    when (type) {
        SubscriptionType.RSS -> {
            RssTimelineSpecs.rss
                .itemId(RssTimelineData(url))
        }

        else -> {
            RssTimelineSpecs.subscription
                .itemId(
                    SubscriptionTimelineData(
                        subscriptionUrl = url,
                        subscriptionType = type,
                    ),
                )
        }
    }
