package org.crux.data.datasource.microblog.datasource

import org.crux.data.datasource.microblog.paging.RemoteLoader
import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiArticle
import org.crux.ui.model.UiTimelineV2
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public interface ArticleDataSource {
    public suspend fun article(articleKey: MicroBlogKey): UiArticle

    public fun articleComments(articleKey: MicroBlogKey): RemoteLoader<UiTimelineV2>
}
