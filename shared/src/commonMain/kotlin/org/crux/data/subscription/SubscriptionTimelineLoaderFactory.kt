package org.crux.data.subscription

import org.crux.data.database.cache.CacheDatabase
import org.crux.data.datasource.microblog.MixedRemoteMediator
import org.crux.data.datasource.microblog.paging.CacheableRemoteLoader
import org.crux.data.model.tab.TimelineMergePolicy
import org.crux.ui.model.UiTimelineV2
import org.koin.core.annotation.Single
import kotlin.native.HiddenFromObjC

@Single
@HiddenFromObjC
public class SubscriptionTimelineLoaderFactory internal constructor(
    private val database: CacheDatabase,
) {
    public fun mixedTimeline(
        loaders: List<CacheableRemoteLoader<UiTimelineV2>>,
        mergePolicy: TimelineMergePolicy = TimelineMergePolicy.Time,
    ): CacheableRemoteLoader<UiTimelineV2> =
        MixedRemoteMediator(
            database = database,
            mediators = loaders,
            mergePolicy = mergePolicy,
        )
}
