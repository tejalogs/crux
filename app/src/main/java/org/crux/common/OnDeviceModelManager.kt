package org.crux.common

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.crux.MainActivity
import org.crux.R
import org.crux.data.ai.OnDeviceModelSpec
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal sealed interface OnDeviceModelState {
    val spec: OnDeviceModelSpec

    data class NotDownloaded(
        override val spec: OnDeviceModelSpec,
        val partialBytes: Long = 0L,
    ) : OnDeviceModelState

    data class Downloading(
        override val spec: OnDeviceModelSpec,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Long = 0L,
    ) : OnDeviceModelState {
        val progress: Float
            get() =
                if (totalBytes > 0L) {
                    (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
    }

    data class Verifying(
        override val spec: OnDeviceModelSpec,
    ) : OnDeviceModelState

    data class Downloaded(
        override val spec: OnDeviceModelSpec,
        val file: File,
        val sizeBytes: Long,
    ) : OnDeviceModelState

    data class Error(
        override val spec: OnDeviceModelSpec,
        val message: String,
        val partialBytes: Long = 0L,
    ) : OnDeviceModelState
}

internal class OnDeviceModelStore(
    internal val modelsDir: File,
    private val minValidModelBytes: Long = DEFAULT_MIN_VALID_MODEL_BYTES,
) {
    fun ensureDir(): Boolean = modelsDir.exists() || modelsDir.mkdirs()

    fun finalFile(spec: OnDeviceModelSpec): File = File(modelsDir, spec.fileName)

    fun partFile(spec: OnDeviceModelSpec): File = File(modelsDir, "${spec.fileName}.part")

    fun installedModelFile(spec: OnDeviceModelSpec): File? {
        val file = finalFile(spec)
        if (!file.exists() || !file.isFile) return null
        if (file.length() < minValidModelBytes) return null
        if (!hasLiteRtLmHeader(file)) return null
        return file
    }

    fun partialBytes(spec: OnDeviceModelSpec): Long =
        partFile(spec).takeIf { it.exists() && it.isFile }?.length() ?: 0L

    fun inspectIdleState(spec: OnDeviceModelSpec): OnDeviceModelState {
        val installed = installedModelFile(spec)
        return if (installed != null) {
            OnDeviceModelState.Downloaded(
                spec = spec,
                file = installed,
                sizeBytes = installed.length(),
            )
        } else {
            OnDeviceModelState.NotDownloaded(
                spec = spec,
                partialBytes = partialBytes(spec),
            )
        }
    }

    fun finalizePartFile(
        spec: OnDeviceModelSpec,
        expectedTotalBytes: Long,
    ): File {
        val part = partFile(spec)
        if (!part.exists() || !part.isFile) {
            throw IOException("Downloaded file missing before verification.")
        }
        val actualLength = part.length()
        if (expectedTotalBytes > 0L && actualLength != expectedTotalBytes) {
            throw IOException(
                "Incomplete download: received ${OnDeviceModelSpec.formatModelBytes(actualLength)} of " +
                    "${OnDeviceModelSpec.formatModelBytes(expectedTotalBytes)}.",
            )
        }
        if (actualLength < minValidModelBytes) {
            throw IOException("Downloaded model file is unexpectedly small ($actualLength bytes).")
        }
        if (!hasLiteRtLmHeader(part)) {
            part.delete()
            throw IOException("Downloaded file is not a valid .litertlm container.")
        }
        val expectedHash = spec.sha256.trim().lowercase()
        if (expectedHash.isNotEmpty()) {
            val actualHash = sha256Hex(part)
            if (!actualHash.equals(expectedHash, ignoreCase = true)) {
                part.delete()
                throw IOException("Downloaded model failed SHA-256 integrity verification.")
            }
        }
        val dest = finalFile(spec)
        if (dest.exists() && !dest.delete()) {
            throw IOException("Could not replace previous model file.")
        }
        if (!part.renameTo(dest)) {
            part.copyTo(dest, overwrite = true)
            part.delete()
        }
        return dest
    }

    fun deleteModelFiles(spec: OnDeviceModelSpec) {
        finalFile(spec).delete()
        partFile(spec).delete()
    }

    fun totalStorageUsedBytes(): Long =
        modelsDir
            .listFiles()
            ?.filter { it.isFile }
            ?.sumOf { it.length() }
            ?: 0L

    fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        FileInputStream(file).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun hasLiteRtLmHeader(file: File): Boolean {
        if (!file.exists() || !file.isFile || file.length() < LITERTLM_MAGIC.size) return false
        return runCatching {
            val header = ByteArray(LITERTLM_MAGIC.size)
            FileInputStream(file).use { input ->
                var offset = 0
                while (offset < header.size) {
                    val read = input.read(header, offset, header.size - offset)
                    if (read <= 0) break
                    offset += read
                }
                offset == header.size && header.contentEquals(LITERTLM_MAGIC)
            }
        }.getOrDefault(false)
    }

    companion object {
        const val DEFAULT_MIN_VALID_MODEL_BYTES: Long = 1024L * 1024L
        private val LITERTLM_MAGIC: ByteArray = "LITERTLM".encodeToByteArray()
    }
}

internal object ModelDownloadNetwork {
    private val TRUSTED_HOST_SUFFIXES =
        listOf(
            "huggingface.co",
            "hf.co",
            "githubusercontent.com",
            "cloudfront.net",
            "amazonaws.com",
            "r2.cloudflarestorage.com",
            "googleapis.com",
        )

    private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    private const val MAX_REDIRECTS = 8
    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 30_000

    fun isTrustedModelUrl(url: String?): Boolean {
        val parsed = url?.let { runCatching { URL(it) }.getOrNull() } ?: return false
        if (!parsed.protocol.equals("https", ignoreCase = true)) return false
        val host = parsed.host.lowercase()
        return TRUSTED_HOST_SUFFIXES.any { suffix ->
            host == suffix || host.endsWith(".$suffix")
        }
    }

    fun parseContentRangeStartAndTotal(headerValue: String?): Pair<Long, Long>? {
        if (headerValue.isNullOrBlank()) return null
        val match = Regex("""bytes\s+(\d+)-(\d+)/(\d+|\*)""", RegexOption.IGNORE_CASE).find(headerValue.trim())
            ?: return null
        val start = match.groupValues[1].toLongOrNull() ?: return null
        val total = match.groupValues[3].toLongOrNull() ?: -1L
        return start to total
    }

    data class OpenedDownload(
        val connection: HttpURLConnection,
        val resumeOffsetBytes: Long,
        val expectedTotalBytes: Long,
        val alreadyCompleteOnServer: Boolean = false,
    )

    fun openDownload(
        initialUrl: String,
        requestedResumeBytes: Long,
        fallbackExpectedBytes: Long,
    ): OpenedDownload {
        var currentUrl = initialUrl
        var redirectsRemaining = MAX_REDIRECTS
        while (true) {
            if (!isTrustedModelUrl(currentUrl)) {
                throw SecurityException("Untrusted model host in download URL.")
            }
            val connection =
                (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = false
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    setRequestProperty("User-Agent", "Crux-Android-App")
                    if (requestedResumeBytes > 0L) {
                        setRequestProperty("Range", "bytes=$requestedResumeBytes-")
                    }
                }
            val code = connection.responseCode
            if (code in REDIRECT_CODES) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank() || redirectsRemaining <= 0) {
                    throw IOException("Model server redirect failed (HTTP $code).")
                }
                currentUrl = URL(URL(currentUrl), location).toString()
                redirectsRemaining--
                continue
            }

            return when (code) {
                HttpURLConnection.HTTP_PARTIAL -> {
                    val parsedRange = parseContentRangeStartAndTotal(connection.getHeaderField("Content-Range"))
                    val rangeStart = parsedRange?.first ?: requestedResumeBytes
                    val totalFromRange = parsedRange?.second?.takeIf { it > 0L }
                    val contentLength = connection.contentLengthLong.takeIf { it > 0L }
                    if (rangeStart != requestedResumeBytes) {
                        connection.disconnect()
                        // Server did not honor the exact offset; restart cleanly without Range.
                        if (requestedResumeBytes > 0L) {
                            return openDownload(initialUrl, 0L, fallbackExpectedBytes)
                        }
                        throw IOException("Unexpected Content-Range start: $rangeStart")
                    }
                    val totalBytes =
                        totalFromRange
                            ?: contentLength?.let { requestedResumeBytes + it }
                            ?: fallbackExpectedBytes
                    OpenedDownload(
                        connection = connection,
                        resumeOffsetBytes = requestedResumeBytes,
                        expectedTotalBytes = totalBytes,
                    )
                }

                HttpURLConnection.HTTP_OK -> {
                    val contentLength = connection.contentLengthLong.takeIf { it > 0L }
                    OpenedDownload(
                        connection = connection,
                        resumeOffsetBytes = 0L,
                        expectedTotalBytes = contentLength ?: fallbackExpectedBytes,
                    )
                }

                416 -> {
                    val parsedRange = parseContentRangeStartAndTotal(connection.getHeaderField("Content-Range"))
                    connection.disconnect()
                    val serverTotal =
                        Regex("""bytes\s+\*/(\d+)""", RegexOption.IGNORE_CASE)
                            .find(connection.getHeaderField("Content-Range").orEmpty())
                            ?.groupValues
                            ?.getOrNull(1)
                            ?.toLongOrNull()
                            ?: parsedRange?.second?.takeIf { it > 0L }
                            ?: fallbackExpectedBytes
                    if (requestedResumeBytes > 0L && serverTotal > 0L && requestedResumeBytes == serverTotal) {
                        OpenedDownload(
                            connection = connection,
                            resumeOffsetBytes = requestedResumeBytes,
                            expectedTotalBytes = serverTotal,
                            alreadyCompleteOnServer = true,
                        )
                    } else {
                        throw IOException("Download range mismatch (HTTP 416).")
                    }
                }

                else -> {
                    connection.disconnect()
                    throw IOException("Server returned HTTP $code while downloading model.")
                }
            }
        }
    }
}

