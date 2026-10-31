package dev.dimension.flare.ui.component.status

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.EyeSlash
import compose.icons.fontawesomeicons.solid.Heart
import compose.icons.fontawesomeicons.solid.Images
import compose.icons.fontawesomeicons.solid.Play
import dev.dimension.flare.compose.ui.Res
import dev.dimension.flare.compose.ui.feed_open_article
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.PostActionFamily
import dev.dimension.flare.data.model.VideoAutoplay
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.component.placeholder
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.model.ClickContext
import dev.dimension.flare.ui.model.TranslationDisplayState
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.toUiImage
import dev.dimension.flare.ui.theme.PlatformTheme
import org.jetbrains.compose.resources.stringResource

private val GalleryTileShape = RoundedCornerShape(12.dp)
private const val GALLERY_TEXT_MAX_LINES = 5
private const val GALLERY_FEED_TITLE_MAX_LINES = 2
private const val GALLERY_FEED_DESC_MAX_LINES = 3
private val GalleryAvatarSize = 24.dp

@Composable
public fun GalleryTimelineItem(
    item: UiTimelineV2?,
    modifier: Modifier = Modifier,
) {
    when (item) {
        is UiTimelineV2.Post -> {
            GalleryPostTile(post = item, modifier = modifier)
        }

        is UiTimelineV2.TimelinePostItem -> {
            GalleryPostTile(post = item.displayPost, modifier = modifier)
        }

        is UiTimelineV2.Feed -> {
            GalleryFeedTile(feed = item, modifier = modifier)
        }

        null -> {
            GalleryPlaceholderTile(modifier = modifier)
        }

        else -> {
            // Unsupported types in gallery mode — fall back to the regular renderer inside the tile.
            Box(
                modifier =
                    modifier
                        .fillMaxWidth()
                        .clip(GalleryTileShape)
                        .background(PlatformTheme.colorScheme.card),
            ) {
                StatusItem(item = item)
            }
        }
    }
}

