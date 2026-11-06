package org.crux.data.datasource.microblog.loader

import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiTimelineV2
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public interface PostLoader {
    public suspend fun status(statusKey: MicroBlogKey): UiTimelineV2

    public suspend fun deleteStatus(statusKey: MicroBlogKey)
}
