package dev.dimension.flare.data.datasource.linkedin

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiCard
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.model.mapper.linkedInComment
import dev.dimension.flare.ui.model.mapper.linkedInLike
import dev.dimension.flare.ui.model.mapper.linkedInShare
import dev.dimension.flare.ui.model.toUiImage
import dev.dimension.flare.ui.render.toUiPlainText
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.time.Clock
import kotlin.time.Instant

public object LinkedInTimelineMapper {

    public fun render(
        update: LinkedInUpdate,
        accountKey: MicroBlogKey,
    ): UiTimelineV2.TimelinePostItem? {
        if (update.isSponsored == true) {
            return null
        }

        val actor = update.actor ?: return null
        val actorName = actor.name?.text?.takeIf { it.isNotBlank() } ?: "LinkedIn Member"
        val headline = actor.description?.text?.takeIf { it.isNotBlank() }
            ?: actor.subDescription?.text?.takeIf { it.isNotBlank() }

        val id = update.id.ifBlank { update.urn.orEmpty() }
        if (id.isBlank()) return null

        val statusKey = MicroBlogKey(
            id = id,
            host = "linkedin.com",
        )
        val authorKey = MicroBlogKey(
            id = actor.urn ?: actorName,
            host = "linkedin.com",
        )

        val avatarUrl = extractAvatarUrl(actor.image)
        val profileUrl = actor.navigationUrl ?: "https://www.linkedin.com/in/"

        val profile = UiProfile(
            key = authorKey,
            handle = UiHandle(
                raw = headline ?: actorName,
                host = "linkedin.com",
            ),
            avatar = avatarUrl.toUiImage(),
            nameInternal = actorName.toUiPlainText(),
            platformId = "linkedin",
            platformIcon = UiIcon.LinkedIn,
            clickEvent = ClickEvent.Deeplink(
                DeeplinkRoute.OpenLinkDirectly(profileUrl)
            ),
            banner = null,
            description = headline?.toUiPlainText(),
            matrices = UiProfile.Matrices(
                fansCount = 0,
                followsCount = 0,
                statusesCount = 0,
                platformFansCount = "",
            ),
            mark = persistentListOf(),
            bottomContent = null,
        )

        val commentaryText = update.commentary?.text?.text?.takeIf { it.isNotBlank() }
            ?: update.content?.articleComponent?.title?.takeIf { it.isNotBlank() }
            ?: update.content?.entityComponent?.title?.text?.takeIf { it.isNotBlank() }
            ?: ""
        val mediaList = mutableListOf<UiMedia>()

        // 1. Single Image or Multi-Image
        val imagesComp = update.content?.imagesComponent
        val imageComp = update.content?.imageComponent
        if (imagesComp != null && imagesComp.images.isNotEmpty()) {
            for (img in imagesComp.images) {
                img.url?.let { url ->
                    mediaList.add(
                        UiMedia.Image(
                            url = url,
                            previewUrl = url,
                            description = img.altText ?: commentaryText,
                            height = img.height?.toFloat() ?: 600f,
                            width = img.width?.toFloat() ?: 800f,
                            sensitive = false,
                        )
                    )
                }
            }
        } else if (imageComp?.url != null) {
            mediaList.add(
                UiMedia.Image(
                    url = imageComp.url,
                    previewUrl = imageComp.url,
                    description = imageComp.altText ?: commentaryText,
                    height = imageComp.height?.toFloat() ?: 600f,
                    width = imageComp.width?.toFloat() ?: 800f,
                    sensitive = false,
                )
            )
        }

        // 2. Video
        val videoComp = update.content?.videoComponent
        if (videoComp?.url != null) {
            mediaList.add(
                UiMedia.Video(
                    url = videoComp.url,
                    thumbnailUrl = videoComp.thumbnailUrl.orEmpty(),
                    description = commentaryText,
                    height = videoComp.height?.toFloat() ?: 720f,
                    width = videoComp.width?.toFloat() ?: 1280f,
                )
            )
        }

        // 3. Article / Link Preview Card
        val card = extractCard(update.content, commentaryText)

        val postUrl = update.permalink ?: "https://www.linkedin.com/feed/update/$id/"

        val counts = update.socialDetail?.totalSocialActivityCounts
        val actions = persistentListOf(
            ActionMenu.linkedInLike(
                statusKey = statusKey,
                liked = update.socialDetail?.reactions?.hasLiked == true,
                count = counts?.numLikes ?: 0L,
                accountKey = accountKey,
            ),
            ActionMenu.linkedInComment(
                statusKey = statusKey,
                count = counts?.numComments ?: 0L,
                accountKey = accountKey,
            ),
            ActionMenu.linkedInShare(
                statusKey = statusKey,
                shareUrl = postUrl,
                accountKey = accountKey,
            ),
        )

        val createdAtInstant = update.created?.time?.let { Instant.fromEpochMilliseconds(it) }
            ?: Clock.System.now()

        val post = UiTimelineV2.Post(
            platformId = "linkedin",
            images = mediaList.toImmutableList(),
            sensitive = false,
            contentWarning = null,
            user = profile,
            platformIcon = UiIcon.LinkedIn,
            content = UiTranslatableText(original = commentaryText.toUiPlainText()),
            actions = actions,
            poll = null,
            statusKey = statusKey,
            card = card,
            createdAt = createdAtInstant.toUi(),
            clickEvent = ClickEvent.Deeplink(DeeplinkRoute.OpenLinkDirectly(postUrl)),
            accountType = AccountType.Specific(accountKey),
        )

        // 4. Handle Quote / Reshared Update if present
        val presentation = if (update.resharedUpdate != null) {
            val resharedItem = render(update.resharedUpdate, accountKey)
            if (resharedItem != null) {
                UiTimelineV2.PostPresentation(
                    quotes = persistentListOf(resharedItem.post),
                )
            } else {
                UiTimelineV2.PostPresentation()
            }
        } else {
            UiTimelineV2.PostPresentation()
        }

        return UiTimelineV2.TimelinePostItem(
            post = post,
            presentation = presentation,
        )
    }