@Composable
private fun GalleryPostTile(
    post: UiTimelineV2.Post,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val appearance = LocalTimelineAppearance.current
    var isMenuExpanded by remember(post.itemKey) { mutableStateOf(false) }
    var revealedSensitive by remember(post.itemKey) { mutableStateOf(false) }
    val showOriginalWithTranslation = appearance.aiConfig.showOriginalWithTranslation
    val translation = post.content.translation
    val visibleContent =
        if (post.translationDisplayState == TranslationDisplayState.Translated && translation != null) {
            if (showOriginalWithTranslation) {
                listOf(post.content.original, translation)
            } else {
                listOf(translation)
            }
        } else {
            listOf(post.content.original)
        }
    val likeCount =
        remember(post.actions) {
            post.actions
                .filterIsInstance<ActionMenu.Item>()
                .firstOrNull { it.actionFamily == PostActionFamily.Like || it.actionFamily == PostActionFamily.Favorite }
                ?.count
                ?.humanized
                ?: (
                    post.actions
                        .filterIsInstance<ActionMenu.Group>()
                        .firstOrNull {
                            it.displayItem.actionFamily == PostActionFamily.Like ||
                                it.displayItem.actionFamily == PostActionFamily.Favorite
                        }?.displayItem
                        ?.count
                        ?.humanized
                )
        }

    TimelineMediaMenuBox(
        expanded = isMenuExpanded,
        onExpandedChange = { isMenuExpanded = it },
        onClick = {
            post.onClicked.invoke(
                ClickContext(launcher = { uriHandler.openUri(it) }),
            )
        },
        modifier =
            modifier
                .fillMaxWidth()
                .clip(GalleryTileShape)
                .background(PlatformTheme.colorScheme.card),
        menu = {
            StatusActionsDropdownMenu(
                items = post.actions,
                expanded = isMenuExpanded,
                onDismissRequest = { isMenuExpanded = false },
            )
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (post.images.isNotEmpty() && appearance.showMedia) {
                val firstMedia = post.images.first()
                CompositionLocalProvider(
                    LocalTimelineAppearance provides
                        appearance.copy(videoAutoplay = VideoAutoplay.NEVER),
                ) {
                    TimelineMediaMenuBox(
                        expanded = isMenuExpanded,
                        onExpandedChange = { isMenuExpanded = it },
                        onClick = {
                            if (post.sensitive && !revealedSensitive) {
                                revealedSensitive = true
                            } else {
                                post.openMedia(
                                    index = 0,
                                    launcher = uriHandler::openUri,
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        menu = {},
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (post.sensitive && !revealedSensitive) {
                                Column(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .height(180.dp)
                                            .background(PlatformTheme.colorScheme.card)
                                            .padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    FAIcon(
                                        imageVector = FontAwesomeIcons.Solid.EyeSlash,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                        tint = PlatformTheme.colorScheme.caption,
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    PlatformText(
                                        text = post.contentWarning?.original?.raw ?: "Sensitive Media",
                                        style = PlatformTheme.typography.caption,
                                        color = PlatformTheme.colorScheme.caption,
                                        maxLines = 2,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    PlatformText(
                                        text = "Tap to show",
                                        style = PlatformTheme.typography.caption,
                                        fontWeight = FontWeight.Bold,
                                        color = PlatformTheme.colorScheme.primary,
                                    )
                                }
                            } else {
                                MediaItem(
                                    media = firstMedia,
                                    keepAspectRatio = true,
                                )
                                if (post.images.size > 1) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(6.dp)
                                                .background(
                                                    color = Color.Black.copy(alpha = 0.65f),
                                                    shape = RoundedCornerShape(4.dp),
                                                ).padding(horizontal = 5.dp, vertical = 2.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        ) {
                                            FAIcon(
                                                imageVector = FontAwesomeIcons.Solid.Images,
                                                contentDescription = null,
                                                modifier = Modifier.size(11.dp),
                                                tint = Color.White,
                                            )
                                            PlatformText(
                                                text = post.images.size.toString(),
                                                style = PlatformTheme.typography.caption.copy(fontSize = 10.sp),
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                } else if (firstMedia is UiMedia.Video) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(6.dp)
                                                .background(
                                                    color = Color.Black.copy(alpha = 0.65f),
                                                    shape = RoundedCornerShape(4.dp),
                                                ).padding(4.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        FAIcon(
                                            imageVector = FontAwesomeIcons.Solid.Play,
                                            contentDescription = null,
                                            modifier = Modifier.size(10.dp),
                                            tint = Color.White,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    visibleContent.forEach { content ->
                        if (!content.isEmpty) {
                            RichText(
                                text = content,
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = GALLERY_TEXT_MAX_LINES,
                            )
                        }
                    }
                }
            }
            GalleryBottomRow(
                avatar = post.user?.avatar,
                name =
                    post.user
                        ?.name
                        ?.raw
                        .orEmpty(),
                isCircle = true,
                likeCount = likeCount,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun GalleryFeedTile(
    feed: UiTimelineV2.Feed,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val appearance = LocalTimelineAppearance.current
    val openArticleLabel = stringResource(Res.string.feed_open_article)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(GalleryTileShape)
                .background(PlatformTheme.colorScheme.card)
                .clickable(onClickLabel = openArticleLabel) {
                    feed.onClicked.invoke(
                        ClickContext(launcher = { uriHandler.openUri(it) }),
                    )
                },
    ) {
        val media = feed.media
        if (media != null && appearance.showMedia) {
            MediaItem(
                media = media,
                modifier = Modifier.fillMaxWidth(),
                keepAspectRatio = true,
            )
            feed.title?.takeIf { it.isNotBlank() }?.let {
                PlatformText(
                    text = it,
                    style = PlatformTheme.typography.caption,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                feed.title?.takeIf { it.isNotBlank() }?.let {
                    PlatformText(
                        text = it,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = GALLERY_FEED_TITLE_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                feed.description?.takeIf { it.isNotBlank() }?.let {
                    PlatformText(
                        text = it,
                        style = PlatformTheme.typography.caption,
                        color = PlatformTheme.colorScheme.caption,
                        maxLines = GALLERY_FEED_DESC_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        GalleryBottomRow(
            avatar = feed.source.icon.toUiImage(),
            name = feed.source.name,
            isCircle = false,
            likeCount = null,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
        )
    }
}

@Composable
private fun GalleryBottomRow(
    avatar: UiMedia.Image?,
    name: String,
    isCircle: Boolean,
    likeCount: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isCircle) {
            AvatarComponent(
                data = avatar,
                size = GalleryAvatarSize,
            )
        } else {
            NetworkImage(
                model = avatar?.url,
                contentDescription = null,
                customHeaders = avatar?.customHeaders,
                modifier =
                    Modifier
                        .size(GalleryAvatarSize)
                        .clip(RoundedCornerShape(4.dp)),
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        PlatformText(
            text = name,
            style = PlatformTheme.typography.caption,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (!likeCount.isNullOrEmpty()) {
            Spacer(modifier = Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                FAIcon(
                    imageVector = FontAwesomeIcons.Solid.Heart,
                    contentDescription = null,
                    modifier = Modifier.size(10.dp),
                    tint = PlatformTheme.colorScheme.caption,
                )
                PlatformText(
                    text = likeCount,
                    style = PlatformTheme.typography.caption.copy(fontSize = 11.sp),
                    color = PlatformTheme.colorScheme.caption,
                )
            }
        }
    }
}

@Composable
private fun GalleryPlaceholderTile(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(GalleryTileShape)
                .background(PlatformTheme.colorScheme.card),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .placeholder(true),
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(GalleryAvatarSize)
                        .clip(RoundedCornerShape(50))
                        .placeholder(true),
            )
            Spacer(modifier = Modifier.width(6.dp))
            PlatformText(
                text = "username",
                style = PlatformTheme.typography.caption,
                modifier = Modifier.placeholder(true),
            )
        }
    }
}
