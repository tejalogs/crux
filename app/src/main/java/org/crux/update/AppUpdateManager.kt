package org.crux.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.crux.CruxLinks
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

public data class AppReleaseInfo(
    val tagName: String,
    val versionName: String,
    val title: String,
    val changelog: String,
    val releaseUrl: String,
    val apkUrl: String?,
    val apkSizeBytes: Long,
)

public sealed interface UpdateCheckResult {
    public data object UpToDate : UpdateCheckResult
    public data class UpdateAvailable(val info: AppReleaseInfo) : UpdateCheckResult
    public data class Error(val message: String) : UpdateCheckResult
}

public object AppUpdateManager {

    public fun isNewerVersion(remoteTag: String, localVersion: String): Boolean {
        val cleanRemote = remoteTag.trim().trimStart('v', 'V').substringBefore('-')
        val cleanLocal = localVersion.trim().trimStart('v', 'V').substringBefore('-')

        val remoteParts = cleanRemote.split('.').mapNotNull { it.toIntOrNull() }
        val localParts = cleanLocal.split('.').mapNotNull { it.toIntOrNull() }

        val length = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until length) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    public suspend fun checkForUpdates(currentVersion: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val url = URL(CruxLinks.LATEST_RELEASE_API)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Crux-Android-App")
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext UpdateCheckResult.Error("Server returned code ${connection.responseCode}")
            }

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseBody)

            val tagName = json.optString("tag_name", "")
            val name = json.optString("name", tagName)
            val body = json.optString("body", "No changelog provided.")
            val htmlUrl = json.optString("html_url", CruxLinks.RELEASES)

            val assets = json.optJSONArray("assets")
            var apkUrl: String? = null
            var apkSize: Long = 0L

            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val assetName = asset.optString("name", "")
                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = if (asset.has("browser_download_url")) asset.getString("browser_download_url") else null
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }

            val cleanVersion = tagName.trimStart('v', 'V')
            if (isNewerVersion(tagName, currentVersion)) {
                UpdateCheckResult.UpdateAvailable(
                    AppReleaseInfo(
                        tagName = tagName,
                        versionName = cleanVersion,
                        title = name,
                        changelog = body,
                        releaseUrl = htmlUrl,
                        apkUrl = apkUrl,
                        apkSizeBytes = apkSize,
                    )
                )
            } else {
                UpdateCheckResult.UpToDate
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.localizedMessage ?: "Unknown network error")
        }
    }

    public suspend fun downloadAndInstallApk(
        activity: Activity,
        apkUrl: String,
        onProgress: (Int) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Check unknown apps install permission on Android 8.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!activity.packageManager.canRequestPackageInstalls()) {
                    withContext(Dispatchers.Main) {
                        val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                            data = Uri.parse("package:${activity.packageName}")
                        }
                        activity.startActivity(settingsIntent)
                    }
                    return@withContext Result.failure(
                        SecurityException("Please grant permission to install updates, then try again.")
                    )
                }
            }

            val updatesDir = File(activity.cacheDir, "updates").apply { mkdirs() }
            val targetFile = File(updatesDir, "crux-update.apk")
            if (targetFile.exists()) {
                targetFile.delete()
            }

            val connection = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                setRequestProperty("User-Agent", "Crux-Android-App")
                instanceFollowRedirects = true
            }

            val contentLength = connection.contentLength
            var downloaded = 0L

            connection.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        if (contentLength > 0) {
                            val percent = ((downloaded * 100) / contentLength).toInt().coerceIn(0, 100)
                            withContext(Dispatchers.Main) {
                                onProgress(percent)
                            }
                        }
                    }
                }
            }

            withContext(Dispatchers.Main) {
                val contentUri = FileProvider.getUriForFile(
                    activity,
                    "${activity.packageName}.provider",
                    targetFile,
                )

                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(contentUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                activity.startActivity(installIntent)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
