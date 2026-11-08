package org.crux.ui.screen.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.persistentMapOf
import moe.tlaster.precompose.molecule.producePresenter
import org.crux.R
import org.crux.common.AndroidMediaSaveLocationRepository
import org.crux.common.MediaSaveLocationMode
import org.crux.data.model.TimelineMediaLayout
import org.crux.data.model.VideoAutoplay
import org.crux.data.model.appearance.AppearanceKeys
import org.crux.ui.component.BackButton
import org.crux.ui.component.CruxDropdownMenu
import org.crux.ui.component.CruxLargeFlexibleTopAppBar
import org.crux.ui.component.CruxScaffold
import org.crux.ui.component.LocalGlobalAppearance
import org.crux.ui.component.LocalTimelineAppearance
import org.crux.ui.component.status.StatusItem
import org.crux.ui.model.isSuccess
import org.crux.ui.model.onSuccess
import org.crux.ui.theme.first
import org.crux.ui.theme.item
import org.crux.ui.theme.last
import org.crux.ui.theme.screenHorizontalPadding
import org.crux.ui.theme.single
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AppearanceMediaScreen(onBack: () -> Unit) {
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val state by producePresenter { appearancePresenter() }
    CruxScaffold(
        topBar = {
            CruxLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_appearance_media_group_title))
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
        modifier =
            Modifier
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) {
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(it)
                    .padding(horizontal = screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            AppearanceMediaContent(state = state)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AppearanceMediaContent(state: AppearanceSettingsUpdater) {
    val globalAppearance = LocalGlobalAppearance.current
    val mediaSaveLocationRepository = koinInject<AndroidMediaSaveLocationRepository>()
    val mediaSaveLocation by mediaSaveLocationRepository.state.collectAsState()
    var showMediaSaveLocationMenu by remember { mutableStateOf(false) }
    val mediaDirectoryLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
            onResult = { uri ->
                uri?.let { mediaSaveLocationRepository.setCustomDirectory(it) }
            },
        )
    Column(
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        SingleChoiceSettingsItem(
            headline = { Text(text = stringResource(id = R.string.settings_lite_mode_title)) },
            supporting = { Text(text = stringResource(id = R.string.settings_lite_mode_description)) },
            items =
                persistentMapOf(
                    org.crux.data.model.LiteMode.OFF to
                        stringResource(id = R.string.settings_lite_mode_off_description),
                    org.crux.data.model.LiteMode.BALANCED to
                        stringResource(id = R.string.settings_lite_mode_balanced_description),
                    org.crux.data.model.LiteMode.TEXT_ONLY to
                        stringResource(id = R.string.settings_lite_mode_text_only_description),
                ),
            selected = globalAppearance.liteMode,
            onSelected = {
                state.update(AppearanceKeys.LiteMode, it)
            },
            shapes = ListItemDefaults.first(),
        )
        val mediaSaveLocationText =
            when (mediaSaveLocation.mode) {
                MediaSaveLocationMode.DefaultDownloads -> {
                    stringResource(id = R.string.settings_media_save_location_downloads)
                }

                MediaSaveLocationMode.CustomDirectory -> {
                    mediaSaveLocation.displayName
                        ?: stringResource(id = R.string.settings_media_save_location_custom_folder)
                }

                MediaSaveLocationMode.AskEveryTime -> {
                    stringResource(id = R.string.settings_media_save_location_ask_every_time)
                }
            }
        SegmentedListItem(
            onClick = {
                showMediaSaveLocationMenu = true
            },
            shapes = ListItemDefaults.last(),
            content = {
                Text(text = stringResource(id = R.string.settings_media_save_location))
            },
            supportingContent = {
                Text(text = stringResource(id = R.string.settings_media_save_location_description))
            },
            trailingContent = {
                Box {
                    TextButton(
                        onClick = {
                            showMediaSaveLocationMenu = true
                        },
                    ) {
                        Text(text = mediaSaveLocationText)
                    }
                    CruxDropdownMenu(
                        expanded = showMediaSaveLocationMenu,
                        onDismissRequest = {
                            showMediaSaveLocationMenu = false
                        },
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(text = stringResource(id = R.string.settings_media_save_location_choose_folder))
                            },
                            onClick = {
                                showMediaSaveLocationMenu = false
                                mediaDirectoryLauncher.launch(null)
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(text = stringResource(id = R.string.settings_media_save_location_ask_every_time))
                            },
                            onClick = {
                                showMediaSaveLocationMenu = false
                                mediaSaveLocationRepository.setAskEveryTime()
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(text = stringResource(id = R.string.settings_media_save_location_reset_downloads))
                            },
                            onClick = {
                                showMediaSaveLocationMenu = false
                                mediaSaveLocationRepository.setDefaultDownloads()
                            },
                        )
                    }
                }
            },
        )
    }
}
