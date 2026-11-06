package org.crux.data.datasource.microblog.paging

import org.crux.ui.model.UiTimelineV2

internal interface SortIdProvider {
    suspend fun sortId(data: UiTimelineV2): Long?
}
