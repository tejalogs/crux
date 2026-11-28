package org.crux.rss

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.crux.data.model.tab.AllRssTimelineData
import org.crux.data.model.tab.RssTimelineData
import org.crux.data.model.tab.SubscriptionTimelineData
import org.crux.data.model.tab.TimelineSpec
import org.crux.data.model.tab.TimelineSpecIds
import org.crux.ui.model.UiIcon
import org.crux.ui.model.UiStrings
import org.crux.ui.model.asType

/**
 * Lightweight tab specs for Crux's native RSS feeds so saved TabSettings slots (`rss.all`,
 * `rss.feed`, legacy `rss.subscription`) deserialize seamlessly.
 * Rendering is handled directly by [org.crux.ui.screen.rss.NativeRssFeedList].
 */
internal object NativeRssTimelineSpecs {
    val rss: TimelineSpec<RssTimelineData> =
        TimelineSpec(
            id = TimelineSpecIds.RSS_FEED,
            title = UiStrings.Rss,
            icon = UiIcon.Rss.asType(),
            serializer = RssTimelineData.serializer(),
            targetId = { it.feedUrl },
        )

    val allRss: TimelineSpec<AllRssTimelineData> =
        TimelineSpec(
            id = TimelineSpecIds.RSS_ALL,
            title = UiStrings.AllRssFeeds,
            icon = UiIcon.Rss.asType(),
            serializer = AllRssTimelineData.serializer(),
            targetId = { "all" },
        )

    val subscription: TimelineSpec<SubscriptionTimelineData> =
        TimelineSpec(
            id = TimelineSpecIds.RSS_SUBSCRIPTION,
            title = UiStrings.Rss,
            icon = UiIcon.Rss.asType(),
            serializer = SubscriptionTimelineData.serializer(),
            targetId = { "${it.subscriptionType.name}:${it.subscriptionUrl}" },
        )

    fun timelineSpecs(): ImmutableList<TimelineSpec<out TimelineSpec.Data>> =
        persistentListOf(rss, allRss, subscription)
}
