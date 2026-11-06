package org.crux.data.datasource.microblog.loader

import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiRelation
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public enum class RelationActionType {
    Follow,
    Block,
    Mute,
}

@HiddenFromObjC
public interface RelationLoader {
    public val supportedTypes: Set<RelationActionType>

    public suspend fun relation(userKey: MicroBlogKey): UiRelation

    public suspend fun follow(userKey: MicroBlogKey)

    public suspend fun unfollow(userKey: MicroBlogKey)

    public suspend fun block(userKey: MicroBlogKey)

    public suspend fun unblock(userKey: MicroBlogKey)

    public suspend fun mute(userKey: MicroBlogKey)

    public suspend fun unmute(userKey: MicroBlogKey)
}
