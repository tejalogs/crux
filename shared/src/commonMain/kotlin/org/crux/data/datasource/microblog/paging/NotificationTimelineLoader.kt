package org.crux.data.datasource.microblog.paging

import org.crux.ui.model.UiTimelineV2
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public interface NotificationTimelineLoader : CacheableRemoteLoader<UiTimelineV2> {
    override val collapseReplyChains: Boolean
        get() = false
}
