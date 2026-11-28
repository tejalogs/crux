package org.crux.model

import org.crux.data.model.tab.TimelineSpec
import org.koin.core.annotation.Provided

@Provided
public data class PlatformRuntimeData(
    public val timelineSpecs: List<TimelineSpec<out TimelineSpec.Data>>,
)
