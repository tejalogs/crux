package org.crux.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import org.crux.ui.model.UiMedia

@Composable
public fun GalleryMedia(
    media: UiMedia,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    NetworkImage(
        media.url,
        media.accessibleDescription(),
        modifier,
        contentScale = contentScale,
        customHeaders = media.customHeaders,
    )
}
