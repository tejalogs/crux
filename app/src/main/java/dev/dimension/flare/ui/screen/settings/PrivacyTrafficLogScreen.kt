package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.R
import dev.dimension.flare.data.privacy.PrivacyCategory
import dev.dimension.flare.data.privacy.PrivacyLog
import dev.dimension.flare.data.privacy.PrivacyLogEntry
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.theme.item
import dev.dimension.flare.ui.theme.last
import dev.dimension.flare.ui.theme.screenHorizontalPadding


/**
 * On-device inspector of every request Flare made.
 *
 * The point of this screen is legibility: the user should be able to see which hosts their
 * accounts talk to and what the client removed, without trusting a marketing claim. Only
 * parameter *names* are ever recorded, so this log cannot itself become a store of tracking ids.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PrivacyTrafficLogScreen(
    onBack: () -> Unit,
) {
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val entries by PrivacyLog.get().entries.collectAsState()

    FlareScaffold(
        topBar = {
            FlareLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_privacy_traffic_log_title))
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
                actions = {
                    if (entries.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                PrivacyLog.get().clear()
                            },
                        ) {
                            FAIcon(
                                FontAwesomeIcons.Solid.Trash,
                                contentDescription = stringResource(id = R.string.settings_app_logging_clear),
                            )
                        }
                    }
                },
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) { paddingValues ->
        if (entries.isEmpty()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = screenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(id = R.string.settings_privacy_traffic_log_empty),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            return@FlareScaffold
        }

        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            item(key = "summary") {
                val telemetry = entries.count { it.category == PrivacyCategory.Telemetry }
                val profiling = entries.count { it.category == PrivacyCategory.Profiling }
                val blocked = entries.count { it.blocked }
                val stripped = entries.sumOf { it.strippedParameters.size }
                Column(
                    modifier =
                        Modifier.padding(
                            horizontal = screenHorizontalPadding,
                            vertical = 12.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(id = R.string.settings_privacy_traffic_log_summary, entries.size),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringResource(id = R.string.settings_privacy_traffic_log_summary_detail, telemetry, profiling, blocked, stripped),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(entries.asReversed(), key = { it.sequence }) { entry ->
                PrivacyLogRow(entry)
            }
        }
    }
}

@Composable
private fun PrivacyLogRow(entry: PrivacyLogEntry) {
    SegmentedListItem(
        onClick = {},
        enabled = false,
        shapes = ListItemDefaults.item(),
        content = {
            Text(
                text = entry.host,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        supportingContent = {
            Text(
                text = buildString {
                    append(entry.method)
                    append(' ')
                    append(entry.path.take(72))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (entry.strippedParameters.isNotEmpty()) {
                Text(
                    text =
                        stringResource(
                            id = R.string.settings_privacy_traffic_log_stripped,
                            entry.strippedParameters.joinToString(),
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = entry.category.label(),
                    style = MaterialTheme.typography.labelSmall,
                    color = entry.category.color(MaterialTheme.colorScheme),
                )
                if (entry.blocked) {
                    Text(
                        text = stringResource(id = R.string.settings_privacy_traffic_log_blocked),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (entry.status != null) {
                    Text(
                        text = entry.status.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

private fun PrivacyCategory.label(): String =
    when (this) {
        PrivacyCategory.Content -> "content"
        PrivacyCategory.Media -> "media"
        PrivacyCategory.Telemetry -> "telemetry"
        PrivacyCategory.Profiling -> "profiling"
        PrivacyCategory.Auth -> "auth"
        PrivacyCategory.Unknown -> "other"
    }

private fun PrivacyCategory.color(scheme: androidx.compose.material3.ColorScheme) =
    when (this) {
        PrivacyCategory.Telemetry, PrivacyCategory.Profiling -> scheme.error
        PrivacyCategory.Auth -> scheme.tertiary
        PrivacyCategory.Media -> scheme.secondary
        PrivacyCategory.Content -> scheme.primary
        PrivacyCategory.Unknown -> scheme.onSurfaceVariant
    }
