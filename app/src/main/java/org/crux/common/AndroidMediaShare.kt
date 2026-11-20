package org.crux.common

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import coil3.annotation.ExperimentalCoilApi
import coil3.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.crux.R
import org.crux.data.network.PublicUrlValidator
import org.crux.ui.model.UiMedia
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val MAX_SHARED_IMAGE_BYTES = 25L * 1024 * 1024
private const val MAX_SHARE_REDIRECTS = 5
private const val SHARE_CONNECT_TIMEOUT_MS = 15_000
private const val SHARE_READ_TIMEOUT_MS = 30_000
private val SHARE_REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

@OptIn(ExperimentalCoilApi::class)
internal suspend fun shareImageMedia(
    context: Context,
    media: UiMedia.Image,
    fileName: String,
) {
    val shareDir = File(context.cacheDir, "share").apply { mkdirs() }
    val targetFile =
        File(
            shareDir,
            MediaFileNamePolicy.safeLocalFileName(fileName, fallback = "media"),
        )
    val success =
        withContext(Dispatchers.IO) {
            if (!PublicUrlValidator.isSafePublicHttpUrl(media.url)) {
                return@withContext false
            }
            context.imageLoader.diskCache?.openSnapshot(media.url)?.use {
                it.data.toFile().copyTo(targetFile, overwrite = true)
                true
            } ?: run {
                withContext(Dispatchers.Main) {
                    Toast
                        .makeText(
                            context,
                            context.getString(R.string.media_prepare_started),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
                downloadToFile(
                    url = media.url,
                    targetFile = targetFile,
                    customHeaders = media.customHeaders,
                )
            }
        }

    withContext(Dispatchers.Main) {
        if (success) {
            val uri =
                FileProvider.getUriForFile(
                    context,
                    context.packageName + ".provider",
                    targetFile,
                )
            val intent =
                Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_STREAM, uri)
                    setDataAndType(uri, "image/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            context.startActivity(
                Intent.createChooser(
                    intent,
                    context.getString(R.string.media_menu_share_image),
                ),
            )
        } else {
            Toast
                .makeText(
                    context,
                    context.getString(R.string.media_save_fail),
                    Toast.LENGTH_SHORT,
                ).show()
        }
    }
}

internal fun downloadToFile(
    url: String,
    targetFile: File,
    customHeaders: Map<String, String>?,
): Boolean =
    runCatching {
        var currentUrl = url.trim()
        var redirectsLeft = MAX_SHARE_REDIRECTS
        while (true) {
            if (!PublicUrlValidator.isSafePublicHttpUrl(currentUrl)) {
                return@runCatching false
            }
            val parsed = URL(currentUrl)
            val connection = (parsed.openConnection() as? HttpURLConnection) ?: return@runCatching false
            connection.connectTimeout = SHARE_CONNECT_TIMEOUT_MS
            connection.readTimeout = SHARE_READ_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            customHeaders?.forEach { (key, value) ->
                connection.setRequestProperty(key, value)
            }
            try {
                connection.connect()
                val code = connection.responseCode
                if (code in SHARE_REDIRECT_CODES) {
                    val location = connection.getHeaderField("Location")
                    if (location.isNullOrBlank() || redirectsLeft <= 0) {
                        return@runCatching false
                    }
                    currentUrl = URL(parsed, location).toString()
                    redirectsLeft--
                    continue
                }
                if (code !in 200..299) {
                    return@runCatching false
                }
                targetFile.parentFile?.mkdirs()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                connection.inputStream.use { input ->
                    targetFile.outputStream().use { output ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > MAX_SHARED_IMAGE_BYTES) {
                                targetFile.delete()
                                return@runCatching false
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                }
                return@runCatching true
            } finally {
                connection.disconnect()
            }
        }
        @Suppress("UNREACHABLE_CODE")
        false
    }.onFailure {
        targetFile.delete()
    }.getOrDefault(false)