@Single
internal class OnDeviceModelManager(
    @Provided private val context: Context,
) {
    private val store = OnDeviceModelStore(File(context.filesDir, MODELS_DIR_NAME))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobLock = Any()
    private val activeJobs = mutableMapOf<String, Job>()

    @Volatile
    private var beforeDeleteHook: (suspend (modelId: String) -> Unit)? = null

    private val _states =
        MutableStateFlow(
            OnDeviceModelSpec.SupportedModels.associate { spec ->
                spec.modelId to store.inspectIdleState(spec)
            },
        )
    val states: StateFlow<Map<String, OnDeviceModelState>> = _states.asStateFlow()

    fun registerBeforeDeleteHook(hook: suspend (modelId: String) -> Unit) {
        beforeDeleteHook = hook
    }

    fun installedModelFile(spec: OnDeviceModelSpec): File? = store.installedModelFile(spec)

    fun firstInstalledModel(): Pair<OnDeviceModelSpec, File>? =
        OnDeviceModelSpec.SupportedModels.firstNotNullOfOrNull { spec ->
            store.installedModelFile(spec)?.let { spec to it }
        }

    fun hasAnyInstalledModel(): Boolean = firstInstalledModel() != null

    fun totalStorageUsedBytes(): Long = store.totalStorageUsedBytes()

    fun availableStorageBytes(): Long {
        store.ensureDir()
        return store.modelsDir.usableSpace
    }

    fun startDownload(spec: OnDeviceModelSpec) {
        synchronized(jobLock) {
            if (activeJobs[spec.modelId]?.isActive == true) {
                return
            }
            val job =
                scope.launch {
                    runDownload(spec)
                }
            activeJobs[spec.modelId] = job
            job.invokeOnCompletion {
                synchronized(jobLock) {
                    if (activeJobs[spec.modelId] === job) {
                        activeJobs.remove(spec.modelId)
                    }
                }
            }
        }
    }

    fun pauseDownload(modelId: String) {
        val spec = OnDeviceModelSpec.findById(modelId)
        scope.launch {
            val active =
                synchronized(jobLock) {
                    activeJobs.remove(modelId)
                }
            active?.cancelAndJoin()
            cancelNotification(spec)
            refreshIdleState(spec)
        }
    }

    fun deleteModel(modelId: String) {
        val spec = OnDeviceModelSpec.findById(modelId)
        scope.launch {
            val active =
                synchronized(jobLock) {
                    activeJobs.remove(modelId)
                }
            active?.cancelAndJoin()
            runCatching { beforeDeleteHook?.invoke(spec.modelId) }
            store.deleteModelFiles(spec)
            cancelNotification(spec)
            refreshIdleState(spec)
        }
    }

    fun refreshAll() {
        _states.update { current ->
            val next = current.toMutableMap()
            for (spec in OnDeviceModelSpec.SupportedModels) {
                val active =
                    synchronized(jobLock) {
                        activeJobs[spec.modelId]?.isActive == true
                    }
                if (!active) {
                    next[spec.modelId] = store.inspectIdleState(spec)
                }
            }
            next
        }
    }

    private fun refreshIdleState(spec: OnDeviceModelSpec) {
        updateState(spec.modelId, store.inspectIdleState(spec))
    }

    private fun updateState(
        modelId: String,
        newState: OnDeviceModelState,
    ) {
        _states.update { it + (modelId to newState) }
    }

    private suspend fun runDownload(spec: OnDeviceModelSpec) {
        try {
            if (!store.ensureDir()) {
                throw IOException("Cannot create model storage directory.")
            }
            store.installedModelFile(spec)?.let { file ->
                updateState(spec.modelId, OnDeviceModelState.Downloaded(spec, file, file.length()))
                return
            }

            var existingPartBytes = store.partialBytes(spec)
            val partFile = store.partFile(spec)
            if (spec.downloadSizeBytes > 0L && existingPartBytes > spec.downloadSizeBytes) {
                partFile.delete()
                existingPartBytes = 0L
            }

            if (spec.downloadSizeBytes > 0L) {
                val remaining = (spec.downloadSizeBytes - existingPartBytes).coerceAtLeast(0L)
                val required = remaining + STORAGE_HEADROOM_BYTES
                val free = store.modelsDir.usableSpace
                if (free < required) {
                    throw IOException(
                        "Not enough free storage (${OnDeviceModelSpec.formatModelBytes(free)} free, " +
                            "needs ${OnDeviceModelSpec.formatModelBytes(required)}).",
                    )
                }
            }

            updateState(
                spec.modelId,
                OnDeviceModelState.Downloading(
                    spec = spec,
                    downloadedBytes = existingPartBytes,
                    totalBytes = spec.downloadSizeBytes,
                ),
            )
            postProgressNotification(spec, existingPartBytes, spec.downloadSizeBytes, 0L)

            val opened =
                ModelDownloadNetwork.openDownload(
                    initialUrl = spec.downloadUrl,
                    requestedResumeBytes = existingPartBytes,
                    fallbackExpectedBytes = spec.downloadSizeBytes,
                )

            val expectedTotal = opened.expectedTotalBytes
            if (!opened.alreadyCompleteOnServer) {
                val append = opened.resumeOffsetBytes > 0L
                var downloaded = if (append) opened.resumeOffsetBytes else 0L
                if (!append && partFile.exists()) {
                    partFile.delete()
                }
                try {
                    val buffer = ByteArray(STREAM_BUFFER_BYTES)
                    var lastUiEmitMs = 0L
                    var lastNotifEmitMs = 0L
                    var windowStartMs = System.currentTimeMillis()
                    var windowBytes = 0L
                    var currentSpeed = 0L

                    opened.connection.inputStream.use { input ->
                        FileOutputStream(partFile, append).use { output ->
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val bytesRead = input.read(buffer)
                                if (bytesRead < 0) break
                                if (bytesRead == 0) continue

                                output.write(buffer, 0, bytesRead)
                                downloaded += bytesRead
                                windowBytes += bytesRead

                                val nowMs = System.currentTimeMillis()
                                val windowElapsedMs = nowMs - windowStartMs
                                if (windowElapsedMs >= 1000L) {
                                    currentSpeed = (windowBytes * 1000L) / windowElapsedMs.coerceAtLeast(1L)
                                    windowStartMs = nowMs
                                    windowBytes = 0L
                                }

                                if (nowMs - lastUiEmitMs >= UI_THROTTLE_MS) {
                                    lastUiEmitMs = nowMs
                                    updateState(
                                        spec.modelId,
                                        OnDeviceModelState.Downloading(
                                            spec = spec,
                                            downloadedBytes = downloaded,
                                            totalBytes = expectedTotal,
                                            speedBytesPerSec = currentSpeed,
                                        ),
                                    )
                                }
                                if (nowMs - lastNotifEmitMs >= NOTIFICATION_THROTTLE_MS) {
                                    lastNotifEmitMs = nowMs
                                    postProgressNotification(spec, downloaded, expectedTotal, currentSpeed)
                                }
                            }
                            output.flush()
                        }
                    }
                } finally {
                    opened.connection.disconnect()
                }
            }

            currentCoroutineContext().ensureActive()
            updateState(spec.modelId, OnDeviceModelState.Verifying(spec))
            val installedFile = store.finalizePartFile(spec, expectedTotal)
            updateState(
                spec.modelId,
                OnDeviceModelState.Downloaded(
                    spec = spec,
                    file = installedFile,
                    sizeBytes = installedFile.length(),
                ),
            )
            postCompletionNotification(spec)
        } catch (ce: CancellationException) {
            cancelNotification(spec)
            refreshIdleState(spec)
            throw ce
        } catch (t: Throwable) {
            Log.w(TAG, "Model download failed for ${spec.modelId}", t)
            cancelNotification(spec)
            updateState(
                spec.modelId,
                OnDeviceModelState.Error(
                    spec = spec,
                    message = t.localizedMessage?.takeIf { it.isNotBlank() } ?: "Download failed.",
                    partialBytes = store.partialBytes(spec),
                ),
            )
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    context.getString(R.string.on_device_model_notif_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.on_device_model_notif_channel_desc)
                }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    private fun postProgressNotification(
        spec: OnDeviceModelSpec,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBytesPerSec: Long,
    ) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureNotificationChannel()
        val percent =
            if (totalBytes > 0L) {
                ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
            } else {
                0
            }
        val progressText =
            buildString {
                append(OnDeviceModelSpec.formatModelBytes(downloadedBytes))
                if (totalBytes > 0L) {
                    append(" / ")
                    append(OnDeviceModelSpec.formatModelBytes(totalBytes))
                }
                if (speedBytesPerSec > 0L) {
                    append(" • ")
                    append(OnDeviceModelSpec.formatModelBytes(speedBytesPerSec))
                    append("/s")
                }
            }
        val builder =
            NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(context.getString(R.string.on_device_model_notif_downloading, spec.displayName))
                .setContentText(progressText)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setProgress(100, percent, totalBytes <= 0L)
                .setContentIntent(launchMainPendingIntent(spec.modelId.hashCode()))
        runCatching { manager.notify(notificationId(spec), builder.build()) }
    }

    private fun postCompletionNotification(spec: OnDeviceModelSpec) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureNotificationChannel()
        val builder =
            NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(context.getString(R.string.on_device_model_notif_ready_title, spec.displayName))
                .setContentText(context.getString(R.string.on_device_model_notif_ready_desc))
                .setAutoCancel(true)
                .setOngoing(false)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(launchMainPendingIntent(spec.modelId.hashCode()))
        runCatching { manager.notify(notificationId(spec), builder.build()) }
    }

    private fun cancelNotification(spec: OnDeviceModelSpec) {
        runCatching { NotificationManagerCompat.from(context).cancel(notificationId(spec)) }
    }

    private fun notificationId(spec: OnDeviceModelSpec): Int =
        NOTIFICATION_BASE_ID + (spec.modelId.hashCode() and 0x0FFF)

    private fun launchMainPendingIntent(requestCode: Int): PendingIntent {
        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val TAG = "OnDeviceModelManager"
        private const val MODELS_DIR_NAME = "litertlm_models"
        private const val NOTIFICATION_CHANNEL_ID = "crux_on_device_models"
        private const val NOTIFICATION_BASE_ID = 2200
        private const val STREAM_BUFFER_BYTES = 64 * 1024
        private const val UI_THROTTLE_MS = 250L
        private const val NOTIFICATION_THROTTLE_MS = 750L
        private const val STORAGE_HEADROOM_BYTES = 128L * 1024L * 1024L
    }
}
