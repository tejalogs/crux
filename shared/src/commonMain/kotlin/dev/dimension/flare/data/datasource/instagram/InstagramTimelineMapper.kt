package dev.dimension.flare.data.datasource.instagram

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.ReferenceType
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
import dev.dimension.flare.ui.render.RenderContent
import dev.dimension.flare.ui.render.RenderRun
import dev.dimension.flare.ui.render.RenderTextStyle
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import dev.dimension.flare.ui.render.uiRichTextOf
import dev.dimension.flare.ui.route.DeeplinkRoute
import dev.dimension.flare.ui.route.toUri
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import moe.tlaster.twitter.parser.CashTagToken
import moe.tlaster.twitter.parser.EmojiToken
import moe.tlaster.twitter.parser.HashTagToken
import moe.tlaster.twitter.parser.StringToken
import moe.tlaster.twitter.parser.TwitterParser
import moe.tlaster.twitter.parser.UrlToken
import moe.tlaster.twitter.parser.UserNameToken
import kotlin.time.Clock
import kotlin.time.Instant

public object InstagramTimelineMapper {
    private val instagramParser: TwitterParser by lazy {
        TwitterParser(
            validMarkInUserName = listOf('.', '_'),
            validMarkInHashTag = listOf('_'),
            enableEscapeInUrl = true,
            enableDomainDetection = true,
        )
    }

    public fun String.toInstagramUiRichText(accountKey: MicroBlogKey): UiRichText {
        val tokens = runCatching { instagramParser.parse(this) }.getOrNull()
        if (tokens.isNullOrEmpty()) {
            return this.toUiPlainText()
        }
        val runs =
            buildList<RenderRun> {
                tokens.forEach { token ->
                    when (token) {
                        is UserNameToken -> {
                            val cleanUser = token.value.trimStart('@', '＠')
                            add(
                                RenderRun.Text(
                                    text = token.value,
                                    style =
                                        RenderTextStyle(
                                            link =
                                                DeeplinkRoute.Profile
                                                    .UserNameWithHost(
                                                        accountType = AccountType.Specific(accountKey),
                                                        userName = cleanUser,
                                                        host = "instagram.com",
                                                    ).toUri(),
                                        ),
                                ),
                            )
                        }

                        is HashTagToken -> {
                            add(
                                RenderRun.Text(
                                    text = token.value,
                                    style =
                                        RenderTextStyle(
                                            link =
                                                DeeplinkRoute
                                                    .Search(
                                                        accountType = AccountType.Specific(accountKey),
                                                        query = token.value,
                                                    ).toUri(),
                                        ),
                                ),
                            )
                        }

                        is UrlToken -> {
                            add(
                                RenderRun.Text(
                                    text = token.value,
                                    style =
                                        RenderTextStyle(
                                            link = token.value,
                                        ),
                                ),
                            )
                        }

                        is CashTagToken -> {
                            add(
                                RenderRun.Text(
                                    text = token.value,
                                    style =
                                        RenderTextStyle(
                                            link =
                                                DeeplinkRoute
                                                    .Search(
                                                        accountType = AccountType.Specific(accountKey),
                                                        query = token.value,
                                                    ).toUri(),
                                        ),
                                ),
                            )
                        }

                        is EmojiToken, is StringToken -> {
                            add(
                                RenderRun.Text(
                                    text = token.value,
                                ),
                            )
                        }
                    }
                }
            }
        return uiRichTextOf(
            renderRuns =
                if (runs.isEmpty()) {
                    emptyList()
                } else {
                    listOf(RenderContent.Text(runs = runs.toImmutableList()))
                },
        )
    }

    public fun renderProfile(
        user: InstagramUser,
        accountKey: MicroBlogKey,
    ): UiProfile? {
        val username = user.username?.takeIf { it.isNotBlank() } ?: return null
        val authorKey =
            MicroBlogKey(
                id = user.resolvedId ?: username,
                host = "instagram.com",
            )
        val bioText = user.biography?.takeIf { it.isNotBlank() }

        return UiProfile(
            key = authorKey,
            handle =
                UiHandle(
                    raw = username,
                    host = "instagram.com",
                ),
            avatar = user.bestAvatarUrl.toUiImage(),
            nameInternal = (user.full_name?.takeIf { it.isNotBlank() } ?: username).toUiPlainText(),
            platformId = "instagram",
            platformIcon = UiIcon.Instagram,
            clickEvent =
                ClickEvent.Deeplink(
                    DeeplinkRoute.Profile.User(
                        accountType = AccountType.Specific(accountKey),
                        userKey = authorKey,
                    ),
                ),
            banner = null as UiMedia.Image?,
            description = bioText?.toInstagramUiRichText(accountKey),
            matrices =
                UiProfile.Matrices(
                    fansCount = user.resolvedFollowerCount,
                    followsCount = user.resolvedFollowingCount,
                    statusesCount = user.resolvedMediaCount,
                    platformFansCount = "",
                ),
            mark =
                if (user.is_verified == true) {
                    persistentListOf(UiProfile.Mark.Verified)
                } else {
                    persistentListOf()
                },
            bottomContent = null,
        )
    }

