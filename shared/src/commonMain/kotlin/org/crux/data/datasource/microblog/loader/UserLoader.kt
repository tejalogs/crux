package org.crux.data.datasource.microblog.loader

import org.crux.ui.model.UiHandle
import org.crux.ui.model.UiProfile
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public interface UserLoader {
    public suspend fun userByHandleAndHost(uiHandle: UiHandle): UiProfile

    public suspend fun userById(id: String): UiProfile
}
