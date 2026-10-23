package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.AngleRight
import dev.dimension.flare.Res
import dev.dimension.flare.data.privacy.PrivacySettings
import dev.dimension.flare.data.privacy.PrivacySettingsRepository
import dev.dimension.flare.settings_privacy_block_profiling
import dev.dimension.flare.settings_privacy_block_profiling_description
import dev.dimension.flare.settings_privacy_block_telemetry
import dev.dimension.flare.settings_privacy_block_telemetry_description
import dev.dimension.flare.settings_privacy_open_blocklist
import dev.dimension.flare.settings_privacy_open_blocklist_description
import dev.dimension.flare.settings_privacy_scrub_tracking
import dev.dimension.flare.settings_privacy_scrub_tracking_description
import dev.dimension.flare.settings_privacy_strip_fingerprint
import dev.dimension.flare.settings_privacy_strip_fingerprint_description
import dev.dimension.flare.settings_privacy_subtitle
import dev.dimension.flare.settings_privacy_title
import dev.dimension.flare.settings_privacy_traffic_log
import dev.dimension.flare.settings_privacy_traffic_log_description
import dev.dimension.flare.settings_privacy_traffic_log_open_description
import dev.dimension.flare.settings_privacy_traffic_log_title
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.Header
import io.github.composefluent.ExperimentalFluentApi
import io.github.composefluent.component.CardExpanderItem
import io.github.composefluent.component.Expander
import io.github.composefluent.component.ExpanderItem
import io.github.composefluent.component.ExpanderItemSeparator
import io.github.composefluent.component.Switcher
import io.github.composefluent.component.Text
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalFluentApi::class)
@Composable
internal fun SettingsPrivacySection(
    toPrivacyTrafficLog: () -> Unit,
    toPrivacyBlocklist: () -> Unit,
) {
    Header(stringResource(Res.string.settings_privacy_title))
    val privacyRepository = koinInject<PrivacySettingsRepository>()
    val privacySettings by privacyRepository.settings().collectAsState()
    val privacyScope = rememberCoroutineScope()
    fun updatePrivacy(transform: (PrivacySettings) -> PrivacySettings) {
        privacyScope.launch { privacyRepository.update(transform(privacySettings)) }
    }

    var privacyExpanded by remember { mutableStateOf(false) }
    Expander(
        expanded = privacyExpanded,
        onExpandedChanged = { privacyExpanded = it },
        heading = {
            Text(stringResource(Res.string.settings_privacy_title))
        },
        caption = {
            Text(stringResource(Res.string.settings_privacy_subtitle))
        },
        icon = null,
    ) {
        ExpanderItem(
            heading = {
                Text(stringResource(Res.string.settings_privacy_traffic_log))
            },
            caption = {
                Text(stringResource(Res.string.settings_privacy_traffic_log_description))
            },
            trailing = {
                Switcher(
                    checked = privacySettings.loggingEnabled,
                    onCheckStateChange = { checked ->
                        updatePrivacy { it.copy(loggingEnabled = checked) }
                    },
                    textBefore = true,
                )
            },
        )
        ExpanderItemSeparator()
        ExpanderItem(
            heading = {
                Text(stringResource(Res.string.settings_privacy_block_telemetry))
            },
            caption = {
                Text(stringResource(Res.string.settings_privacy_block_telemetry_description))
            },
            trailing = {
                Switcher(
                    checked = privacySettings.blockTelemetry,
                    onCheckStateChange = { checked ->
                        updatePrivacy { it.copy(blockTelemetry = checked) }
                    },
                    textBefore = true,
                )
            },
        )
        ExpanderItemSeparator()
        ExpanderItem(
            heading = {
                Text(stringResource(Res.string.settings_privacy_block_profiling))
            },
            caption = {
                Text(stringResource(Res.string.settings_privacy_block_profiling_description))
            },
            trailing = {
                Switcher(
                    checked = privacySettings.blockProfiling,
                    onCheckStateChange = { checked ->
                        updatePrivacy { it.copy(blockProfiling = checked) }
                    },
                    textBefore = true,
                )
            },
        )
        ExpanderItemSeparator()
        ExpanderItem(
            heading = {
                Text(stringResource(Res.string.settings_privacy_scrub_tracking))
            },
            caption = {
                Text(stringResource(Res.string.settings_privacy_scrub_tracking_description))
            },
            trailing = {
                Switcher(
                    checked = privacySettings.scrubTrackingParameters,
                    onCheckStateChange = { checked ->
                        updatePrivacy { it.copy(scrubTrackingParameters = checked) }
                    },
                    textBefore = true,
                )
            },
        )
        ExpanderItemSeparator()
        ExpanderItem(
            heading = {
                Text(stringResource(Res.string.settings_privacy_strip_fingerprint))
            },
            caption = {
                Text(stringResource(Res.string.settings_privacy_strip_fingerprint_description))
            },
            trailing = {
                Switcher(
                    checked = privacySettings.stripFingerprintHeaders,
                    onCheckStateChange = { checked ->
                        updatePrivacy { it.copy(stripFingerprintHeaders = checked) }
                    },
                    textBefore = true,
                )
            },
        )
        ExpanderItemSeparator()
        CardExpanderItem(
            onClick = toPrivacyTrafficLog,
            heading = {
                Text(stringResource(Res.string.settings_privacy_traffic_log_title))
            },
            caption = {
                Text(stringResource(Res.string.settings_privacy_traffic_log_open_description))
            },
            trailing = {
                FAIcon(
                    imageVector = FontAwesomeIcons.Solid.AngleRight,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                )
            },
        )
        ExpanderItemSeparator()
        CardExpanderItem(
            onClick = toPrivacyBlocklist,
            heading = {
                Text(stringResource(Res.string.settings_privacy_open_blocklist))
            },
            caption = {
                Text(stringResource(Res.string.settings_privacy_open_blocklist_description))
            },
            trailing = {
                FAIcon(
                    imageVector = FontAwesomeIcons.Solid.AngleRight,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                )
            },
        )
    }
}
