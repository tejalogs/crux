package dev.dimension.flare.ui.component.status

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.WandMagicSparkles
import dev.dimension.flare.compose.ui.Res
import dev.dimension.flare.compose.ui.feed_open_article
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.DateTimeText
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.NetworkImage
import dev.dimension.flare.ui.component.accessibleDescription
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.component.platform.PlatformTextButton
import dev.dimension.flare.ui.model.ClickContext
import dev.dimension.flare.ui.model.TranslationDisplayState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.route.DeeplinkRoute
import dev.dimension.flare.ui.route.toUri
import dev.dimension.flare.ui.theme.PlatformTheme
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FeedComponent(
    data: UiTimelineV2.Feed,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val openArticleLabel = stringResource(Res.string.feed_open_article)
    Column(
        modifier =
            Modifier
                .clickable(onClickLabel = openArticleLabel) {
                    data.onClicked.invoke(
                        ClickContext(uriHandler::openUri),
                    )
                }.padding(
                    horizontal = screenHorizontalPadding,
                    vertical = 8.dp,
                ).then(modifier),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            data.source.icon?.let {
                NetworkImage(
                    it,
                    contentDescription = data.source.name,
                    modifier = Modifier.size(16.dp),
                )
            }
            PlatformText(
                text = data.source.name,
                style = PlatformTheme.typography.caption,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            AnimatedVisibility(data.translationDisplayState != TranslationDisplayState.Hidden) {
                TranslationDisplayBadge(
                    state = data.translationDisplayState,
                )
            }
            data.actualCreatedAt?.let {
                DateTimeText(
                    it,
                    style = PlatformTheme.typography.caption,
                    color = PlatformTheme.colorScheme.caption,
                )
            }
        }
        data.title?.let { title ->
            PlatformText(
                text = title,
                style = PlatformTheme.typography.title,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            data.description?.let {
                PlatformText(
                    text = it,
                    style = PlatformTheme.typography.caption,
                    maxLines = 5,
                    color = PlatformTheme.colorScheme.caption,
                    modifier =
                        Modifier.let {
                            if (data.media != null) {
                                it.weight(1f)
                            } else {
                                it
                            }
                        },
                )
            }
            data.media?.let {
                NetworkImage(
                    model = it.url,
                    contentDescription = it.accessibleDescription(),
                    modifier =
                        Modifier
                            .let {
                                if (data.description != null) {
                                    it.size(80.dp)
                                } else {
                                    it.aspectRatio(16f / 9f)
                                }
                            }.clip(
                                PlatformTheme.shapes.medium,
                            ),
                    customHeaders = it.customHeaders,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            PlatformTextButton(
                onClick = {
                    val url = data.url
                    val title = data.title.orEmpty()
                    val body = data.descriptionHtml?.takeIf { it.isNotBlank() } ?: data.description.orEmpty()
                    val author = data.source.name
                    val uri = DeeplinkRoute.Status.OmniScribe(
                        statusKey = MicroBlogKey(url.hashCode().toString(), "rss"),
                        accountType = AccountType.Guest,
                        initialText = body,
                        authorName = author,
                        authorHandle = "rss",
                        articleTitle = title,
                        sourceUrl = url,
                    ).toUri()
                    uriHandler.openUri(uri)
                },
            ) {
                FAIcon(
                    imageVector = FontAwesomeIcons.Solid.WandMagicSparkles,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = PlatformTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(5.dp))
                PlatformText(
                    text = "Synthesize Post & Carousel",
                    style = PlatformTheme.typography.caption,
                    fontWeight = FontWeight.SemiBold,
                    color = PlatformTheme.colorScheme.primary,
                )
            }
        }
    }
}
