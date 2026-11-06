package org.crux.data.datasource.microblog.handler

import org.crux.common.MemCacheable
import org.crux.data.datasource.microblog.loader.NotificationLoader
import org.crux.model.MicroBlogKey
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public class NotificationHandler(
    public val accountKey: MicroBlogKey,
    public val loader: NotificationLoader,
    private val fetchBadgeCount: (suspend () -> Int) = { loader.notificationBadgeCount() },
) {
    private val key by lazy {
        "notificationHandler_$accountKey"
    }

    public val notificationBadgeCount: MemCacheable<Int> by lazy {
        MemCacheable(
            key = key,
            fetchSource = {
                fetchBadgeCount.invoke()
            },
        )
    }

    public fun update(count: Int) {
        MemCacheable.update(key, count)
    }

    public fun clear() {
        update(0)
    }
}
