package org.crux.di

import org.crux.data.model.tab.LiveWebTimelineSpecs
import org.crux.data.platform.AllRssTimelineLoaderFactory
import org.crux.data.platform.RssTimelineSpecs
import org.crux.model.PlatformRuntimeData
import org.crux.model.PlatformSpec
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
@Module
@Configuration
@ComponentScan("org.crux.data.platform")
internal class SubscriptionKoinModule {
    @Single
    fun runtimeData(
        platformSpecs: List<PlatformSpec>,
        allRssTimelineLoaderFactory: AllRssTimelineLoaderFactory,
    ): PlatformRuntimeData =
        PlatformRuntimeData(
            platformSpecs = platformSpecs,
            extraTimelineSpecs =
                RssTimelineSpecs.timelineSpecs(allRssTimelineLoaderFactory) +
                    LiveWebTimelineSpecs.reddit +
                    LiveWebTimelineSpecs.x +
                    LiveWebTimelineSpecs.instagram +
                    LiveWebTimelineSpecs.linkedin,
        )
}
