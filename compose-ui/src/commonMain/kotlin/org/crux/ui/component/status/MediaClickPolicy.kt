package org.crux.ui.component.status

import org.crux.ui.model.ClickContext
import org.crux.ui.model.UiMedia
import org.crux.ui.model.UiTimelineV2
import org.crux.ui.route.DeeplinkRoute
import org.crux.ui.route.toUri

internal fun UiTimelineV2.Post.openMedia(
    index: Int,
    launcher: (String) -> Unit,
) {
    val media = images.getOrNull(index) ?: return
    when (mediaClickPolicy) {
        UiTimelineV2.Post.MediaClickPolicy.OpenStatusMedia -> {
            launcher(statusMediaRoute(media, index).toUri())
        }

        UiTimelineV2.Post.MediaClickPolicy.OpenPostClickEvent -> {
            onClicked.invoke(ClickContext(launcher = launcher))
        }
    }
}

private fun UiTimelineV2.Post.statusMediaRoute(
    media: UiMedia,
    index: Int,
): DeeplinkRoute.Media.StatusMedia =
    DeeplinkRoute.Media.StatusMedia(
        statusKey = statusKey,
        accountType = accountType,
        index = index,
        preview =
            when (media) {
                is UiMedia.Image -> media.previewUrl
                is UiMedia.Video -> media.thumbnailUrl
                is UiMedia.Gif -> media.previewUrl
                is UiMedia.Audio -> null
            },
        aspectRatio =
            when (media) {
                is UiMedia.Image -> media.aspectRatio
                is UiMedia.Video -> media.aspectRatio
                is UiMedia.Gif -> media.aspectRatio
                is UiMedia.Audio -> 0f
            },
        previewIsImage = media is UiMedia.Image,
    )
