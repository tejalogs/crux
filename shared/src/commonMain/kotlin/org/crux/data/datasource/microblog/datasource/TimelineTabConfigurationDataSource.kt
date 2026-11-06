package org.crux.data.datasource.microblog.datasource

import kotlinx.collections.immutable.ImmutableList
import org.crux.data.model.tab.ShortcutSpec
import org.crux.data.model.tab.TimelineCandidate
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public interface TimelineTabConfigurationDataSource {
    public val defaultTabs: ImmutableList<TimelineCandidate<*>>
    public val builtInTimelineTabs: ImmutableList<TimelineCandidate<*>>
    public val shortcuts: ImmutableList<ShortcutSpec>
}
