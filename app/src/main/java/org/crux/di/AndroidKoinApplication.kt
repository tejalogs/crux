package org.crux.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.crux.data.model.tab.LiveWebTimelineSpecs
import org.crux.model.PlatformRuntimeData
import org.crux.rss.NativeRssTimelineSpecs
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@KoinApplication
internal class AndroidKoinApplication

@Module
@Configuration
@ComponentScan("org.crux")
internal class AndroidKoinModule {
    @Single
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Single
    fun runtimeData(): PlatformRuntimeData =
        PlatformRuntimeData(
            timelineSpecs =
                NativeRssTimelineSpecs.timelineSpecs() +
                    LiveWebTimelineSpecs.reddit +
                    LiveWebTimelineSpecs.x +
                    LiveWebTimelineSpecs.instagram +
                    LiveWebTimelineSpecs.linkedin +
                    LiveWebTimelineSpecs.mastodon,
        )
}
