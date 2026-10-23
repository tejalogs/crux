package dev.dimension.flare.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.PlatformContext
import coil3.SingletonImageLoader
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.AngleRight
import compose.icons.fontawesomeicons.solid.Robot
import dev.dimension.flare.Res
import dev.dimension.flare.action_export
import dev.dimension.flare.action_import
import dev.dimension.flare.cancel
import dev.dimension.flare.ui.component.ComposeInAppNotification
import dev.dimension.flare.delete
import dev.dimension.flare.import_completed
import dev.dimension.flare.import_confirmation_message
import dev.dimension.flare.import_confirmation_title
import dev.dimension.flare.import_error
import dev.dimension.flare.ok
import dev.dimension.flare.save_completed
import dev.dimension.flare.save_error
import dev.dimension.flare.settings_agent_history_description
import dev.dimension.flare.settings_agent_history_title
import dev.dimension.flare.settings_draft_box_description
import dev.dimension.flare.settings_draft_box_title
import dev.dimension.flare.settings_local_history_description
import dev.dimension.flare.settings_local_history_title
import dev.dimension.flare.settings_rss_management_description
import dev.dimension.flare.settings_rss_management_title
import dev.dimension.flare.settings_storage_app_log
import dev.dimension.flare.settings_storage_app_log_description
import dev.dimension.flare.settings_storage_clear_database
import dev.dimension.flare.settings_storage_clear_database_description
import dev.dimension.flare.settings_storage_clear_image_cache
import dev.dimension.flare.settings_storage_clear_image_cache_description
import dev.dimension.flare.settings_storage_export_data
import dev.dimension.flare.settings_storage_export_data_description
import dev.dimension.flare.settings_storage_import_data
import dev.dimension.flare.settings_storage_import_data_description
import dev.dimension.flare.settings_storage_subtitle
import dev.dimension.flare.settings_storage_title
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.Header
import dev.dimension.flare.ui.presenter.ExportDataPresenter
import dev.dimension.flare.ui.presenter.ImportDataPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.StoragePresenter
import dev.dimension.flare.ui.presenter.settings.StorageState
import io.github.composefluent.ExperimentalFluentApi
import io.github.composefluent.component.Button
import io.github.composefluent.component.CardExpanderItem
import io.github.composefluent.component.ContentDialog
import io.github.composefluent.component.ContentDialogButton
import io.github.composefluent.component.Expander
import io.github.composefluent.component.ExpanderItem
import io.github.composefluent.component.ExpanderItemSeparator
import io.github.composefluent.component.ProgressBar
import io.github.composefluent.component.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import java.io.File

internal interface DesktopStorageState : StorageState {
    val expanded: Boolean
    val imageCacheSize: Long
    val showImportConfirmation: Boolean
    val isClearingImageCache: Boolean
    val isClearingDatabaseCache: Boolean
    val isClearingStorage: Boolean
    fun clearImageCache()
    fun clearCacheDatabase()
    fun export()
    fun import()
    fun confirmImport()
    fun cancelImport()
    fun setExpanded(value: Boolean)
}

