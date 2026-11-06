package org.crux.data.platform

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.crux.data.database.app.model.SubscriptionType
import org.crux.data.datasource.microblog.paging.RemoteLoader
import org.crux.data.datasource.rss.RssDataSource
import org.crux.data.model.tab.AllRssTimelineData
import org.crux.data.model.tab.RssTimelineData
import org.crux.data.model.tab.SubscriptionTimelineData
import org.crux.data.model.tab.TimelineLoaderContext
import org.crux.data.model.tab.TimelineLoaderFactory
import org.crux.data.model.tab.TimelineSpec
import org.crux.data.model.tab.TimelineSpecIds
import org.crux.data.model.tab.remoteLoaderFactory
import org.crux.data.repository.SubscriptionRepository
import org.crux.data.subscription.SubscriptionTimelineLoaderFactory
import org.crux.di.koinInject
import org.crux.ui.model.UiIcon
import org.crux.ui.model.UiStrings
import org.crux.ui.model.UiTimelineV2
import org.crux.ui.model.asType
import org.koin.core.annotation.Single
import kotlin.native.HiddenFromObjC

public data object RssTimelineSpecs {
    public val rss: TimelineSpec<RssTimelineData> =
        TimelineSpec(
            id = TimelineSpecIds.RSS_FEED,
            title = UiStrings.Rss,
            icon = UiIcon.Rss.asType(),
            serializer = RssTimelineData.serializer(),
            targetId = { it.feedUrl },
            loaderFactory = remoteLoaderFactory { RssDataSource.fetchLoader(it.feedUrl) },
        )

    public val allRss: TimelineSpec<AllRssTimelineData> = allRss(KoinAllRssTimelineLoaderFactory)

    public fun allRss(loaderFactory: TimelineLoaderFactory<AllRssTimelineData>): TimelineSpec<AllRssTimelineData> =
        TimelineSpec(
            id = TimelineSpecIds.RSS_ALL,
            title = UiStrings.AllRssFeeds,
            icon = UiIcon.Rss.asType(),
            serializer = AllRssTimelineData.serializer(),
            targetId = { "all" },
            loaderFactory = loaderFactory,
        )

    public val subscription: TimelineSpec<SubscriptionTimelineData> =
        TimelineSpec(
            id = TimelineSpecIds.RSS_SUBSCRIPTION,
            title = UiStrings.Rss,
            icon = UiIcon.Rss.asType(),
            serializer = SubscriptionTimelineData.serializer(),
            targetId = { "${it.subscriptionType.name}:${it.subscriptionUrl}" },
            loaderFactory =
                remoteLoaderFactory {
                    RssDataSource.fetchLoader(it.subscriptionType, it.subscriptionUrl)
                },
        )

    public fun timelineSpecs(
        allRssTimelineLoaderFactory: TimelineLoaderFactory<AllRssTimelineData>,
    ): ImmutableList<TimelineSpec<out TimelineSpec.Data>> =
        persistentListOf(
            rss,
            allRss(allRssTimelineLoaderFactory),
            subscription,
        )
}

@HiddenFromObjC
@Single
public class AllRssTimelineLoaderFactory(
    private val subscriptionRepository: SubscriptionRepository,
    private val subscriptionTimelineLoaderFactory: SubscriptionTimelineLoaderFactory,
) : TimelineLoaderFactory<AllRssTimelineData> {
    override fun create(
        data: AllRssTimelineData,
        context: TimelineLoaderContext,
    ): Flow<RemoteLoader<UiTimelineV2>> =
        subscriptionRepository
            .observeAll()
            .map { items ->
                subscriptionTimelineLoaderFactory.mixedTimeline(
                    loaders =
                        items
                            .filter { it.type == SubscriptionType.RSS }
                            .map { RssDataSource.fetchLoader(it) },
                )
            }
}

private object KoinAllRssTimelineLoaderFactory :
    TimelineLoaderFactory<AllRssTimelineData> {
    private val delegate: AllRssTimelineLoaderFactory by koinInject()

    override fun create(
        data: AllRssTimelineData,
        context: TimelineLoaderContext,
    ): Flow<RemoteLoader<UiTimelineV2>> = delegate.create(data, context)
}
