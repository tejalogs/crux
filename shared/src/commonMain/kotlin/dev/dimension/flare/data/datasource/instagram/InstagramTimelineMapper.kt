package dev.dimension.flare.data.datasource.instagram

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.model.mapper.instagramComment
import dev.dimension.flare.ui.model.mapper.instagramLike
import dev.dimension.flare.ui.model.mapper.instagramShare
import dev.dimension.flare.ui.model.toUiImage
import dev.dimension.flare.ui.render.toUiPlainText
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.time.Clock
import kotlin.time.Instant

public object InstagramTimelineMapper {

    public fun render(
        mediaOrAd: InstagramMediaOrAd,
        accountKey: MicroBlogKey,
    ): UiTimelineV2.TimelinePostItem? {
        val user = mediaOrAd.user ?: return null
        val username = user.username?.takeIf { it.isNotBlank() } ?: return null

        // Ad and sponsored filtering:
        if (mediaOrAd.is_paid_partnership == true || mediaOrAd.ad_action != null) {
            return null
        }

        val statusKey = MicroBlogKey(
            id = mediaOrAd.id,
            host = "instagram.com",
        )
        val authorKey = MicroBlogKey(
            id = user.pk ?: username,
            host = "instagram.com",
        )

        val profile = UiProfile(
            key = authorKey,
            handle = UiHandle(
                raw = username,
                host = "instagram.com",
            ),
            avatar = user.profile_pic_url.toUiImage(),
            nameInternal = (user.full_name?.takeIf { it.isNotBlank() } ?: username).toUiPlainText(),
            platformId = "instagram",
            platformIcon = UiIcon.Instagram,
            clickEvent = ClickEvent.Deeplink(
                DeeplinkRoute.OpenLinkDirectly("https://www.instagram.com/$username/")
            ),
            banner = null,
            description = null,
            matrices = UiProfile.Matrices(
                fansCount = 0,
                followsCount = 0,
                statusesCount = 0,
                platformFansCount = "",
            ),
            mark = if (user.is_verified == true) {
                persistentListOf(UiProfile.Mark.Verified)
            } else {
                persistentListOf()
            },
            bottomContent = null,
        )

        val captionText = mediaOrAd.caption?.text.orEmpty()
        val mediaList = mutableListOf<UiMedia>()

        // 1. Carousel Media (multi-photo / video gallery)
        if (!mediaOrAd.carousel_media.isNullOrEmpty()) {
            for (carouselItem in mediaOrAd.carousel_media) {
                val video = carouselItem.video_versions?.firstOrNull()
                val imageCandidate = carouselItem.image_versions2?.candidates?.firstOrNull()
                if (video != null) {
                    val thumbUrl = imageCandidate?.url.orEmpty()
                    mediaList.add(
                        UiMedia.Video(
                            url = video.url,
                            thumbnailUrl = thumbUrl,
                            description = captionText,
                            height = video.height.toFloat(),
                            width = video.width.toFloat(),
                        )
                    )
                } else if (imageCandidate != null) {
                    val candidates = carouselItem.image_versions2.candidates
                    val previewCandidate = candidates.find { it.width in 640..1080 }
                        ?: candidates.firstOrNull()
                        ?: imageCandidate
                    mediaList.add(
                        UiMedia.Image(
                            url = imageCandidate.url,
                            previewUrl = previewCandidate.url,
                            description = captionText,
                            height = imageCandidate.height.toFloat(),
                            width = imageCandidate.width.toFloat(),
                            sensitive = false,
                        )
                    )
                }
            }
        } else if (!mediaOrAd.video_versions.isNullOrEmpty() || mediaOrAd.media_type == 2) {
            // 2. Single Video / Reel
            val video = mediaOrAd.video_versions?.firstOrNull()
            val imageCandidate = mediaOrAd.image_versions2?.candidates?.firstOrNull()
            if (video != null) {
                val thumbUrl = imageCandidate?.url.orEmpty()
                mediaList.add(
                    UiMedia.Video(
                        url = video.url,
                        thumbnailUrl = thumbUrl,
                        description = captionText,
                        height = video.height.toFloat(),
                        width = video.width.toFloat(),
                    )
                )
            } else if (imageCandidate != null) {
                val candidates = mediaOrAd.image_versions2.candidates
                val previewCandidate = candidates.find { it.width in 640..1080 }
                    ?: candidates.firstOrNull()
                    ?: imageCandidate
                mediaList.add(
                    UiMedia.Image(
                        url = imageCandidate.url,
                        previewUrl = previewCandidate.url,
                        description = captionText,
                        height = imageCandidate.height.toFloat(),
                        width = imageCandidate.width.toFloat(),
                        sensitive = false,
                    )
                )
            }
        } else {
            // 3. Single Photo
            val imageCandidate = mediaOrAd.image_versions2?.candidates?.firstOrNull()
            if (imageCandidate != null) {
                val candidates = mediaOrAd.image_versions2.candidates
                val previewCandidate = candidates.find { it.width in 640..1080 }
                    ?: candidates.firstOrNull()
                    ?: imageCandidate
                mediaList.add(
                    UiMedia.Image(
                        url = imageCandidate.url,
                        previewUrl = previewCandidate.url,
                        description = captionText,
                        height = imageCandidate.height.toFloat(),
                        width = imageCandidate.width.toFloat(),
                        sensitive = false,
                    )
                )
            }
        }

        val code = mediaOrAd.code ?: mediaOrAd.id
        val postUrl = "https://www.instagram.com/p/$code/"

        val actions = persistentListOf(
            ActionMenu.instagramComment(
                statusKey = statusKey,
                count = mediaOrAd.comment_count ?: 0L,
                accountKey = accountKey,
            ),
            ActionMenu.instagramLike(
                statusKey = statusKey,
                liked = mediaOrAd.has_liked == true,
                count = mediaOrAd.like_count ?: 0L,
                accountKey = accountKey,
            ),
            ActionMenu.Group(
                displayItem =
                    ActionMenu.Item(
                        icon = UiIcon.More,
                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.More),
                    ),
                actions =
                    persistentListOf(
                        ActionMenu.instagramShare(
                            statusKey = statusKey,
                            shareUrl = postUrl,
                            accountKey = accountKey,
                        ),
                    ),
            ),
        )

        val createdAtInstant = mediaOrAd.taken_at?.let { Instant.fromEpochSeconds(it) }
            ?: Clock.System.now()

        val post = UiTimelineV2.Post(
            platformId = "instagram",
            images = mediaList.toImmutableList(),
            sensitive = false,
            contentWarning = null,
            user = profile,
            platformIcon = UiIcon.Instagram,
            content = UiTranslatableText(original = captionText.toUiPlainText()),
            actions = actions,
            poll = null,
            statusKey = statusKey,
            card = null,
            createdAt = createdAtInstant.toUi(),
            clickEvent = ClickEvent.Deeplink(DeeplinkRoute.OpenLinkDirectly(postUrl)),
            accountType = AccountType.Specific(accountKey),
        )

        return UiTimelineV2.TimelinePostItem(post = post)
    }
}
