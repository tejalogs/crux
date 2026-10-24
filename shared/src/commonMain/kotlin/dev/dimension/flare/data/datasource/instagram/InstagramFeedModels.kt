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
    val is_ad: Boolean? = null,
    val invalidation_type: String? = null,
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
    val is_paid_partnership: Boolean? = null,
    val ad_action: String? = null,
)

@Serializable
public data class InstagramCaption(
    val text: String? = null,
    val created_at: Long? = null,
)

@Serializable
public data class InstagramUser(
    val pk: String? = null,
    val username: String? = null,
    val full_name: String? = null,
    val profile_pic_url: String? = null,
    val is_verified: Boolean? = null,
    val is_private: Boolean? = null,
)

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
