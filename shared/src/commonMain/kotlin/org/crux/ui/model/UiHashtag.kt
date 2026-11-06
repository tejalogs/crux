package org.crux.ui.model

import androidx.compose.runtime.Immutable

@Immutable
public data class UiHashtag public constructor(
    val hashtag: String,
    val description: String?,
    val searchContent: String,
)