    private fun extractAvatarUrl(imageAttr: LinkedInImageAttributes?): String? {
        val attributes = imageAttr?.attributes.orEmpty()
        for (detail in attributes) {
            if (!detail.sourceWebImageUrl.isNullOrBlank()) {
                return detail.sourceWebImageUrl
            }
            val nonEntity = detail.detailData?.nonEntityProfilePicture?.url
            if (!nonEntity.isNullOrBlank()) {
                return nonEntity
            }
            val vector = detail.detailData?.vectorImage
            if (vector != null) {
                val rootUrl = vector.rootUrl.orEmpty()
                val artifactPath = vector.artifacts.lastOrNull()?.fileIdentifyingUrlPathSegment.orEmpty()
                if (rootUrl.isNotBlank() && artifactPath.isNotBlank()) {
                    return if (rootUrl.endsWith("/") || artifactPath.startsWith("/")) {
                        rootUrl + artifactPath
                    } else {
                        "$rootUrl/$artifactPath"
                    }
                }
            }
        }
        return null
    }

    private fun extractCard(content: LinkedInContent?, fallbackText: String): UiCard? {
        val article = content?.articleComponent
        if (article != null && !article.navigationUrl.isNullOrBlank()) {
            val thumbMedia = article.thumbnail?.let { url ->
                UiMedia.Image(
                    url = url,
                    previewUrl = url,
                    description = article.title ?: fallbackText,
                    height = 400f,
                    width = 800f,
                    sensitive = false,
                )
            }
            return UiCard(
                title = article.title ?: article.source ?: "Article",
                description = article.subtitle,
                media = thumbMedia,
                url = article.navigationUrl,
            )
        }

        val entity = content?.entityComponent
        if (entity != null && !entity.navigationUrl.isNullOrBlank()) {
            val thumbMedia = entity.thumbnail?.let { url ->
                UiMedia.Image(
                    url = url,
                    previewUrl = url,
                    description = entity.title?.text ?: fallbackText,
                    height = 400f,
                    width = 800f,
                    sensitive = false,
                )
            }
            return UiCard(
                title = entity.title?.text ?: "Link",
                description = entity.subtitle?.text,
                media = thumbMedia,
                url = entity.navigationUrl,
            )
        }

        return null
    }
}
