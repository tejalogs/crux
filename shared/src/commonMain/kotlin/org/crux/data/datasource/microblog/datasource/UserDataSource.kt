package org.crux.data.datasource.microblog.datasource

import org.crux.data.datasource.microblog.handler.UserHandler
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public interface UserDataSource {
    public val userHandler: UserHandler
}
