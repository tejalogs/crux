package org.crux.data.datasource.rss

import org.crux.data.database.app.model.SubscriptionType
import org.crux.data.datasource.microblog.paging.CacheableRemoteLoader
import org.crux.data.datasource.subscription.KoinSubscriptionDataSource
import org.crux.ui.model.UiRssSource
import org.crux.ui.model.UiTimelineV2

internal object RssDataSource {
    fun fetchLoader(url: String) = KoinSubscriptionDataSource.createTimelineLoader(SubscriptionType.RSS, url)

    fun fetchLoader(
        type: SubscriptionType,
        url: String,
    ): CacheableRemoteLoader<UiTimelineV2> = KoinSubscriptionDataSource.createTimelineLoader(type, url)

    fun fetchLoader(subscription: UiRssSource): CacheableRemoteLoader<UiTimelineV2> =
        fetchLoader(
            type = subscription.type,
            url = subscription.url,
        )
}
