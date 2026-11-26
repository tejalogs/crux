package org.crux.ui.screen.settings

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Download
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.crux.BuildConfig
import org.crux.R
import org.crux.update.AppReleaseInfo
import org.crux.update.AppUpdateManager
import org.crux.update.AppUpdateNotificationManager
import org.crux.update.AppUpdatePrefs
import org.crux.update.UpdateCheckResult
import org.crux.update.shouldPromptRelease

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppUpdateBottomSheet(
    updateInfo: AppReleaseInfo,
    onDismissRequest: () -> Unit,
    onSkipVersion: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    // One inline note at a time: a failure in error colour, a deliberate Cancel in calm text.
    var note by remember { mutableStateOf<UpdateSheetNote?>(null) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    // A cancelled download can still be inside blocking file work on the same APK path, so a new
    // download waits for it to finish instead of racing it.
    var cancelledJob by remember { mutableStateOf<Job?>(null) }
    // The download runs in this sheet's scope, so closing the sheet would cancel it with no word.
    // While it runs, swipe, scrim tap and Back are refused; Cancel is the explicit way out.
    val sheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { target -> canDismissUpdateSheet(target == SheetValue.Hidden, isDownloading) },
        )
    val cancelledMessage = stringResource(R.string.settings_update_download_cancelled)
    val notificationsGranted =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    // Crux only checks for updates while it is open and auto-check is on, so the permission is
    // offered only then; with auto-check off no notification would ever come.
    val updatePrefs = remember(context) { AppUpdatePrefs.get(context) }
    val autoCheckEnabled by updatePrefs.autoCheckEnabled.collectAsState()
    // Asked here, once an update exists, instead of the moment Crux opens.
    var notifyMeAnswered by remember { mutableStateOf(false) }
    val notificationLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Granted or not, the choice is made; don't keep offering it in this sheet.
            notifyMeAnswered = true
        }

    ModalBottomSheet(
        onDismissRequest = {
            if (canDismissUpdateSheet(hiding = true, isDownloading = isDownloading)) onDismissRequest()
        },
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(56.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(16.dp),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = FontAwesomeIcons.Solid.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.settings_update_available, updateInfo.tagName),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(12.dp))

            // Changelog Box
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
            ) {
                Text(
                    text = updateInfo.changelog,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            note?.let { current ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text =
                        when (current) {
                            UpdateSheetNote.Cancelled -> cancelledMessage
                            is UpdateSheetNote.Failed -> current.message ?: UPDATE_FAILED_FALLBACK_MESSAGE
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (current.isError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }

            if (isDownloading) {
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { downloadProgress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.settings_update_downloading, downloadProgress),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            downloadJob?.let { job ->
                                job.cancel()
                                cancelledJob = job
                            }
                            downloadJob = null
                            isDownloading = false
                            downloadProgress = 0
                            // Shown once, inline: the sheet stays open, so a Toast would only repeat it.
                            note = UpdateSheetNote.Cancelled
                        },
                    ) {
                        Text(text = stringResource(R.string.settings_update_cancel_download))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { uriHandler.openUri(updateInfo.releaseUrl) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) {
                    Text(
                        text = stringResource(R.string.settings_update_view_release),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (updateInfo.apkUrl != null) {
                    Button(
                        onClick = {
                            val activity = context as? Activity
                            if (activity != null) {
                                isDownloading = true
                                downloadProgress = 0
                                note = null
                                val previous = cancelledJob
                                downloadJob =
                                    coroutineScope.launch {
                                        previous?.join()
                                        if (cancelledJob === previous) cancelledJob = null
                                        val result =
                                            AppUpdateManager.downloadAndInstallApk(
                                                activity = activity,
                                                apkUrl = updateInfo.apkUrl,
                                                expectedSizeBytes = updateInfo.apkSizeBytes,
                                                onProgress = { progress -> downloadProgress = progress },
                                            )
                                        // A cancelled download never gets here: the job stops at its next suspension.
                                        ensureActive()
                                        isDownloading = false
                                        downloadJob = null
                                        result.onFailure { error ->
                                            val failure = UpdateSheetNote.Failed(error.localizedMessage)
                                            note = failure
                                            Toast
                                                .makeText(context, failure.message ?: UPDATE_FAILED_FALLBACK_MESSAGE, Toast.LENGTH_LONG)
                                                .show()
                                        }
                                    }
                            }
                        },
                        enabled = !isDownloading,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_update_download_install),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (showNotifyMeButton(notificationsGranted, notifyMeAnswered, autoCheckEnabled, isDownloading)) {
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            notifyMeAnswered = true
                        }
                    },
                ) {
                    Text(text = stringResource(R.string.settings_update_notify_me))
                }
            }

            if (onSkipVersion != null && !isDownloading) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = onSkipVersion) {
                        Text(text = stringResource(R.string.settings_update_skip_version))
                    }
                    TextButton(onClick = onDismissRequest) {
                        Text(text = stringResource(R.string.settings_update_later))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AutoAppUpdatePrompt() {
    val context = LocalContext.current
    val prefs = remember(context) { AppUpdatePrefs.get(context) }
    val autoCheckEnabled by prefs.autoCheckEnabled.collectAsState()
    var updateInfo by remember { mutableStateOf<AppReleaseInfo?>(null) }

    LaunchedEffect(Unit) {
        AppUpdateManager.pendingPromptUpdate?.let { pending ->
            updateInfo = pending
            AppUpdateManager.pendingPromptUpdate = null
        }
    }

    LaunchedEffect(autoCheckEnabled) {
        // Checks silently. Notification permission is only offered from the sheet, once an update exists.
        if (!autoCheckEnabled) return@LaunchedEffect
        delay(1500L)
        if (!prefs.tryStartSessionCheck()) return@LaunchedEffect
        when (val result = AppUpdateManager.checkForUpdates(BuildConfig.VERSION_NAME)) {
            is UpdateCheckResult.UpToDate -> prefs.recordCheckCompleted()
            is UpdateCheckResult.UpdateAvailable -> {
                prefs.recordCheckCompleted()
                if (shouldPromptRelease(result.info.tagName, prefs.skippedTag(), BuildConfig.VERSION_NAME)) {
                    updateInfo = result.info
                    AppUpdateNotificationManager.showUpdateNotification(context, result.info)
                }
            }

            is UpdateCheckResult.Error -> Unit
        }
    }

    updateInfo?.let { current ->
        AppUpdateBottomSheet(
            updateInfo = current,
            onDismissRequest = {
                AppUpdateNotificationManager.cancelUpdateNotification(context)
                updateInfo = null
            },
            onSkipVersion = {
                prefs.skipVersion(current.tagName)
                AppUpdateNotificationManager.cancelUpdateNotification(context)
                updateInfo = null
            },
        )
    }
}

/**
 * Whether the update sheet may close. While the APK downloads, closing would cancel it silently,
 * so only the explicit Cancel button stops it.
 */
internal fun canDismissUpdateSheet(
    hiding: Boolean,
    isDownloading: Boolean,
): Boolean = !hiding || !isDownloading

/** Shown when a failed download carries no message of its own (unchanged from before this sheet had notes). */
private const val UPDATE_FAILED_FALLBACK_MESSAGE = "Failed to install update"

/** The one inline note under the changelog. Only a failure uses error colour; Cancel was the user's choice. */
internal sealed interface UpdateSheetNote {
    val isError: Boolean

    data object Cancelled : UpdateSheetNote {
        override val isError: Boolean = false
    }

    /** [message] is the failure's own text when it has one; the sheet falls back to a generic line. */
    data class Failed(
        val message: String?,
    ) : UpdateSheetNote {
        override val isError: Boolean = true
    }
}

/**
 * Whether to offer the notification permission. Crux checks for updates only while it is open and
 * auto-check is on, so with auto-check off the permission would never bring a notification.
 */
internal fun showNotifyMeButton(
    notificationsGranted: Boolean,
    alreadyAnswered: Boolean,
    autoCheckEnabled: Boolean,
    isDownloading: Boolean,
): Boolean = !notificationsGranted && !alreadyAnswered && autoCheckEnabled && !isDownloading
