package org.crux.data.datasource.linkedin

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import moe.tlaster.twitter.parser.CashTagToken
import moe.tlaster.twitter.parser.EmojiToken
import moe.tlaster.twitter.parser.HashTagToken
import moe.tlaster.twitter.parser.StringToken
import moe.tlaster.twitter.parser.TwitterParser
import moe.tlaster.twitter.parser.UrlToken
import moe.tlaster.twitter.parser.UserNameToken
import org.crux.data.datasource.microblog.ActionMenu
import org.crux.data.datasource.microblog.PostActionFamily
import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import org.crux.model.ReferenceType
import org.crux.ui.model.ClickEvent
import org.crux.ui.model.UiCard
import org.crux.ui.model.UiHandle
import org.crux.ui.model.UiIcon
import org.crux.ui.model.UiMedia
import org.crux.ui.model.UiProfile
import org.crux.ui.model.UiTimelineV2
import org.crux.ui.model.UiTranslatableText
import org.crux.ui.model.mapper.linkedInComment
import org.crux.ui.model.mapper.linkedInLike
import org.crux.ui.model.mapper.linkedInShare
import org.crux.ui.model.toUiImage
import org.crux.ui.render.RenderContent
import org.crux.ui.render.RenderRun
import org.crux.ui.render.RenderTextStyle
import org.crux.ui.render.UiRichText
import org.crux.ui.render.toUi
import org.crux.ui.render.toUiPlainText
import org.crux.ui.render.uiRichTextOf
import org.crux.ui.route.DeeplinkRoute
import org.crux.ui.route.toUri
import kotlin.time.Clock
import kotlin.time.Instant

public object LinkedInTimelineMapper {
    private val linkedInParser: TwitterParser by lazy {
        TwitterParser(
            validMarkInUserName = listOf('.', '_', '-'),
            validMarkInHashTag = listOf('_'),
            enableEscapeInUrl = true,
            enableDomainDetection = true,
        )
    }