@Composable
internal fun storagePresenter(
    onExportFilePicker: () -> File?,
    onImportFilePicker: () -> File?,
): DesktopStorageState = run {
    var refreshKey by remember { mutableStateOf(0) }
    val presenter = remember { StoragePresenter() }
    val state = presenter.invoke()

    val notification = koinInject<ComposeInAppNotification>()
    val exportPresenter = remember { ExportDataPresenter() }
    val exportState = exportPresenter.body()
    val scope = rememberCoroutineScope()
    var isClearingImageCache by remember { mutableStateOf(false) }
    var isClearingDatabaseCache by remember { mutableStateOf(false) }

    var importJson by remember { mutableStateOf<String?>(null) }
    val importPresenter = remember(importJson) { importJson?.let { ImportDataPresenter(it) } }
    val importState = importPresenter?.body()

    var showImportConfirmation by remember { mutableStateOf(false) }
    var pendingImportFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(importState) {
        importState?.let {
            try {
                it.import()
                notification.message(Res.string.import_completed)
            } catch (e: Exception) {
                notification.message(Res.string.import_error, success = false)
            } finally {
                importJson = null
                refreshKey++
            }
        }
    }

    var imageCacheSize by remember(refreshKey) {
        mutableLongStateOf(
            SingletonImageLoader
                .get(PlatformContext.INSTANCE)
                .diskCache
                ?.size
                ?.div(1024L * 1024L) ?: 0L,
        )
    }
    LaunchedEffect(refreshKey) {
        imageCacheSize =
            (
                (
                    SingletonImageLoader
                        .get(
                            PlatformContext.INSTANCE,
                        ).diskCache
                        ?.size ?: 0
                ) +
                    presenter.ugoiraCacheSize()
            ) /
            (1024 * 1024)
    }
    var expanded by remember { mutableStateOf(false) }
    object : DesktopStorageState, StorageState by state {
        override val expanded = expanded
        override val imageCacheSize = imageCacheSize
        override val showImportConfirmation = showImportConfirmation
        override val isClearingImageCache = isClearingImageCache
        override val isClearingDatabaseCache = isClearingDatabaseCache
        override val isClearingStorage = isClearingImageCache || isClearingDatabaseCache

        override fun clearImageCache() {
            if (isClearingStorage) {
                return
            }
            scope.launch {
                isClearingImageCache = true
                try {
                    withContext(Dispatchers.IO) {
                        SingletonImageLoader.get(PlatformContext.INSTANCE).diskCache?.clear()
                        presenter.clearUgoiraCache()
                    }
                    refreshKey++
                } finally {
                    isClearingImageCache = false
                }
            }
        }

        override fun clearCacheDatabase() {
            if (isClearingStorage) {
                return
            }
            scope.launch {
                isClearingDatabaseCache = true
                try {
                    presenter.clearCacheSuspend()
                    refreshKey++
                } finally {
                    isClearingDatabaseCache = false
                }
            }
        }

        override fun export() {
            scope.launch {
                try {
                    val json = exportState.export()
                    onExportFilePicker()?.let { file ->
                        file.writeText(json)
                        notification.message(Res.string.save_completed)
                    }
                } catch (e: Exception) {
                    notification.message(Res.string.save_error, success = false)
                }
            }
        }

        override fun import() {
            onImportFilePicker()?.let { file ->
                pendingImportFile = file
                showImportConfirmation = true
            }
        }

        override fun confirmImport() {
            pendingImportFile?.let { file ->
                importJson = file.readText()
            }
            showImportConfirmation = false
            pendingImportFile = null
        }

        override fun cancelImport() {
            showImportConfirmation = false
            pendingImportFile = null
        }

        override fun setExpanded(value: Boolean) {
            expanded = value
            if (value) {
                refreshKey++
            }
        }
    }
}

