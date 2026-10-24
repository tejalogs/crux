package dev.dimension.flare.data.readinglist

import kotlinx.serialization.Serializable

@Serializable
public data class ReadingListItem(
    val id: String,
    val title: String,
    val authorName: String,
    val authorHandle: String = "",
    val authorAvatarUrl: String? = null,
    val contentText: String,
    val contentHtml: String? = null,
    val sourceUrl: String,
    val platformId: String,
    val savedAtTimestamp: Long,
    val isRead: Boolean = false,
    val mediaUrls: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
)