    public fun render(
        mediaOrAd: InstagramMediaOrAd,
        accountKey: MicroBlogKey,
        statusKeyOverride: MicroBlogKey? = null,
    ): UiTimelineV2.TimelinePostItem? {
        val user = mediaOrAd.user ?: return null
        val profile = renderProfile(user, accountKey) ?: return null

        // Ad and sponsored filtering:
        if (mediaOrAd.is_paid_partnership == true || mediaOrAd.ad_action != null) {
            return null
        }

        val statusKey =
            statusKeyOverride ?: MicroBlogKey(
                id = mediaOrAd.id,
                host = "instagram.com",
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
                        ),
                    )
                } else if (imageCandidate != null) {
                    val candidates = carouselItem.image_versions2.candidates
                    val previewCandidate =
                        candidates.find { it.width in 640..1080 }
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
                        ),
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
                    ),
                )
            } else if (imageCandidate != null) {
                val candidates = mediaOrAd.image_versions2.candidates
                val previewCandidate =
                    candidates.find { it.width in 640..1080 }
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
                    ),
                )
            }
        } else {
            // 3. Single Photo
            val imageCandidate = mediaOrAd.image_versions2?.candidates?.firstOrNull()
            if (imageCandidate != null) {
                val candidates = mediaOrAd.image_versions2.candidates
                val previewCandidate =
                    candidates.find { it.width in 640..1080 }
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
                    ),
                )
            }
        }

        val code = mediaOrAd.code ?: mediaOrAd.id
        val postUrl = "https://www.instagram.com/p/$code/"

        val actions =
            persistentListOf(
                ActionMenu.instagramComment(
                    statusKey = statusKey,
                    count = mediaOrAd.comment_count ?: 0L,
                    accountKey = accountKey,
                    openDetail = statusKeyOverride == null,
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

        val createdAtInstant =
            mediaOrAd.taken_at?.let { Instant.fromEpochSeconds(it) }
                ?: Clock.System.now()

        val post =
            UiTimelineV2.Post(
                platformId = "instagram",
                images = mediaList.toImmutableList(),
                sensitive = false,
                contentWarning = null,
                user = profile,
                platformIcon = UiIcon.Instagram,
                content = UiTranslatableText(original = captionText.toInstagramUiRichText(accountKey)),
                actions = actions,
                poll = null,
                statusKey = statusKey,
                card = null,
                createdAt = createdAtInstant.toUi(),
                clickEvent =
                    ClickEvent.Deeplink(
                        DeeplinkRoute.Status.Detail(
                            statusKey = statusKey,
                            accountType = AccountType.Specific(accountKey),
                        ),
                    ),
                accountType = AccountType.Specific(accountKey),
            )

        return UiTimelineV2.TimelinePostItem(post = post)
    }

    /**
     * Renders threaded Instagram comments (and any inline preview child replies) into timeline items.
     * Deeply branched comment trees expose an expandable "View X more replies" item.
     */
    public fun renderComments(
        comments: List<InstagramComment>,
        parentPostKey: MicroBlogKey,
        parentAuthorHandle: String?,
        accountKey: MicroBlogKey,
    ): List<UiTimelineV2> {
        val result = mutableListOf<UiTimelineV2>()
        for (comment in comments) {
            val topLevelItem =
                renderSingleComment(
                    comment = comment,
                    replyToStatusKey = parentPostKey,
                    replyToHandle = parentAuthorHandle,
                    accountKey = accountKey,
                ) ?: continue
            result.add(topLevelItem)

            val topLevelHandle = comment.user?.username
            val childComments = comment.preview_child_comments.orEmpty()
            childComments.forEach { childComment ->
                renderSingleComment(
                    comment = childComment,
                    replyToStatusKey = topLevelItem.statusKey,
                    replyToHandle = topLevelHandle,
                    accountKey = accountKey,
                )?.let { result.add(it) }
            }

            val totalChildCount = comment.child_comment_count ?: 0
            if (totalChildCount > childComments.size) {
                val remaining = totalChildCount - childComments.size
                val replyTargetKey =
                    MicroBlogKey(
                        id = "${parentPostKey.id.substringBefore("#")}#comment_${comment.resolvedPk}",
                        host = "instagram.com",
                    )
                val epochSeconds = comment.created_at ?: comment.created_at_utc
                val createdAtInstant =
                    epochSeconds?.let { Instant.fromEpochSeconds(it) }
                        ?: Clock.System.now()

                result.add(
                    UiTimelineV2.Message(
                        statusKey = replyTargetKey,
                        icon = UiIcon.Comment,
                        type = UiTimelineV2.Message.Type.Raw("View $remaining more ${if (remaining == 1) "reply" else "replies"}..."),
                        createdAt = createdAtInstant.toUi(),
                        clickEvent =
                            ClickEvent.Deeplink(
                                DeeplinkRoute.Compose.Reply(
                                    accountKey = accountKey,
                                    statusKey = replyTargetKey,
                                    initialText = if (!topLevelHandle.isNullOrBlank()) "@$topLevelHandle " else "",
                                ),
                            ),
                        accountType = AccountType.Specific(accountKey),
                    ),
                )
            }
        }
        return result
    }

    private fun renderSingleComment(
        comment: InstagramComment,
        replyToStatusKey: MicroBlogKey,
        replyToHandle: String?,
        accountKey: MicroBlogKey,
    ): UiTimelineV2.TimelinePostItem? {
        val user = comment.user ?: return null
        val profile = renderProfile(user, accountKey) ?: return null
        val text = comment.text?.takeIf { it.isNotBlank() } ?: return null

        val commentPk = comment.resolvedPk.takeIf { it.isNotBlank() } ?: return null
        val commentStatusKey =
            MicroBlogKey(
                id = "comment_$commentPk",
                host = "instagram.com",
            )
        val epochSeconds = comment.created_at ?: comment.created_at_utc
        val createdAtInstant =
            epochSeconds?.let { Instant.fromEpochSeconds(it) }
                ?: Clock.System.now()

        val replyTargetKey =
            MicroBlogKey(
                id = "${replyToStatusKey.id.substringBefore("#")}#comment_$commentPk",
                host = "instagram.com",
            )

        val commentMoreGroup =
            ActionMenu.Group(
                displayItem =
                    ActionMenu.Item(
                        icon = UiIcon.More,
                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.More),
                    ),
                actions =
                    persistentListOf(
                        ActionMenu.instagramShare(
                            statusKey = commentStatusKey,
                            shareUrl = "https://www.instagram.com/p/${replyToStatusKey.id.substringBefore("#")}/",
                            accountKey = accountKey,
                        ),
                    ),
            )

        val actions =
            persistentListOf(
                ActionMenu.instagramComment(
                    statusKey = replyTargetKey,
                    count = (comment.child_comment_count ?: 0).toLong(),
                    accountKey = accountKey,
                    openDetail = false,
                    replyToHandle = user.username,
                ),
                ActionMenu.instagramLike(
                    statusKey = commentStatusKey,
                    liked = comment.has_liked_comment == true,
                    count = comment.resolvedLikeCount,
                    accountKey = accountKey,
                ),
                commentMoreGroup,
            )

        val post =
            UiTimelineV2.Post(
                platformId = "instagram",
                images = persistentListOf(),
                sensitive = false,
                contentWarning = null,
                user = profile,
                platformIcon = UiIcon.Instagram,
                content = UiTranslatableText(original = text.toInstagramUiRichText(accountKey)),
                actions = actions,
                poll = null,
                statusKey = commentStatusKey,
                card = null,
                createdAt = createdAtInstant.toUi(),
                replyToHandle = replyToHandle,
                references =
                    persistentListOf(
                        UiTimelineV2.Post.Reference(
                            statusKey = replyToStatusKey,
                            type = ReferenceType.Reply,
                        ),
                    ),
                clickEvent = ClickEvent.Noop,
                accountType = AccountType.Specific(accountKey),
            )

        return UiTimelineV2.TimelinePostItem(post = post)
    }
}
