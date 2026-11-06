package org.crux.data.datasource.microblog.datasource

import org.crux.data.datasource.microblog.handler.NotificationHandler
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public interface NotificationDataSource {
    public val notificationHandler: NotificationHandler
}
