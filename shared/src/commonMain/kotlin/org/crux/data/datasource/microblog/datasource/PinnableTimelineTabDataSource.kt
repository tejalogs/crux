package org.crux.data.datasource.microblog.datasource

import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import org.crux.data.model.tab.TimelineCandidate
import org.crux.ui.model.UiStrings
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public interface PinnableTimelineTabDataSource {
    public val pinnableTimelineTabs: List<PinnableTimelineTabSection>
}

@HiddenFromObjC
public data class PinnableTimelineTabSection(
    val title: UiStrings,
    val data: Flow<PagingData<TimelineCandidate<*>>>,
)
