package org.crux.ui.component

import androidx.compose.runtime.Composable
import org.crux.compose.ui.Res
import org.crux.compose.ui.media_audio_no_description
import org.crux.compose.ui.media_image_no_alt
import org.crux.compose.ui.media_video_no_alt
import org.crux.ui.model.UiMedia
import org.jetbrains.compose.resources.stringResource

@Composable
public fun UiMedia.accessibleDescription(): String =
    description?.takeIf { it.isNotBlank() }
        ?: when (this) {
            is UiMedia.Image,
            is UiMedia.Gif,
            -> stringResource(Res.string.media_image_no_alt)

            is UiMedia.Video -> stringResource(Res.string.media_video_no_alt)

            is UiMedia.Audio -> stringResource(Res.string.media_audio_no_description)
        }
