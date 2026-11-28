package org.crux.ui.screen.media

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.dp
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Download
import compose.icons.fontawesomeicons.solid.ShareNodes
import compose.icons.fontawesomeicons.solid.Xmark
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import org.crux.common.AndroidDownloadManager
import org.crux.common.MediaFileNamePolicy
import org.crux.common.shareImageMedia
import org.crux.ui.component.FAIcon
import org.crux.ui.model.UiMedia
import org.koin.compose.koinInject

private fun isAllowedMediaScheme(url: String?): Boolean {
    val trimmed = url?.trim()?.lowercase() ?: return false
    return trimmed.startsWith("https://") || trimmed.startsWith("http://")
}

@Composable
internal fun MediaScreen(
    uri: String,
    previewUrl: String?,
    customHeaders: Map<String, String>?,
    onDismiss: () -> Unit,
    toAltText: (UiMedia) -> Unit = {},
) {
    if (!isAllowedMediaScheme(uri)) {
        LaunchedEffect(uri) { onDismiss() }
        return
    }
    val safePreviewUrl = previewUrl?.takeIf(::isAllowedMediaScheme) ?: uri
    RawMediaScreen(
        medias =
            persistentListOf(
                UiMedia.Image(
                    url = uri,
                    previewUrl = safePreviewUrl,
                    description = null,
                    height = 0f,
                    width = 0f,
                    sensitive = false,
                    customHeaders = customHeaders?.toImmutableMap(),
                ),
            ),
        index = 0,
        preview = previewUrl,
        onDismiss = onDismiss,
        toAltText = toAltText,
        uriHandler = LocalUriHandler.current,
    )
}

@Composable
internal fun RawMediaScreen(
    medias: ImmutableList<UiMedia>,
    index: Int,
    @Suppress("UNUSED_PARAMETER") preview: String?,
    onDismiss: () -> Unit,
    @Suppress("UNUSED_PARAMETER") toAltText: (UiMedia) -> Unit,
    @Suppress("UNUSED_PARAMETER") uriHandler: UriHandler,
) {
    val context = LocalContext.current
    val downloadManager = koinInject<AndroidDownloadManager>()
    val scope = rememberCoroutineScope()
    val safeInitial = index.coerceIn(0, (medias.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(initialPage = safeInitial, pageCount = { medias.size.coerceAtLeast(1) })
    val currentMedia = medias.getOrNull(pagerState.currentPage)

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        if (medias.isNotEmpty()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val media = medias[page]
                val imageUrl =
                    when (media) {
                        is UiMedia.Image -> media.url
                        else -> media.urlForDownload
                    }
                val request =
                    remember(imageUrl, media.customHeaders) {
                        ImageRequest
                            .Builder(context)
                            .data(imageUrl)
                            .let { builder ->
                                val headers = media.customHeaders
                                if (!headers.isNullOrEmpty()) {
                                    builder.httpHeaders(
                                        NetworkHeaders
                                            .Builder()
                                            .apply { headers.forEach { (k, v) -> set(k, v) } }
                                            .build(),
                                    )
                                } else {
                                    builder
                                }
                            }.build()
                    }
                ZoomableAsyncImage(
                    model = request,
                    contentDescription = media.description,
                    state = rememberZoomableImageState(),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
            ) {
                IconButton(onClick = onDismiss) {
                    FAIcon(
                        FontAwesomeIcons.Solid.Xmark,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            if (medias.size > 1) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${medias.size}",
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            if (currentMedia != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.55f),
                    ) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val fileName = MediaFileNamePolicy.rawMediaFileName(currentMedia)
                                    downloadManager.downloadMedia(
                                        uri = currentMedia.urlForDownload,
                                        fileName = fileName,
                                        customHeaders = currentMedia.customHeaders,
                                        callback =
                                            object : AndroidDownloadManager.DownloadCallback {
                                                override fun onDownloadSuccess(downloadId: Long) {
                                                    Toast.makeText(context, "Saved $fileName", Toast.LENGTH_SHORT).show()
                                                }

                                                override fun onDownloadFailed(downloadId: Long) {
                                                    Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                    )
                                }
                            },
                        ) {
                            FAIcon(
                                FontAwesomeIcons.Solid.Download,
                                contentDescription = "Save image",
                                tint = Color.White,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }
                    if (currentMedia is UiMedia.Image) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.55f),
                        ) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        val fileName = MediaFileNamePolicy.rawMediaFileName(currentMedia)
                                        shareImageMedia(
                                            context = context,
                                            media = currentMedia,
                                            fileName = fileName,
                                        )
                                    }
                                },
                            ) {
                                FAIcon(
                                    FontAwesomeIcons.Solid.ShareNodes,
                                    contentDescription = "Share image",
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
