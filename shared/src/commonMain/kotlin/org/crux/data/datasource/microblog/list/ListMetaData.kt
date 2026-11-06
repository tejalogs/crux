package org.crux.data.datasource.microblog.list

import org.crux.common.FileItem

public data class ListMetaData(
    val title: String,
    val description: String? = null,
    val avatar: FileItem? = null,
)
