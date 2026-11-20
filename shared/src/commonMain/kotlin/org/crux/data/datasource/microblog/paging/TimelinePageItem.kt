package org.crux.data.datasource.microblog.paging

import org.crux.data.database.cache.dao.DbTimelinePageIdentity
import org.crux.ui.model.UiTimelineV2

/** The single retained representation shared by the page cache and Paging/UI. */
internal class TimelinePageItem(
    val identity: DbTimelinePageIdentity,
    val baseItem: UiTimelineV2,
) {
    val statusId: String
        get() = identity.statusId

    val sortId: Long
        get() = identity.sortId

    fun toUi(): UiTimelineV2 = baseItem
}
