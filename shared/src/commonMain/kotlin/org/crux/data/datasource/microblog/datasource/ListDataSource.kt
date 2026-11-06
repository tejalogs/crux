package org.crux.data.datasource.microblog.datasource

import org.crux.data.datasource.microblog.handler.ListHandler
import org.crux.data.datasource.microblog.handler.ListMemberHandler
import org.crux.data.datasource.microblog.paging.RemoteLoader
import org.crux.ui.model.UiList
import org.crux.ui.model.UiTimelineV2
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public interface ListDataSource {
    public fun listTimeline(listId: String): RemoteLoader<UiTimelineV2>

    public val listHandler: ListHandler<UiList.List>
    public val listMemberHandler: ListMemberHandler
}
