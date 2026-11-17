package org.crux.data.readinglist

import kotlinx.serialization.Serializable

public const val DEFAULT_BOOKMARK_FOLDER_ID: String = "quick_saves"

@Serializable
public data class BookmarkFolder(
    val id: String,
    val name: String,
    val iconName: String = "folder",
    val colorHex: Long = 0xFF6366F1L,
    val createdAtTimestamp: Long = 0L,
    val isDefault: Boolean = false,
)

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
    val folderId: String = DEFAULT_BOOKMARK_FOLDER_ID,
    val note: String = "",
)
