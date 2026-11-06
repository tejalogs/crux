package org.crux.data.datastore.model

import kotlinx.serialization.Serializable
import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiTimelineV2

@Serializable
internal data class ComposeConfigData(
    val visibility: UiTimelineV2.Post.Visibility =
        UiTimelineV2.Post.Visibility.Public,
    val lastAccounts: List<MicroBlogKey> = emptyList(),
)
