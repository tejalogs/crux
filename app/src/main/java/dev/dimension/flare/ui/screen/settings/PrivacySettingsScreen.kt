package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R
import dev.dimension.flare.data.privacy.PrivacySettingsRepository
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.theme.first
import dev.dimension.flare.ui.theme.item
import dev.dimension.flare.ui.theme.last
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import dev.dimension.flare.ui.theme.single
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.koin.compose.koinInject

/**
 * Privacy controls.
 *
 * Every toggle is a real restriction the client enforces itself. Nothing here asks a server to
 * stop collecting; the point is that Flare stops sending in the first place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PrivacySettingsScreen(
    onBack: () -> Unit,
    toTrafficLog: () -> Unit,
    toBlocklist: () -> Unit,
) {
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val repository = koinInject<PrivacySettingsRepository>()
    val settings by repository.settings().collectAsState()
    val scope = rememberCoroutineScope()

    fun update(transform: (dev.dimension.flare.data.privacy.PrivacySettings) -> dev.dimension.flare.data.privacy.PrivacySettings) {
        scope.launch { repository.update(transform(settings)) }
    }

    FlareScaffold(
        topBar = {
            FlareLargeFlexibleTopAppBar(
                title = { Text(text = stringResource(id = R.string.settings_privacy_title)) },
                navigationIcon = { BackButton(onBack = onBack) },
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                PrivacyToggle(
                    title = stringResource(id = R.string.settings_privacy_block_telemetry),
                    description = stringResource(id = R.string.settings_privacy_block_telemetry_description),
                    checked = settings.blockTelemetry,
                    shapes = ListItemDefaults.first(),
                ) { it -> update { s -> s.copy(blockTelemetry = it) } }

                PrivacyToggle(
                    title = stringResource(id = R.string.settings_privacy_block_profiling),
                    description = stringResource(id = R.string.settings_privacy_block_profiling_description),
                    checked = settings.blockProfiling,
                    shapes = ListItemDefaults.item(),
                ) { it -> update { s -> s.copy(blockProfiling = it) } }

                PrivacyToggle(
                    title = stringResource(id = R.string.settings_privacy_scrub_tracking),
                    description = stringResource(id = R.string.settings_privacy_scrub_tracking_description),
                    checked = settings.scrubTrackingParameters,
                    shapes = ListItemDefaults.item(),
                ) { it -> update { s -> s.copy(scrubTrackingParameters = it) } }

                PrivacyToggle(
                    title = stringResource(id = R.string.settings_privacy_strip_fingerprint),
                    description =
                        stringResource(
                            id = R.string.settings_privacy_strip_fingerprint_description,
                        ),
                    checked = settings.stripFingerprintHeaders,
                    shapes = ListItemDefaults.last(),
                ) { it ->
                    update { s -> s.copy(stripFingerprintHeaders = it) }
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                PrivacyToggle(
                    title = stringResource(id = R.string.settings_privacy_app_lock),
                    description = stringResource(id = R.string.settings_privacy_app_lock_description),
                    checked = settings.requireAppLock,
                    shapes = ListItemDefaults.single(),
                ) { it ->
                    update { s -> s.copy(requireAppLock = it) }
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                PrivacyToggle(
                    title = stringResource(id = R.string.settings_privacy_traffic_log),
                    description = stringResource(id = R.string.settings_privacy_traffic_log_description),
                    checked = settings.loggingEnabled,
                    shapes = ListItemDefaults.first(),
                ) { it -> update { s -> s.copy(loggingEnabled = it) } }

                SegmentedListItemLike(
                    title = stringResource(id = R.string.settings_privacy_traffic_log_title),
                    description =
                        stringResource(
                            id = R.string.settings_privacy_traffic_log_open_description,
                        ),
                    shapes = ListItemDefaults.item(),
                    onClick = { toTrafficLog() },
                )

                SegmentedListItemLike(
                    title = stringResource(id = R.string.settings_privacy_open_blocklist),
                    description =
                        stringResource(
                            id = R.string.settings_privacy_open_blocklist_description,
                        ),
                    shapes = ListItemDefaults.last(),
                    onClick = { toBlocklist() },
                )
            }
        }
    }
}

@Composable
private fun PrivacyToggle(
    title: String,
    description: String,
    checked: Boolean,
    shapes: ListItemShapes = ListItemDefaults.item(),
    onCheckedChange: (Boolean) -> Unit,
) {
    SegmentedListItemLike(
        title = title,
        description = description,
        shapes = shapes,
        onClick = { onCheckedChange(!checked) },
        trailing = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
    )
}

@Composable
private fun SegmentedListItemLike(
    title: String,
    description: String,
    shapes: ListItemShapes = ListItemDefaults.item(),
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    androidx.compose.material3.SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        content = { Text(text = title) },
        supportingContent = { Text(text = description) },
        trailingContent = trailing,
    )
}
