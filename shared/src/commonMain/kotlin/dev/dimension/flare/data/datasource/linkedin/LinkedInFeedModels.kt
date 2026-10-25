package dev.dimension.flare.data.datasource.linkedin

import kotlinx.serialization.Serializable

@Serializable
public data class LinkedInFeedResponse(
    val elements: List<LinkedInUpdate> = emptyList(),
    val metadata: LinkedInFeedMetadata? = null,
)

@Serializable
public data class LinkedInFeedMetadata(
    val paginationToken: String? = null,
    val count: Int? = null,
)

@Serializable
public data class LinkedInUpdate(
    val id: String = "",
    val urn: String? = null,
    val actor: LinkedInActor? = null,
    val commentary: LinkedInCommentary? = null,
    val created: LinkedInTimestamp? = null,
    val content: LinkedInContent? = null,
    val socialDetail: LinkedInSocialDetail? = null,
    val resharedUpdate: LinkedInUpdate? = null,
    val isSponsored: Boolean? = null,
    val permalink: String? = null,
)

@Serializable
public data class LinkedInTimestamp(
    val time: Long? = null,
)

@Serializable
public data class LinkedInActor(
    val urn: String? = null,
    val name: LinkedInText? = null,
    val description: LinkedInText? = null, // Professional headline (e.g. "Software Engineer at Google")
    val subDescription: LinkedInText? = null, // Relative timestamp
    val navigationUrl: String? = null,
    val image: LinkedInImageAttributes? = null,
)

@Serializable(with = LinkedInTextSerializer::class)
public data class LinkedInText(
    val text: String? = null,
)

@Serializable(with = LinkedInCommentarySerializer::class)
public data class LinkedInCommentary(
    val text: LinkedInText? = null,
)

@Serializable
public data class LinkedInImageAttributes(
    val attributes: List<LinkedInImageDetail> = emptyList(),
)

@Serializable
public data class LinkedInImageDetail(
    val sourceWebImageUrl: String? = null,
    val detailData: LinkedInImageDetailData? = null,
)

@Serializable
public data class LinkedInImageDetailData(
    val nonEntityProfilePicture: LinkedInProfilePicUrl? = null,
    val vectorImage: LinkedInVectorImage? = null,
)

@Serializable
public data class LinkedInProfilePicUrl(
    val url: String? = null,
)

@Serializable
public data class LinkedInVectorImage(
    val rootUrl: String? = null,
    val artifacts: List<LinkedInArtifact> = emptyList(),
)

@Serializable
public data class LinkedInArtifact(
    val width: Int = 0,
    val height: Int = 0,
    val fileIdentifyingUrlPathSegment: String = "",
)

@Serializable
public data class LinkedInContent(
    val articleComponent: LinkedInArticleComponent? = null,
    val imageComponent: LinkedInImageComponent? = null,
    val imagesComponent: LinkedInMultiImagesComponent? = null,
    val videoComponent: LinkedInVideoComponent? = null,
    val entityComponent: LinkedInEntityComponent? = null,
)

@Serializable
public data class LinkedInArticleComponent(
    val title: String? = null,
    val subtitle: String? = null,
    val navigationUrl: String? = null,
    val thumbnail: String? = null,
    val source: String? = null,
)

@Serializable
public data class LinkedInImageComponent(
    val url: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val altText: String? = null,
)

@Serializable
public data class LinkedInMultiImagesComponent(
    val images: List<LinkedInImageComponent> = emptyList(),
)

@Serializable
public data class LinkedInVideoComponent(
    val url: String? = null,
    val thumbnailUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
public data class LinkedInEntityComponent(
    val title: LinkedInText? = null,
    val subtitle: LinkedInText? = null,
    val navigationUrl: String? = null,
    val thumbnail: String? = null,
)

@Serializable
public data class LinkedInSocialDetail(
    val totalSocialActivityCounts: LinkedInSocialCounts? = null,
    val reactions: LinkedInReactions? = null,
)

@Serializable
public data class LinkedInSocialCounts(
    val numLikes: Long? = null,
    val numComments: Long? = null,
    val numShares: Long? = null,
)

@Serializable
public data class LinkedInReactions(
    val hasLiked: Boolean? = null,
    val reactionType: String? = null,
)
