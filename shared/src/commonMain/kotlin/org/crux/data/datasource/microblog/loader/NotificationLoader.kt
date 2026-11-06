package org.crux.data.datasource.microblog.loader
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public interface NotificationLoader {
    public suspend fun notificationBadgeCount(): Int
}
