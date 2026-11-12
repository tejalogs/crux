package org.crux.ui.screen.settings

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.SheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Download
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.crux.BuildConfig
import org.crux.R
import org.crux.update.AppReleaseInfo
import org.crux.update.AppUpdateManager
import org.crux.update.AppUpdatePrefs
import org.crux.update.UpdateCheckResult
import org.crux.update.shouldPromptRelease

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppUpdateBottomSheet(
    sheetState: SheetState,
    updateInfo: AppReleaseInfo,
    onDismissRequest: () -> Unit,
    onSkipVersion: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
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
                        .heightIn(max = 240.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp),
                        ).padding(16.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = updateInfo.changelog,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (errorMessage != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (isDownloading) {
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { downloadProgress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_update_downloading, downloadProgress),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { uriHandler.openUri(updateInfo.releaseUrl) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.settings_update_view_release))
                }

                if (updateInfo.apkUrl != null) {
                    Button(
                        onClick = {
                            val activity = context as? Activity
                            if (activity != null) {
                                isDownloading = true
                                errorMessage = null
                                coroutineScope.launch {
                                    val result =
                                        AppUpdateManager.downloadAndInstallApk(
                                            activity = activity,
                                            apkUrl = updateInfo.apkUrl,
                                            onProgress = { progress -> downloadProgress = progress },
                                        )
                                    isDownloading = false
                                    result.onFailure { error ->
                                        errorMessage = error.localizedMessage ?: "Failed to install update"
                                        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        enabled = !isDownloading,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = stringResource(R.string.settings_update_download_install))
                    }
                }
                if (onSkipVersion != null && !isDownloading) {
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AutoAppUpdatePrompt() {
    val context = LocalContext.current
    val prefs = remember(context) { AppUpdatePrefs.get(context) }
    val autoCheckEnabled by prefs.autoCheckEnabled.collectAsState()
    var updateInfo by remember { mutableStateOf<AppReleaseInfo?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(autoCheckEnabled) {
        if (!autoCheckEnabled) return@LaunchedEffect
        delay(1500L)
        if (!prefs.tryStartSessionCheck()) return@LaunchedEffect
        when (val result = AppUpdateManager.checkForUpdates(BuildConfig.VERSION_NAME)) {
            is UpdateCheckResult.UpToDate -> prefs.recordCheckCompleted()
            is UpdateCheckResult.UpdateAvailable -> {
                prefs.recordCheckCompleted()
                if (shouldPromptRelease(result.info.tagName, prefs.skippedTag(), BuildConfig.VERSION_NAME)) {
                    updateInfo = result.info
                }
            }

            is UpdateCheckResult.Error -> Unit
        }
    }

    updateInfo?.let { current ->
        AppUpdateBottomSheet(
            sheetState = sheetState,
            updateInfo = current,
            onDismissRequest = { updateInfo = null },
            onSkipVersion = {
                prefs.skipVersion(current.tagName)
                updateInfo = null
            },
        )
    }
}