    public fun String.toLinkedInUiRichText(accountKey: MicroBlogKey): UiRichText {
        val tokens = runCatching { linkedInParser.parse(this) }.getOrNull()
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
                                                DeeplinkRoute
                                                    .OpenLinkDirectly(
                                                        "https://www.linkedin.com/in/$cleanUser",
                                                    ).toUri(),
                                        ),
                                ),
                            )
                        }

                        is HashTagToken -> {
                            val tag = token.value.trimStart('#')
                            add(
                                RenderRun.Text(
                                    text = token.value,
                                    style =
                                        RenderTextStyle(
                                            link =
                                                DeeplinkRoute
                                                    .OpenLinkDirectly(
                                                        "https://www.linkedin.com/feed/hashtag/?keywords=$tag",
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
            raw = this,
            innerText = this,
        )
    }

    public fun render(
        update: LinkedInUpdate,
        accountKey: MicroBlogKey,
        statusKeyOverride: MicroBlogKey? = null,
    ): UiTimelineV2.TimelinePostItem? {
        if (update.isSponsored == true) {
            return null
        }

        val actor = update.actor ?: return null
        val actorName =
            actor.name?.text?.takeIf { it.isNotBlank() }
                ?: actor.title?.text?.takeIf { it.isNotBlank() }
                ?: "LinkedIn Member"
        val headline =
            actor.description?.text?.takeIf { it.isNotBlank() }
                ?: actor.subtitle?.text?.takeIf { it.isNotBlank() }
                ?: actor.headline?.text?.takeIf { it.isNotBlank() }
                ?: actor.subDescription?.text?.takeIf { it.isNotBlank() }

        val id =
            update.id
                .ifBlank {
                    update.urn ?: update.entityUrn ?: update.trackingUrn
                }.takeIf { !it.isNullOrBlank() }
                ?: "li_${actorName.hashCode()}_${update.created?.time ?: Clock.System.now().toEpochMilliseconds()}"

        val statusKey =
            statusKeyOverride ?: MicroBlogKey(
                id = id,
                host = "linkedin.com",
            )
        val authorKey =
            MicroBlogKey(
                id = actor.urn ?: actor.entityUrn ?: actorName,
                host = "linkedin.com",
            )

        val avatarUrl = extractAvatarUrl(actor.image)
        val profileUrl = actor.navigationUrl ?: "https://www.linkedin.com/in/"
        val cleanHandle = extractHandle(actor.navigationUrl, actorName)

        val profile =
            UiProfile(
                key = authorKey,
                handle =
                    UiHandle(
                        raw = cleanHandle,
                        host = "linkedin.com",
                    ),
                avatar = avatarUrl.toUiImage(),
                nameInternal = actorName.toUiPlainText(),
                platformId = "linkedin",
                platformIcon = UiIcon.LinkedIn,
                clickEvent =
                    ClickEvent.Deeplink(
                        DeeplinkRoute.OpenLinkDirectly(profileUrl),
                    ),
                banner = null,
                description = headline?.toUiPlainText(),
                matrices =
                    UiProfile.Matrices(
                        fansCount = 0,
                        followsCount = 0,
                        statusesCount = 0,
                        platformFansCount = "",
                    ),
                mark = persistentListOf(),
                bottomContent = null,
            )

        val commentaryText =
            update.commentary
                ?.text
                ?.text
                ?.takeIf { it.isNotBlank() }
                ?: update.header?.text?.takeIf { it.isNotBlank() }
                ?: update.title?.text?.takeIf { it.isNotBlank() }
                ?: update.content
                    ?.articleComponent
                    ?.title
                    ?.takeIf { it.isNotBlank() }
                ?: update.content
                    ?.entityComponent
                    ?.title
                    ?.text
                    ?.takeIf { it.isNotBlank() }
                ?: update.content
                    ?.documentComponent
                    ?.title
                    ?.takeIf { it.isNotBlank() }
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
                        ),
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
                ),
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
                ),
            )
        }

        // 3. Document Carousel Preview
        val docComp = update.content?.documentComponent
        if (docComp != null && !docComp.thumbnail.isNullOrBlank() && mediaList.isEmpty()) {
            mediaList.add(
                UiMedia.Image(
                    url = docComp.thumbnail,
                    previewUrl = docComp.thumbnail,
                    description = docComp.title ?: commentaryText,
                    height = 600f,
                    width = 800f,
                    sensitive = false,
                ),
            )
        }

        // 4. Article / Link / Document Preview Card
        val card = extractCard(update.content, commentaryText)

        // 5. Poll Component
        val poll =
            update.content?.pollComponent?.let { p ->
                val options =
                    p.options.map { opt ->
                        org.crux.ui.model.UiPoll.Option(
                            title = opt.optionText?.text ?: "",
                            votesCount = opt.voteCount ?: 0L,
                            percentage = (opt.votePercentage ?: 0).toFloat() / 100f,
                        )
                    }
                org.crux.ui.model.UiPoll(
                    id = "poll_$id",
                    options = options.toImmutableList(),
                    multiple = false,
                    ownVotes = persistentListOf(),
                    voteEvent = null,
                    expiresAt = if (p.pollStatus.equals("CLOSED", ignoreCase = true)) Clock.System.now() else null,
                    enabled = false,
                )
            }

        val postUrl = update.permalink ?: "https://www.linkedin.com/feed/update/$id/"

        val counts = update.socialDetail?.totalSocialActivityCounts
        val actions =
            persistentListOf(
                ActionMenu.linkedInComment(
                    statusKey = statusKey,
                    count = counts?.numComments ?: 0L,
                    accountKey = accountKey,
                ),
                ActionMenu.linkedInLike(
                    statusKey = statusKey,
                    liked = update.socialDetail?.reactions?.hasLiked == true,
                    count = counts?.numLikes ?: 0L,
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
                            ActionMenu.Item(
                                text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.SmartReply),
                                clickEvent =
                                    ClickEvent.Deeplink(
                                        DeeplinkRoute.Status.SmartReply(
                                            statusKey = statusKey,
                                            accountType = AccountType.Specific(accountKey),
                                            initialText = commentaryText,
                                            authorName = actorName,
                                            authorHandle = cleanHandle,
                                            mediaDescription =
                                                mediaList
                                                    .mapNotNull {
                                                        it.description?.takeIf { d ->
                                                            d.isNotBlank()
                                                        }
                                                    }.joinToString("\n"),
                                        ),
                                    ),
                                icon = UiIcon.Reply,
                                actionFamily = PostActionFamily.Reply,
                            ),
                            ActionMenu.linkedInShare(
                                statusKey = statusKey,
                                shareUrl = postUrl,
                                accountKey = accountKey,
                            ),
                        ),
                ),
            )

        val createdAtInstant =
            update.created?.time?.let { Instant.fromEpochMilliseconds(it) }
                ?: Clock.System.now()

        val post =
            UiTimelineV2.Post(
                platformId = "linkedin",
                images = mediaList.toImmutableList(),
                sensitive = false,
                contentWarning = null,
                user = profile,
                platformIcon = UiIcon.LinkedIn,
                content = UiTranslatableText(original = commentaryText.toLinkedInUiRichText(accountKey)),
                actions = actions,
                poll = poll,
                statusKey = statusKey,
                card = card,
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

        // 6. Handle Quote / Reshared Update if present
        val presentation =
            if (update.resharedUpdate != null) {
                val resharedItem = render(update.resharedUpdate, accountKey)
                if (resharedItem != null) {
                    if (commentaryText.isBlank()) {
                        UiTimelineV2.PostPresentation(
                            repost = resharedItem.post,
                        )
                    } else {
                        UiTimelineV2.PostPresentation(
                            quotes = persistentListOf(resharedItem.post),
                        )
                    }
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

    internal fun extractHandle(
        navigationUrl: String?,
        actorName: String,
    ): String {
        if (!navigationUrl.isNullOrBlank()) {
            val match = Regex("/(?:in|company|school)/([^/?#]+)").find(navigationUrl)
            if (match != null) {
                val slug = match.groupValues[1].trim().removeSuffix("/")
                if (slug.isNotBlank()) return slug
            }
        }
        val cleaned =
            actorName
                .lowercase()
                .replace(Regex("[^a-z0-9_]"), "")
                .take(30)
        return if (cleaned.isNotBlank()) cleaned else "linkedin"
    }

    private fun extractAvatarUrl(imageAttr: LinkedInImageAttributes?): String? {
        if (!imageAttr?.url.isNullOrBlank()) return imageAttr.url
        if (!imageAttr?.sourceWebImageUrl.isNullOrBlank()) return imageAttr.sourceWebImageUrl
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
                val artifactPath =
                    vector.artifacts
                        .lastOrNull()
                        ?.fileIdentifyingUrlPathSegment
                        .orEmpty()
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

    private fun extractCard(
        content: LinkedInContent?,
        fallbackText: String,
    ): UiCard? {
        val article = content?.articleComponent
        if (article != null && !article.navigationUrl.isNullOrBlank()) {
            val thumbMedia =
                article.thumbnail?.let { url ->
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

        val doc = content?.documentComponent
        if (doc != null && !doc.navigationUrl.isNullOrBlank()) {
            val thumbMedia =
                doc.thumbnail?.let { url ->
                    UiMedia.Image(
                        url = url,
                        previewUrl = url,
                        description = doc.title ?: fallbackText,
                        height = 400f,
                        width = 800f,
                        sensitive = false,
                    )
                }
            return UiCard(
                title = doc.title ?: "Document",
                description = "${doc.totalPageCount ?: 1} page document carousel",
                media = thumbMedia,
                url = doc.navigationUrl,
            )
        }

        val mediaComp = content?.mediaComponent
        if (mediaComp != null && !mediaComp.navigationUrl.isNullOrBlank()) {
            val thumbMedia =
                mediaComp.thumbnail?.let { url ->
                    UiMedia.Image(
                        url = url,
                        previewUrl = url,
                        description = mediaComp.title?.text ?: fallbackText,
                        height = 400f,
                        width = 800f,
                        sensitive = false,
                    )
                }
            return UiCard(
                title = mediaComp.title?.text ?: "LinkedIn Media",
                description = mediaComp.subtitle?.text,
                media = thumbMedia,
                url = mediaComp.navigationUrl,
            )
        }

        val entity = content?.entityComponent
        if (entity != null && !entity.navigationUrl.isNullOrBlank()) {
            val thumbMedia =
                entity.thumbnail?.let { url ->
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

    public fun renderComments(
        comments: List<LinkedInComment>,
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

            val topLevelHandle =
                extractHandle(
                    comment.commenter?.navigationUrl,
                    comment.commenter
                        ?.name
                        ?.text
                        .orEmpty(),
                )
            val childComments = comment.comments
            childComments.forEach { childComment ->
                renderSingleComment(
                    comment = childComment,
                    replyToStatusKey = topLevelItem.statusKey,
                    replyToHandle = topLevelHandle,
                    accountKey = accountKey,
                )?.let { result.add(it) }
            }
        }
        return result
    }

    public fun renderSingleComment(
        comment: LinkedInComment,
        replyToStatusKey: MicroBlogKey,
        replyToHandle: String?,
        accountKey: MicroBlogKey,
    ): UiTimelineV2.TimelinePostItem? {
        val actor = comment.commenter ?: return null
        val actorName =
            actor.name?.text?.takeIf { it.isNotBlank() }
                ?: actor.title?.text?.takeIf { it.isNotBlank() }
                ?: "LinkedIn Member"
        val headline =
            actor.description?.text?.takeIf { it.isNotBlank() }
                ?: actor.subtitle?.text?.takeIf { it.isNotBlank() }
                ?: actor.headline?.text?.takeIf { it.isNotBlank() }

        val commentText =
            comment.commentary
                ?.text
                ?.text
                ?.takeIf { it.isNotBlank() } ?: return null

        val id =
            comment.id
                .ifBlank {
                    comment.urn ?: comment.entityUrn
                }.takeIf { !it.isNullOrBlank() }
                ?: "li_comment_${actorName.hashCode()}_${comment.created?.time ?: Clock.System.now().toEpochMilliseconds()}"

        val commentStatusKey =
            MicroBlogKey(
                id = id,
                host = "linkedin.com",
            )
        val authorKey =
            MicroBlogKey(
                id = actor.urn ?: actor.entityUrn ?: actorName,
                host = "linkedin.com",
            )

        val avatarUrl = extractAvatarUrl(actor.image)
        val profileUrl = actor.navigationUrl ?: "https://www.linkedin.com/in/"
        val cleanHandle = extractHandle(actor.navigationUrl, actorName)

        val profile =
            UiProfile(
                key = authorKey,
                handle =
                    UiHandle(
                        raw = cleanHandle,
                        host = "linkedin.com",
                    ),
                avatar = avatarUrl.toUiImage(),
                nameInternal = actorName.toUiPlainText(),
                platformId = "linkedin",
                platformIcon = UiIcon.LinkedIn,
                clickEvent =
                    ClickEvent.Deeplink(
                        DeeplinkRoute.OpenLinkDirectly(profileUrl),
                    ),
                banner = null,
                description = headline?.toUiPlainText(),
                matrices =
                    UiProfile.Matrices(
                        fansCount = 0,
                        followsCount = 0,
                        statusesCount = 0,
                        platformFansCount = "",
                    ),
                mark = persistentListOf(),
                bottomContent = null,
            )

        val counts = comment.socialDetail?.totalSocialActivityCounts
        val replyTargetKey =
            MicroBlogKey(
                id = "${replyToStatusKey.id.substringBefore("#")}#$id",
                host = "linkedin.com",
            )

        val actions =
            persistentListOf(
                ActionMenu.linkedInComment(
                    statusKey = replyTargetKey,
                    count = counts?.numComments ?: comment.comments.size.toLong(),
                    accountKey = accountKey,
                    initialText = if (cleanHandle.isNotBlank()) "@$cleanHandle " else "",
                ),
                ActionMenu.linkedInLike(
                    statusKey = commentStatusKey,
                    liked = comment.socialDetail?.reactions?.hasLiked == true,
                    count = counts?.numLikes ?: 0L,
                    accountKey = accountKey,
                ),
            )

        val createdAtInstant =
            comment.created?.time?.let { Instant.fromEpochMilliseconds(it) }
                ?: Clock.System.now()

        val post =
            UiTimelineV2.Post(
                platformId = "linkedin",
                images = persistentListOf(),
                sensitive = false,
                contentWarning = null,
                user = profile,
                platformIcon = UiIcon.LinkedIn,
                content = UiTranslatableText(original = commentText.toLinkedInUiRichText(accountKey)),
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
                clickEvent =
                    ClickEvent.Deeplink(
                        DeeplinkRoute.Compose.Reply(
                            accountKey = accountKey,
                            statusKey = replyTargetKey,
                            initialText = if (cleanHandle.isNotBlank()) "@$cleanHandle " else "",
                        ),
                    ),
                accountType = AccountType.Specific(accountKey),
            )

        return UiTimelineV2.TimelinePostItem(
            post = post,
        )
    }
}
