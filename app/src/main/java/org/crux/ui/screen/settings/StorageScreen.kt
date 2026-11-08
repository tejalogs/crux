package org.crux.ui.screen.settings

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.imageLoader
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Bookmark
import compose.icons.fontawesomeicons.solid.CloudArrowDown
import compose.icons.fontawesomeicons.solid.Database
import compose.icons.fontawesomeicons.solid.Envelope
import compose.icons.fontawesomeicons.solid.FileExport
import compose.icons.fontawesomeicons.solid.FileImport
import compose.icons.fontawesomeicons.solid.HardDrive
import compose.icons.fontawesomeicons.solid.Images
import compose.icons.fontawesomeicons.solid.TrashCan
import compose.icons.fontawesomeicons.solid.Wifi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.tlaster.precompose.molecule.producePresenter
import org.crux.R
import org.crux.common.ComposeInAppNotification
import org.crux.common.OfflineMediaPrefetcher
import org.crux.data.cache.CacheEvictionManager
import org.crux.data.cache.OfflineCacheConfig
import org.crux.data.cache.OfflineCacheRepository
import org.crux.data.readinglist.ReadingListRepository
import org.crux.ui.component.BackButton
import org.crux.ui.component.CruxLargeFlexibleTopAppBar
import org.crux.ui.component.CruxScaffold
import org.crux.ui.component.ThemeIconData
import org.crux.ui.component.ThemedIcon
import org.crux.ui.presenter.ExportDataPresenter
import org.crux.ui.presenter.ImportDataPresenter
import org.crux.ui.presenter.invoke
import org.crux.ui.presenter.settings.StoragePresenter
import org.crux.ui.presenter.settings.StorageState
import org.crux.ui.theme.first
import org.crux.ui.theme.item
import org.crux.ui.theme.last
import org.crux.ui.theme.screenHorizontalPadding
import org.koin.compose.koinInject
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StorageScreen(
    onBack: () -> Unit,
    toAppLog: () -> Unit,
) {
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    val state by producePresenter {
        storagePresenter(context = context)
    }

    val offlineCacheRepo = koinInject<OfflineCacheRepository>()
    val cacheEvictionManager = koinInject<CacheEvictionManager>()
    val notification = koinInject<ComposeInAppNotification>()
    val readingListRepo = koinInject<ReadingListRepository>()
    val offlineConfig by offlineCacheRepo.settings.collectAsState()
    val readingListItems by readingListRepo.items.collectAsState()
    val prefetcher = remember { OfflineMediaPrefetcher(context) }
    val prefetchProgress by prefetcher.status.collectAsState()
    val scope = rememberCoroutineScope()

    var showCacheSizeDialog by remember { mutableStateOf(false) }
    var showRetentionDaysDialog by remember { mutableStateOf(false) }
    var isPruningDatabase by remember { mutableStateOf(false) }
    var showImportConfirmation by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var showExportWarning by remember { mutableStateOf(false) }
    val exportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json"),
            onResult = { uri ->
                uri?.let { state.export(it) }
            },
        )

    if (showExportWarning) {
        AlertDialog(
            onDismissRequest = { showExportWarning = false },
            title = { Text(text = stringResource(id = R.string.storage_export_warning_title)) },
            text = { Text(text = stringResource(id = R.string.storage_export_warning_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExportWarning = false
                        exportLauncher.launch("crux_data_export.json")
                    },
                ) {
                    Text(text = stringResource(id = R.string.storage_export_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportWarning = false }) {
                    Text(text = stringResource(id = android.R.string.cancel))
                }
            },
        )
    }

    if (showCacheSizeDialog) {
        AlertDialog(
            onDismissRequest = { showCacheSizeDialog = false },
            title = { Text(text = stringResource(id = R.string.storage_media_cache_limit)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OfflineCacheConfig.ALLOWED_SIZES_MB.forEach { sizeMb ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch { offlineCacheRepo.setMaxMediaCacheSizeMb(sizeMb) }
                                        showCacheSizeDialog = false
                                    }.padding(vertical = 8.dp),
                        ) {
                            RadioButton(
                                selected = offlineConfig.maxMediaCacheSizeMb == sizeMb,
                                onClick = {
                                    scope.launch { offlineCacheRepo.setMaxMediaCacheSizeMb(sizeMb) }
                                    showCacheSizeDialog = false
                                },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val label = if (sizeMb >= 1024) "${sizeMb / 1024} GB" else "$sizeMb MB"
                            Text(text = label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCacheSizeDialog = false }) {
                    Text(text = stringResource(id = R.string.action_done))
                }
            },
        )
    }

    if (showRetentionDaysDialog) {
        AlertDialog(
            onDismissRequest = { showRetentionDaysDialog = false },
            title = { Text(text = stringResource(id = R.string.storage_feed_cache_retention)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OfflineCacheConfig.ALLOWED_RETENTION_DAYS.forEach { days ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch { offlineCacheRepo.setTimelineCacheRetentionDays(days) }
                                        showRetentionDaysDialog = false
                                    }.padding(vertical = 8.dp),
                        ) {
                            RadioButton(
                                selected = offlineConfig.timelineCacheRetentionDays == days,
                                onClick = {
                                    scope.launch { offlineCacheRepo.setTimelineCacheRetentionDays(days) }
                                    showRetentionDaysDialog = false
                                },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(id = R.string.storage_retention_days, days))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRetentionDaysDialog = false }) {
                    Text(text = stringResource(id = R.string.action_done))
                }
            },
        )
    }

    if (showImportConfirmation) {
        AlertDialog(
            onDismissRequest = {
                showImportConfirmation = false
                pendingImportUri = null
            },
            title = {
                Text(text = stringResource(id = R.string.import_confirmation_title))
            },
            text = {
                Text(text = stringResource(id = R.string.import_confirmation_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingImportUri?.let { state.import(it) }
                        showImportConfirmation = false
                        pendingImportUri = null
                    },
                ) {
                    Text(text = stringResource(id = android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showImportConfirmation = false
                        pendingImportUri = null
                    },
                ) {
                    Text(text = stringResource(id = android.R.string.cancel))
                }
            },
        )
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        CruxScaffold(
            topBar = {
                CruxLargeFlexibleTopAppBar(
                    title = {
                        Text(text = stringResource(id = R.string.settings_storage_title))
                    },
                    navigationIcon = {
                        BackButton(onBack = onBack)
                    },
                    scrollBehavior = topAppBarScrollBehavior,
                )
            },
            modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
        ) {
            Column(
                modifier =
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(it)
                        .padding(horizontal = screenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                Text(
                    text = stringResource(id = R.string.settings_storage_section_data_cache),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
                SegmentedListItem(
                    onClick = {
                        state.clearImageCache()
                    },
                    shapes = ListItemDefaults.first(),
                    content = {
                        Text(text = stringResource(id = R.string.settings_storage_clear_image_cache))
                    },
                    supportingContent = {
                        Text(
                            text =
                                stringResource(
                                    id = R.string.settings_storage_clear_image_cache_description,
                                    state.imageCacheSize,
                                ),
                        )
                    },
                    leadingContent = {
                        ThemedIcon(
                            FontAwesomeIcons.Solid.Images,
                            contentDescription = stringResource(id = R.string.settings_storage_clear_image_cache),
                            color = ThemeIconData.Color.ForestGreen,
                        )
                    },
                )
                SegmentedListItem(
                    onClick = {
                        state.clearCacheDatabase()
                    },
                    shapes = ListItemDefaults.item(),
                    content = {
                        Text(text = stringResource(id = R.string.settings_storage_clear_database))
                    },
                    supportingContent = {
                        Text(
                            text =
                                stringResource(
                                    id = R.string.settings_storage_clear_database_description,
                                    state.userCount,
                                    state.statusCount,
                                ),
                        )
                    },
                    leadingContent = {
                        ThemedIcon(
                            FontAwesomeIcons.Solid.Database,
                            contentDescription = stringResource(id = R.string.settings_storage_clear_database),
                            color = ThemeIconData.Color.ImperialMagenta,
                        )
                    },
                )
                SegmentedListItem(
                    onClick = {
                        toAppLog.invoke()
                    },
                    shapes = ListItemDefaults.item(),
                    content = {
                        Text(text = stringResource(id = R.string.settings_storage_app_log))
                    },
                    supportingContent = {
                        Text(
                            text =
                                stringResource(id = R.string.settings_storage_app_log_description),
                        )
                    },
                    leadingContent = {
                        ThemedIcon(
                            FontAwesomeIcons.Solid.Envelope,
                            contentDescription = stringResource(id = R.string.settings_storage_app_log),
                            color = ThemeIconData.Color.DeepTeal,
                        )
                    },
                )

                SegmentedListItem(
                    onClick = {
                        showExportWarning = true
                    },
                    shapes = ListItemDefaults.item(),
                    content = {
                        Text(text = stringResource(id = R.string.settings_storage_export_data))
                    },
                    supportingContent = {
                        Text(
                            text =
                                stringResource(id = R.string.settings_storage_export_data_description),
                        )
                    },
                    leadingContent = {
                        ThemedIcon(
                            FontAwesomeIcons.Solid.FileExport,
                            contentDescription = stringResource(id = R.string.settings_storage_export_data),
                            color = ThemeIconData.Color.DarkAmber,
                        )
                    },
                )

                val importLauncher =
                    rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocument(),
                        onResult = { uri ->
                            uri?.let {
                                pendingImportUri = it
                                showImportConfirmation = true
                            }
                        },
                    )

                SegmentedListItem(
                    onClick = {
                        importLauncher.launch(arrayOf("*/*"))
                    },
                    shapes = ListItemDefaults.last(),
                    content = {
                        Text(text = stringResource(id = R.string.settings_storage_import_data))
                    },
                    supportingContent = {
                        Text(
                            text =
                                stringResource(id = R.string.settings_storage_import_data_description),
                        )
                    },
                    leadingContent = {
                        ThemedIcon(
                            FontAwesomeIcons.Solid.FileImport,
                            contentDescription = stringResource(id = R.string.settings_storage_import_data),
                            color = ThemeIconData.Color.SapphireBlue,
                        )
                    },
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(id = R.string.settings_storage_section_offline),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )

                SegmentedListItem(
                    onClick = { showCacheSizeDialog = true },
                    shapes = ListItemDefaults.first(),
                    content = {
                        Text(text = stringResource(id = R.string.storage_media_cache_limit))
                    },
                    supportingContent = {
                        val limitLabel =
                            if (offlineConfig.maxMediaCacheSizeMb >= 1024) {
                                "${offlineConfig.maxMediaCacheSizeMb / 1024} GB"
                            } else {
                                "${offlineConfig.maxMediaCacheSizeMb} MB"
                            }
                        Text(text = stringResource(id = R.string.storage_media_cache_summary, limitLabel, state.imageCacheSize))
                    },
                    leadingContent = {
                        ThemedIcon(
                            FontAwesomeIcons.Solid.HardDrive,
                            contentDescription = stringResource(id = R.string.storage_media_cache_limit),
                            color = ThemeIconData.Color.ForestGreen,
                        )
                    },
                )

                SegmentedListItem(
                    onClick = {
                        scope.launch {
                            offlineCacheRepo.setPrefetchMediaForReadingList(!offlineConfig.prefetchMediaForReadingList)
                        }
                    },
                    shapes = ListItemDefaults.item(),
                    content = {
                        Text(text = stringResource(id = R.string.storage_prefetch_media))
                    },
                    supportingContent = {
                        Text(text = stringResource(id = R.string.storage_prefetch_media_desc))
                    },
                    trailingContent = {
                        Switch(
                            checked = offlineConfig.prefetchMediaForReadingList,
                            onCheckedChange = { checked ->
                                scope.launch {
                                    offlineCacheRepo.setPrefetchMediaForReadingList(checked)
                                }
                            },
                        )
                    },
                    leadingContent = {
                        ThemedIcon(
                            FontAwesomeIcons.Solid.CloudArrowDown,
                            contentDescription = stringResource(id = R.string.storage_prefetch_media),
                            color = ThemeIconData.Color.DeepTeal,
                        )
                    },
                )

                SegmentedListItem(
                    onClick = {
                        scope.launch {
                            offlineCacheRepo.setWifiOnlyPrefetch(!offlineConfig.wifiOnlyPrefetch)
                        }
                    },
                    shapes = ListItemDefaults.item(),
                    content = {
                        Text(text = stringResource(id = R.string.storage_wifi_only))
                    },
                    supportingContent = {
                        Text(text = stringResource(id = R.string.storage_wifi_only_desc))
                    },
                    trailingContent = {
                        Switch(
                            checked = offlineConfig.wifiOnlyPrefetch,
                            onCheckedChange = { checked ->
                                scope.launch {
                                    offlineCacheRepo.setWifiOnlyPrefetch(checked)
                                }
                            },
                        )
                    },
                    leadingContent = {
                        ThemedIcon(
                            FontAwesomeIcons.Solid.Wifi,
                            contentDescription = stringResource(id = R.string.storage_wifi_only),
                            color = ThemeIconData.Color.SapphireBlue,
                        )
                    },
                )

                SegmentedListItem(
                    onClick = {
                        prefetcher.prefetchAll(readingListItems)
                    },
                    shapes = ListItemDefaults.last(),
                    content = {
                        Text(text = stringResource(id = R.string.storage_reading_list_cache))
                    },
                    supportingContent = {
                        if (prefetchProgress.isPrefetching) {
                            Text(
                                text =
                                    stringResource(
                                        id = R.string.storage_reading_list_caching,
                                        prefetchProgress.completedCount,
                                        prefetchProgress.totalCount,
                                    ),
                            )
                        } else {
                            Text(text = stringResource(id = R.string.storage_reading_list_summary, readingListItems.size))
                        }
                    },
                    leadingContent = {
                        ThemedIcon(
                            FontAwesomeIcons.Solid.Bookmark,
                            contentDescription = stringResource(id = R.string.storage_reading_list_cache),
                            color = ThemeIconData.Color.ImperialMagenta,
                        )
                    },
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(id = R.string.settings_storage_section_database),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )

                SegmentedListItem(
                    onClick = { showRetentionDaysDialog = true },
                    shapes = ListItemDefaults.first(),
                    content = {
                        Text(text = stringResource(id = R.string.storage_feed_cache_retention))
                    },
                    supportingContent = {
                        Text(text = stringResource(id = R.string.storage_retention_summary, offlineConfig.timelineCacheRetentionDays))
                    },
                    leadingContent = {
                        ThemedIcon(
                            FontAwesomeIcons.Solid.Database,
                            contentDescription = stringResource(id = R.string.storage_feed_cache_retention),
                            color = ThemeIconData.Color.DeepTeal,
                        )
                    },
                )

                SegmentedListItem(
                    onClick = {
                        scope.launch {
                            offlineCacheRepo.setAutoPruneDatabaseCache(!offlineConfig.autoPruneDatabaseCache)
                        }
                    },
                    shapes = ListItemDefaults.item(),
                    content = {
                        Text(text = stringResource(id = R.string.storage_auto_prune))
                    },
                    supportingContent = {
                        Text(text = stringResource(id = R.string.storage_auto_prune_desc))
                    },
                    trailingContent = {
                        Switch(
                            checked = offlineConfig.autoPruneDatabaseCache,
                            onCheckedChange = { checked ->
                                scope.launch {
                                    offlineCacheRepo.setAutoPruneDatabaseCache(checked)
                                }
                            },
                        )
                    },
                    leadingContent = {
                        ThemedIcon(
                            FontAwesomeIcons.Solid.HardDrive,
                            contentDescription = stringResource(id = R.string.storage_auto_prune),
                            color = ThemeIconData.Color.ForestGreen,
                        )
                    },
                )

                SegmentedListItem(
                    onClick = {
                        if (!isPruningDatabase) {
                            scope.launch {
                                isPruningDatabase = true
                                try {
                                    val result = cacheEvictionManager.purgeExpiredCache()
                                    android.widget.Toast
                                        .makeText(
                                            context,
                                            context.applicationContext.getString(
                                                R.string.storage_purge_success,
                                                result.purgedTimelineEntries,
                                                result.purgedStatuses,
                                                result.durationMs,
                                            ),
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                } catch (e: Exception) {
                                    android.widget.Toast
                                        .makeText(
                                            context,
                                            context.applicationContext.getString(
                                                R.string.storage_purge_failed,
                                                e.message.orEmpty(),
                                            ),
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                } finally {
                                    isPruningDatabase = false
                                }
                            }
                        }
                    },
                    shapes = ListItemDefaults.last(),
                    content = {
                        Text(text = stringResource(id = R.string.storage_prune_now))
                    },
                    supportingContent = {
                        if (isPruningDatabase) {
                            Text(text = stringResource(id = R.string.storage_pruning))
                        } else {
                            val lastPrune = offlineConfig.lastDatabasePruneTimestamp
                            val lastPruneStr =
                                if (lastPrune > 0) {
                                    val hoursAgo = (Clock.System.now().toEpochMilliseconds() - lastPrune) / 3600000
                                    if (hoursAgo == 0L) {
                                        stringResource(id = R.string.storage_just_now)
                                    } else {
                                        stringResource(id = R.string.storage_hours_ago, hoursAgo)
                                    }
                                } else {
                                    stringResource(id = R.string.storage_never)
                                }
                            Text(
                                text =
                                    stringResource(
                                        id = R.string.storage_prune_summary,
                                        offlineConfig.timelineCacheRetentionDays,
                                        lastPruneStr,
                                    ),
                            )
                        }
                    },
                    trailingContent = {
                        if (isPruningDatabase) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    },
                    leadingContent = {
                        ThemedIcon(
                            FontAwesomeIcons.Solid.TrashCan,
                            contentDescription = stringResource(id = R.string.storage_prune_now),
                            color = ThemeIconData.Color.DarkAmber,
                        )
                    },
                )
            }
        }
        if (state.isClearingStorage) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {},
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun storagePresenter(context: Context) =
    run {
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

        LaunchedEffect(importState) {
            importState?.let {
                try {
                    it.import()
                    notification.message(R.string.import_completed)
                } catch (e: Exception) {
                    notification.message(R.string.import_error)
                } finally {
                    importJson = null
                }
            }
        }

        var imageCacheSize by remember {
            mutableLongStateOf(
                context.imageLoader.diskCache
                    ?.size
                    ?.div(1024L * 1024L) ?: 0L,
            )
        }

        LaunchedEffect(Unit) {
            imageCacheSize = (context.imageLoader.diskCache?.size ?: 0) / (1024 * 1024)
        }
        object : StorageState by state {
            val imageCacheSize: Long = imageCacheSize
            val isClearingImageCache: Boolean = isClearingImageCache
            val isClearingDatabaseCache: Boolean = isClearingDatabaseCache
            val isClearingStorage: Boolean = isClearingImageCache || isClearingDatabaseCache

            fun clearImageCache() {
                if (isClearingStorage) {
                    return
                }
                scope.launch {
                    isClearingImageCache = true
                    try {
                        withContext(Dispatchers.IO) {
                            context.imageLoader.diskCache?.clear()
                        }
                        imageCacheSize = 0L
                    } finally {
                        isClearingImageCache = false
                    }
                }
            }

            fun clearCacheDatabase() {
                if (isClearingStorage) {
                    return
                }
                scope.launch {
                    isClearingDatabaseCache = true
                    try {
                        presenter.clearCacheSuspend()
                    } finally {
                        isClearingDatabaseCache = false
                    }
                }
            }

            fun export(uri: android.net.Uri) {
                scope.launch {
                    try {
                        val json = exportState.export()
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                                outputStream.write(json.toByteArray())
                            }
                        }
                        notification.message(R.string.save_completed)
                    } catch (e: Exception) {
                        notification.message(R.string.save_error)
                    }
                }
            }

            fun import(uri: android.net.Uri) {
                scope.launch {
                    val json =
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                                    reader.readText()
                                }
                            }
                        }
                    importJson = json
                }
            }
        }
    }
