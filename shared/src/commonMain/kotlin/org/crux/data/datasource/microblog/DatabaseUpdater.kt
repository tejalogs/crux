package org.crux.data.datasource.microblog

import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiTimelineV2
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public interface DatabaseUpdater {
    public suspend fun updateCache(
        postKey: MicroBlogKey,
        update: suspend (UiTimelineV2) -> UiTimelineV2,
    )

    public suspend fun deleteFromCache(postKey: MicroBlogKey)

    public suspend fun updateActionMenu(
        postKey: MicroBlogKey,
        newActionMenu: ActionMenu.Item,
    )
}
