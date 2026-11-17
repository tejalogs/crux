package org.crux.ui.screen.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CircleCheck
import compose.icons.fontawesomeicons.solid.CircleExclamation
import compose.icons.fontawesomeicons.solid.Database
import compose.icons.fontawesomeicons.solid.Trash
import kotlinx.coroutines.launch
import org.crux.R
import org.crux.common.LiteRtLmRuntime
import org.crux.common.LiteRtRuntimeStatus
import org.crux.common.OnDeviceModelManager
import org.crux.common.OnDeviceModelState
import org.crux.data.ai.OnDeviceModelSpec
import org.crux.di.koinInject

@Composable
internal fun OnDeviceModelsSection(
    activeModelId: String,
    onSelectModel: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val modelManager by koinInject<OnDeviceModelManager>()
    val liteRtLmRuntime by koinInject<LiteRtLmRuntime>()
    val coroutineScope = rememberCoroutineScope()

    val states by modelManager.states.collectAsState()
    val runtimeStatus by liteRtLmRuntime.status.collectAsState()

    var usedStorageBytes by remember { mutableLongStateOf(modelManager.totalStorageUsedBytes()) }
    var freeStorageBytes by remember { mutableLongStateOf(modelManager.availableStorageBytes()) }
    var pendingDeleteSpec by remember { mutableStateOf<OnDeviceModelSpec?>(null) }

    LaunchedEffect(states) {
        usedStorageBytes = modelManager.totalStorageUsedBytes()
        freeStorageBytes = modelManager.availableStorageBytes()
    }

    val hasAnyInstalled = states.values.any { it is OnDeviceModelState.Downloaded }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.on_device_models_section_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(id = R.string.on_device_models_section_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = FontAwesomeIcons.Solid.Database,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text =
                        stringResource(
                            id = R.string.on_device_models_storage_summary,
                            OnDeviceModelSpec.formatModelBytes(usedStorageBytes),
                            OnDeviceModelSpec.formatModelBytes(freeStorageBytes),
                        ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            RuntimeStatusBanner(
                runtimeStatus = runtimeStatus,
                hasAnyInstalled = hasAnyInstalled,
                onWarmupClick = { coroutineScope.launch { liteRtLmRuntime.preloadActiveModel() } },
                onUnloadClick = { coroutineScope.launch { liteRtLmRuntime.unloadAll() } },
            )

            OnDeviceModelSpec.SupportedModels.forEach { spec ->
                val state = states[spec.modelId] ?: OnDeviceModelState.NotDownloaded(spec)
                val isActive = spec.modelId.equals(activeModelId, ignoreCase = true)
                ModelSpecCard(
                    spec = spec,
                    state = state,
                    isActive = isActive,
                    onDownloadClick = {
                        val activeState = states[activeModelId]
                        if (activeState !is OnDeviceModelState.Downloaded) {
                            onSelectModel(spec.modelId)
                        }
                        modelManager.startDownload(spec)
                    },
                    onPauseClick = { modelManager.pauseDownload(spec.modelId) },
                    onUseClick = { onSelectModel(spec.modelId) },
                    onDeleteClick = { pendingDeleteSpec = spec },
                )
            }
        }
    }

    pendingDeleteSpec?.let { specToDelete ->
        val currentState = states[specToDelete.modelId]
        val sizeToFree =
            when (currentState) {
                is OnDeviceModelState.Downloaded -> currentState.sizeBytes
                is OnDeviceModelState.NotDownloaded -> currentState.partialBytes
                is OnDeviceModelState.Error -> currentState.partialBytes
                else -> specToDelete.downloadSizeBytes
            }.coerceAtLeast(0L)
        AlertDialog(
            onDismissRequest = { pendingDeleteSpec = null },
            title = {
                Text(text = stringResource(id = R.string.on_device_model_delete_title, specToDelete.displayName))
            },
            text = {
                Text(
                    text =
                        stringResource(
                            id = R.string.on_device_model_delete_message,
                            OnDeviceModelSpec.formatModelBytes(sizeToFree),
                        ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        modelManager.deleteModel(specToDelete.modelId)
                        pendingDeleteSpec = null
                    },
                ) {
                    Text(
                        text = stringResource(id = R.string.on_device_model_action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteSpec = null }) {
                    Text(text = stringResource(id = android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun RuntimeStatusBanner(
    runtimeStatus: LiteRtRuntimeStatus,
    hasAnyInstalled: Boolean,
    onWarmupClick: () -> Unit,
    onUnloadClick: () -> Unit,
) {
    val containerColor =
        when (runtimeStatus) {
            is LiteRtRuntimeStatus.Ready -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            is LiteRtRuntimeStatus.Loading -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
            is LiteRtRuntimeStatus.Failed -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
            is LiteRtRuntimeStatus.Unloaded -> MaterialTheme.colorScheme.surfaceContainerHigh
        }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                when (runtimeStatus) {
                    is LiteRtRuntimeStatus.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                        )
                    }

                    is LiteRtRuntimeStatus.Ready -> {
                        Icon(
                            imageVector = FontAwesomeIcons.Solid.CircleCheck,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }

                    is LiteRtRuntimeStatus.Failed -> {
                        Icon(
                            imageVector = FontAwesomeIcons.Solid.CircleExclamation,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }

                    is LiteRtRuntimeStatus.Unloaded -> Unit
                }

                val statusText =
                    when (runtimeStatus) {
                        is LiteRtRuntimeStatus.Ready -> {
                            stringResource(
                                id = R.string.on_device_runtime_ready,
                                runtimeStatus.modelSpec.displayName,
                                runtimeStatus.activeBackendLabel,
                                runtimeStatus.initTimeMs,
                            )
                        }

                        is LiteRtRuntimeStatus.Loading -> {
                            stringResource(
                                id = R.string.on_device_runtime_loading,
                                runtimeStatus.modelSpec.displayName,
                                runtimeStatus.backendLabel,
                            )
                        }

                        is LiteRtRuntimeStatus.Failed -> {
                            stringResource(id = R.string.on_device_runtime_failed, runtimeStatus.message)
                        }

                        is LiteRtRuntimeStatus.Unloaded -> {
                            if (hasAnyInstalled) {
                                stringResource(id = R.string.on_device_runtime_standby)
                            } else {
                                stringResource(id = R.string.on_device_runtime_fallback)
                            }
                        }
                    }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            if (runtimeStatus is LiteRtRuntimeStatus.Ready) {
                TextButton(
                    onClick = onUnloadClick,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = stringResource(id = R.string.on_device_model_unload_action),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            } else if (runtimeStatus is LiteRtRuntimeStatus.Unloaded && hasAnyInstalled) {
                TextButton(
                    onClick = onWarmupClick,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = stringResource(id = R.string.on_device_model_warmup_action),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelSpecCard(
    spec: OnDeviceModelSpec,
    state: OnDeviceModelState,
    isActive: Boolean,
    onDownloadClick: () -> Unit,
    onPauseClick: () -> Unit,
    onUseClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border =
            if (isActive) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
            } else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = spec.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "${spec.parameterCount} • ${spec.formattedDownloadSize()} • ${spec.licenseName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isActive) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = stringResource(id = R.string.on_device_model_badge_active),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            Text(
                text = spec.summaryDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(2.dp))

            when (state) {
                is OnDeviceModelState.NotDownloaded -> {
                    NotDownloadedFooter(
                        spec = spec,
                        partialBytes = state.partialBytes,
                        onDownloadClick = onDownloadClick,
                        onDiscardClick = onDeleteClick,
                    )
                }

                is OnDeviceModelState.Downloading -> {
                    DownloadingFooter(
                        state = state,
                        onPauseClick = onPauseClick,
                    )
                }

                is OnDeviceModelState.Verifying -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = stringResource(id = R.string.on_device_model_verifying),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                is OnDeviceModelState.Downloaded -> {
                    DownloadedFooter(
                        sizeBytes = state.sizeBytes,
                        isActive = isActive,
                        onUseClick = onUseClick,
                        onDeleteClick = onDeleteClick,
                    )
                }

                is OnDeviceModelState.Error -> {
                    ErrorFooter(
                        state = state,
                        onRetryClick = onDownloadClick,
                        onDiscardClick = onDeleteClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun NotDownloadedFooter(
    spec: OnDeviceModelSpec,
    partialBytes: Long,
    onDownloadClick: () -> Unit,
    onDiscardClick: () -> Unit,
) {
    if (partialBytes > 0L) {
        val ratio =
            if (spec.downloadSizeBytes > 0L) {
                (partialBytes.toFloat() / spec.downloadSizeBytes.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
        LinearProgressIndicator(
            progress = { ratio },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text =
                stringResource(
                    id = R.string.on_device_model_paused_progress,
                    OnDeviceModelSpec.formatModelBytes(partialBytes),
                    spec.formattedDownloadSize(),
                ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalButton(
            onClick = onDownloadClick,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(
                text =
                    if (partialBytes > 0L) {
                        stringResource(id = R.string.on_device_model_action_resume)
                    } else {
                        stringResource(
                            id = R.string.on_device_model_action_download,
                            spec.formattedDownloadSize(),
                        )
                    },
            )
        }
        if (partialBytes > 0L) {
            TextButton(onClick = onDiscardClick) {
                Text(text = stringResource(id = R.string.on_device_model_action_discard_partial))
            }
        }
    }
}

@Composable
private fun DownloadingFooter(
    state: OnDeviceModelState.Downloading,
    onPauseClick: () -> Unit,
) {
    if (state.totalBytes > 0L) {
        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val percent = (state.progress * 100f).toInt().coerceIn(0, 100)
        val details =
            buildString {
                if (state.totalBytes > 0L) {
                    append("$percent% • ")
                }
                append(OnDeviceModelSpec.formatModelBytes(state.downloadedBytes))
                if (state.totalBytes > 0L) {
                    append(" / ")
                    append(OnDeviceModelSpec.formatModelBytes(state.totalBytes))
                }
                if (state.speedBytesPerSec > 0L) {
                    append(" • ")
                    append(OnDeviceModelSpec.formatModelBytes(state.speedBytesPerSec))
                    append("/s")
                }
            }
        Text(
            text = details,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedButton(
            onClick = onPauseClick,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text(text = stringResource(id = R.string.on_device_model_action_pause))
        }
    }
}

@Composable
private fun DownloadedFooter(
    sizeBytes: Long,
    isActive: Boolean,
    onUseClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = FontAwesomeIcons.Solid.CircleCheck,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text =
                    "${stringResource(id = R.string.on_device_model_badge_installed)} • " +
                        OnDeviceModelSpec.formatModelBytes(sizeBytes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (!isActive) {
                FilledTonalButton(
                    onClick = onUseClick,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(text = stringResource(id = R.string.on_device_model_action_use))
                }
            }
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = FontAwesomeIcons.Solid.Trash,
                    contentDescription = stringResource(id = R.string.on_device_model_action_delete),
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ErrorFooter(
    state: OnDeviceModelState.Error,
    onRetryClick: () -> Unit,
    onDiscardClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = state.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(
                onClick = onRetryClick,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    text =
                        if (state.partialBytes > 0L) {
                            stringResource(id = R.string.on_device_model_action_resume)
                        } else {
                            stringResource(id = R.string.on_device_model_action_retry)
                        },
                )
            }
            if (state.partialBytes > 0L) {
                TextButton(onClick = onDiscardClick) {
                    Text(text = stringResource(id = R.string.on_device_model_action_discard_partial))
                }
            }
        }
    }
}
