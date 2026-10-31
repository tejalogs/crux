package dev.dimension.flare.data.datasource.instagram

import kotlinx.serialization.Serializable

@Serializable
public data class InstagramTimelineResponse(
    val feed_items: List<InstagramFeedItem> = emptyList(),
    val items: List<InstagramMediaOrAd> = emptyList(),
    val next_max_id: String? = null,
    val more_available: Boolean? = null,
    val status: String? = null,
    val num_results: Int? = null,
)

@Serializable
public data class InstagramFeedItem(
    val media_or_ad: InstagramMediaOrAd? = null,
    val end_of_feed_demarcator: InstagramEndOfFeedDemarcator? = null,
    val is_ad: Boolean? = null,
    val invalidation_type: String? = null,
)

@Serializable
public data class InstagramEndOfFeedDemarcator(
    val id: Long? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val group_set: InstagramGroupSet? = null,
)

@Serializable
public data class InstagramGroupSet(
    val id: Long? = null,
    val active_group_id: String? = null,
    val connected_group_id: String? = null,
    val next_max_id: String? = null,
    val pagination_source: String? = null,
    val groups: List<InstagramDemarcatorGroup> = emptyList(),
)

@Serializable
public data class InstagramDemarcatorGroup(
    val id: String? = null,
    val title: String? = null,
    val next_max_id: String? = null,
    val feed_items: List<InstagramFeedItem> = emptyList(),
)

@Serializable
public data class InstagramMediaInfoResponse(
    val items: List<InstagramMediaOrAd> = emptyList(),
    val num_results: Int? = null,
    val more_available: Boolean? = null,
    val status: String? = null,
)

@Serializable
public data class InstagramMediaOrAd(
    val id: String,
    val pk: String? = null,
    val code: String? = null,
    val taken_at: Long? = null,
    val media_type: Int? = null,
    val caption: InstagramCaption? = null,
    val user: InstagramUser? = null,
    val image_versions2: InstagramImageVersions? = null,
    val video_versions: List<InstagramVideoVersion>? = null,
    val carousel_media: List<InstagramCarouselItem>? = null,
    val like_count: Long? = null,
    val has_liked: Boolean? = null,
    val comment_count: Long? = null,
    val view_count: Long? = null,
    val video_duration: Double? = null,
    val has_viewer_saved: Boolean? = null,
    val can_viewer_save: Boolean? = null,
    val is_paid_partnership: Boolean? = null,
    val ad_action: String? = null,
    val end_of_feed_demarcator: InstagramEndOfFeedDemarcator? = null,
)

@Serializable
public data class InstagramCaption(
    val text: String? = null,
    val created_at: Long? = null,
)

@Serializable
public data class InstagramUser(
    val pk: String? = null,
    val id: String? = null,
    val username: String? = null,
    val full_name: String? = null,
    val profile_pic_url: String? = null,
    val profile_pic_url_hd: String? = null,
    val hd_profile_pic_url_info: InstagramImageCandidate? = null,
    val is_verified: Boolean? = null,
    val is_private: Boolean? = null,
    val biography: String? = null,
    val external_url: String? = null,
    val media_count: Long? = null,
    val follower_count: Long? = null,
    val following_count: Long? = null,
    val edge_followed_by: InstagramEdgeCount? = null,
    val edge_follow: InstagramEdgeCount? = null,
    val edge_owner_to_timeline_media: InstagramEdgeCount? = null,
) {
    public val resolvedId: String?
        get() = pk?.takeIf { it.isNotBlank() } ?: id?.takeIf { it.isNotBlank() }

    public val bestAvatarUrl: String?
        get() =
            hd_profile_pic_url_info?.url?.takeIf { it.isNotBlank() }
                ?: profile_pic_url_hd?.takeIf { it.isNotBlank() }
                ?: profile_pic_url?.takeIf { it.isNotBlank() }

    public val resolvedFollowerCount: Long
        get() = follower_count ?: edge_followed_by?.count ?: 0L

    public val resolvedFollowingCount: Long
        get() = following_count ?: edge_follow?.count ?: 0L

    public val resolvedMediaCount: Long
        get() = media_count ?: edge_owner_to_timeline_media?.count ?: 0L
}

@Serializable
public data class InstagramEdgeCount(
    val count: Long = 0L,
)

@Serializable
public data class InstagramUserInfoResponse(
    val user: InstagramUser? = null,
    val data: InstagramWebProfileData? = null,
    val status: String? = null,
) {
    public val resolvedUser: InstagramUser?
        get() = user ?: data?.user
}

@Serializable
public data class InstagramWebProfileData(
    val user: InstagramUser? = null,
)

@Serializable
public data class InstagramCommentsResponse(
    val comments: List<InstagramComment> = emptyList(),
    val child_comments: List<InstagramComment> = emptyList(),
    val comment_count: Long? = null,
    val child_comment_count: Int? = null,
    val next_min_id: String? = null,
    val next_max_id: String? = null,
    val next_max_child_cursor: String? = null,
    val has_more_comments: Boolean? = null,
    val has_more_headload_comments: Boolean? = null,
    val has_more_head_load_comments: Boolean? = null,
    val has_more_tail_child_comments: Boolean? = null,
    val status: String? = null,
)

@Serializable
public data class InstagramComment(
    val pk: String = "",
    val id: String? = null,
    val text: String? = null,
    val created_at: Long? = null,
    val created_at_utc: Long? = null,
    val user: InstagramUser? = null,
    val comment_like_count: Long? = null,
    val like_count: Long? = null,
    val has_liked_comment: Boolean? = null,
    val child_comment_count: Int? = null,
    val preview_child_comments: List<InstagramComment>? = null,
    val parent_comment_id: String? = null,
) {
    public val resolvedPk: String
        get() = pk.takeIf { it.isNotBlank() } ?: id.orEmpty()

    public val resolvedLikeCount: Long
        get() = comment_like_count ?: like_count ?: 0L
}

@Serializable
public data class InstagramImageVersions(
    val candidates: List<InstagramImageCandidate> = emptyList(),
)

@Serializable
public data class InstagramImageCandidate(
    val url: String,
    val width: Int = 0,
    val height: Int = 0,
)

@Serializable
public data class InstagramVideoVersion(
    val url: String,
    val width: Int = 0,
    val height: Int = 0,
    val type: Int? = null,
)

@Serializable
public data class InstagramCarouselItem(
    val id: String? = null,
    val media_type: Int? = null,
    val image_versions2: InstagramImageVersions? = null,
    val video_versions: List<InstagramVideoVersion>? = null,
)

@Serializable
public data class InstagramDomPost(
    public val code: String = "",
    public val username: String = "",
    public val avatar: String = "",
    public val imageUrl: String = "",
    public val videoUrl: String = "",
    public val caption: String = "",
    public val datetime: String = "",
    public val likes: Long = 0L,
)
