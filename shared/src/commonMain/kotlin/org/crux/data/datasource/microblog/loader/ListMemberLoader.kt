package org.crux.data.datasource.microblog.loader

import org.crux.data.datasource.microblog.paging.PagingRequest
import org.crux.data.datasource.microblog.paging.PagingResult
import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiList
import org.crux.ui.model.UiProfile
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public interface ListMemberLoader {
    public suspend fun loadMembers(
        pageSize: Int,
        request: PagingRequest,
        listId: String,
    ): PagingResult<UiProfile>

    public suspend fun addMember(
        listId: String,
        userKey: MicroBlogKey,
    ): UiProfile

    public suspend fun removeMember(
        listId: String,
        userKey: MicroBlogKey,
    )

    public suspend fun loadUserLists(
        pageSize: Int,
        request: PagingRequest,
        userKey: MicroBlogKey,
    ): PagingResult<UiList>
}