@OptIn(ExperimentalFluentApi::class)
@Composable
internal fun SettingsStorageSection(
    storageState: DesktopStorageState,
    isAccountActive: Boolean,
    isAiAgentEnabled: Boolean,
    toRSSManagement: () -> Unit,
    toLocalCache: () -> Unit,
    toAgentHistory: () -> Unit,
    toDraftBox: () -> Unit,
    toAppLog: () -> Unit,
) {
    Header(stringResource(Res.string.settings_storage_title))
    CardExpanderItem(
        onClick = toRSSManagement,
        heading = {
            Text(stringResource(Res.string.settings_rss_management_title))
        },
        trailing = {
            FAIcon(
                imageVector = FontAwesomeIcons.Solid.AngleRight,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
            )
        },
        caption = {
            Text(stringResource(Res.string.settings_rss_management_description))
        },
        icon = null,
    )
    AnimatedVisibility(isAccountActive) {
        CardExpanderItem(
            onClick = toLocalCache,
            heading = {
                Text(stringResource(Res.string.settings_local_history_title))
            },
            trailing = {
                FAIcon(
                    imageVector = FontAwesomeIcons.Solid.AngleRight,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                )
            },
            caption = {
                Text(stringResource(Res.string.settings_local_history_description))
            },
            icon = null,
        )
    }
    AnimatedVisibility(isAiAgentEnabled) {
        CardExpanderItem(
            onClick = toAgentHistory,
            heading = {
                Text(stringResource(Res.string.settings_agent_history_title))
            },
            trailing = {
                FAIcon(
                    imageVector = FontAwesomeIcons.Solid.AngleRight,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                )
            },
            caption = {
                Text(stringResource(Res.string.settings_agent_history_description))
            },
            icon = {
                FAIcon(
                    imageVector = FontAwesomeIcons.Solid.Robot,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
        )
    }
    CardExpanderItem(
        onClick = toDraftBox,
        heading = {
            Text(stringResource(Res.string.settings_draft_box_title))
        },
        trailing = {
            FAIcon(
                imageVector = FontAwesomeIcons.Solid.AngleRight,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
            )
        },
        caption = {
            Text(stringResource(Res.string.settings_draft_box_description))
        },
        icon = null,
    )
    Expander(
        expanded = storageState.expanded,
        onExpandedChanged = storageState::setExpanded,
        heading = {
            Text(stringResource(Res.string.settings_storage_title))
        },
        caption = {
            Text(stringResource(Res.string.settings_storage_subtitle))
        },
        icon = null,
    ) {
        CardExpanderItem(
            onClick = toAppLog,
            heading = {
                Text(stringResource(Res.string.settings_storage_app_log))
            },
            caption = {
                Text(stringResource(Res.string.settings_storage_app_log_description))
            },
            trailing = {
                FAIcon(
                    imageVector = FontAwesomeIcons.Solid.AngleRight,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                )
            },
        )

        ExpanderItem(
            heading = {
                Text(text = stringResource(Res.string.settings_storage_clear_image_cache))
            },
            caption = {
                Text(
                    text =
                        stringResource(
                            Res.string.settings_storage_clear_image_cache_description,
                            storageState.imageCacheSize,
                        ),
                )
            },
            trailing = {
                Button(
                    onClick = {
                        storageState.clearImageCache()
                    },
                ) {
                    Text(stringResource(Res.string.delete))
                }
            },
        )
        AnimatedVisibility(storageState.isClearingImageCache) {
            ProgressBar(
                modifier = Modifier.fillMaxWidth(),
            )
        }

        ExpanderItem(
            heading = {
                Text(text = stringResource(Res.string.settings_storage_clear_database))
            },
            caption = {
                Text(
                    text =
                        stringResource(
                            Res.string.settings_storage_clear_database_description,
                            storageState.userCount,
                            storageState.statusCount,
                        ),
                )
            },
            trailing = {
                Button(
                    onClick = {
                        storageState.clearCacheDatabase()
                    },
                ) {
                    Text(stringResource(Res.string.delete))
                }
            },
        )
        AnimatedVisibility(storageState.isClearingDatabaseCache) {
            ProgressBar(
                modifier = Modifier.fillMaxWidth(),
            )
        }

        ExpanderItemSeparator()

        ExpanderItem(
            heading = {
                Text(stringResource(Res.string.settings_storage_export_data))
            },
            caption = {
                Text(stringResource(Res.string.settings_storage_export_data_description))
            },
            trailing = {
                Button(
                    onClick = {
                        storageState.export()
                    },
                ) {
                    Text(stringResource(Res.string.action_export))
                }
            },
        )

        ExpanderItem(
            heading = {
                Text(stringResource(Res.string.settings_storage_import_data))
            },
            caption = {
                Text(stringResource(Res.string.settings_storage_import_data_description))
            },
            trailing = {
                Button(
                    onClick = {
                        storageState.import()
                    },
                ) {
                    Text(stringResource(Res.string.action_import))
                }
            },
        )
    }

    ContentDialog(
        title = stringResource(Res.string.import_confirmation_title),
        visible = storageState.showImportConfirmation,
        content = {
            Text(
                text = stringResource(Res.string.import_confirmation_message),
                modifier = Modifier.padding(16.dp),
            )
        },
        primaryButtonText = stringResource(Res.string.ok),
        closeButtonText = stringResource(Res.string.cancel),
        onButtonClick = {
            when (it) {
                ContentDialogButton.Primary -> {
                    storageState.confirmImport()
                }

                else -> {
                    storageState.cancelImport()
                }
            }
        },
    )
}
