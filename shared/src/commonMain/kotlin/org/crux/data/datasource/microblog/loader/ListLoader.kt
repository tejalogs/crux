package org.crux.data.datasource.microblog.loader

import kotlinx.collections.immutable.ImmutableList
import org.crux.data.datasource.microblog.list.ListMetaData
import org.crux.data.datasource.microblog.list.ListMetaDataType
import org.crux.data.datasource.microblog.paging.PagingRequest
import org.crux.data.datasource.microblog.paging.PagingResult
import org.crux.ui.model.UiList
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public interface ListLoader<T : UiList> {
    public suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<T>

    public suspend fun info(listId: String): T

    public suspend fun create(metaData: ListMetaData): T

    public suspend fun update(
        listId: String,
        metaData: ListMetaData,
    ): T

    public suspend fun delete(listId: String)

    public val supportedMetaData: ImmutableList<ListMetaDataType>
}
