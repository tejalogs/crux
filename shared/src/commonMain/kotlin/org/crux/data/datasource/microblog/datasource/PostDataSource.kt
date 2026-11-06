package org.crux.data.datasource.microblog.datasource

import org.crux.data.datasource.microblog.handler.PostEventHandler
import org.crux.data.datasource.microblog.handler.PostHandler
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public interface PostDataSource {
    public val postHandler: PostHandler
    public val postEventHandler: PostEventHandler
}
