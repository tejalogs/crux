package org.crux.ui.screen.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Globe
import compose.icons.fontawesomeicons.solid.Lock
import compose.icons.fontawesomeicons.solid.Robot
import compose.icons.fontawesomeicons.solid.TrashCan
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.crux.R
import org.crux.data.ai.OnDeviceHardwareBackend
import org.crux.data.ai.OnDeviceModelSpec
import org.crux.data.ai.benchmark.BenchmarkProgress
import org.crux.data.ai.benchmark.BenchmarkResult
import org.crux.data.ai.benchmark.BenchmarkStage
import org.crux.data.ai.benchmark.OnDeviceHardwareBenchmarkEngine
import org.crux.data.privacy.PrivacySettingsRepository
import org.crux.ui.component.BackButton
import org.crux.ui.component.CruxLargeFlexibleTopAppBar
import org.crux.ui.component.CruxScaffold
import org.crux.ui.component.FAIcon
import org.crux.ui.haptic.CruxHapticEvent
import org.crux.ui.haptic.rememberFlareHapticFeedback
import org.crux.ui.presenter.invoke
import org.crux.ui.screen.crux.hasSession
import org.crux.ui.screen.home.LiveWebPrivacyCounters
import org.crux.ui.screen.home.LiveWebSite
import org.crux.ui.screen.home.LocalLiveWebPages
import org.crux.ui.screen.home.clearSession
import org.crux.ui.screen.home.cookieManager
import org.crux.ui.theme.first
import org.crux.ui.theme.item
import org.crux.ui.theme.last
import org.crux.ui.theme.screenHorizontalPadding
import org.crux.ui.theme.single
import org.koin.compose.koinInject

/**
 * Privacy controls.
 *
 * Every toggle is a real restriction the client enforces itself. Nothing here asks a server to
 * stop collecting; the point is that Crux stops sending in the first place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PrivacySettingsScreen(
    onBack: () -> Unit,
    toTrafficLog: () -> Unit,
    toBlocklist: () -> Unit,
    toPrivacyTweaks: () -> Unit,
) {
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val repository = koinInject<PrivacySettingsRepository>()
    val settings by repository.settings().collectAsState()
    val scope = rememberCoroutineScope()

    fun update(transform: (org.crux.data.privacy.PrivacySettings) -> org.crux.data.privacy.PrivacySettings) {
        scope.launch { repository.update(transform(settings)) }
    }

    CruxScaffold(
        topBar = {
            CruxLargeFlexibleTopAppBar(
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
            // Real totals from the web tabs: refused requests (Strict sites) and link cleaning.
            val counters = LiveWebPrivacyCounters.get(LocalContext.current)
            val blockedTrackers by counters.blockedRequests.collectAsState()
            val strippedParams by counters.parametersRemoved.collectAsState()

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(id = R.string.privacy_hub_title),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(id = R.string.privacy_hub_subtitle),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    // Live Egress Stats Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        EgressMetricBadge(
                            value = "$blockedTrackers",
                            label = stringResource(id = R.string.privacy_blocked_trackers),
                            color = if (blockedTrackers > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        EgressMetricBadge(
                            value = "$strippedParams",
                            label = stringResource(id = R.string.privacy_params_stripped),
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                SegmentedListItemLike(
                    title = stringResource(id = R.string.web_tweaks_title),
                    description = stringResource(id = R.string.web_tweaks_subtitle),
                    shapes = ListItemDefaults.single(),
                    onClick = toPrivacyTweaks,
                )
            }

            LiveWebProfilesSandboxCard()

            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                PrivacyToggle(
                    title = stringResource(id = R.string.settings_privacy_scrub_tracking),
                    description = stringResource(id = R.string.settings_privacy_scrub_tracking_description),
                    checked = settings.scrubTrackingParameters,
                    shapes = ListItemDefaults.single(),
                ) { it -> update { s -> s.copy(scrubTrackingParameters = it) } }
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
    onClick: () -> Unit,
    shapes: ListItemShapes = ListItemDefaults.item(),
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

@Composable
private fun EgressMetricBadge(
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = color,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun LiveWebProfilesSandboxCard() {
    val context = LocalContext.current
    var refreshTick by remember { mutableIntStateOf(0) }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.Globe,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.privacy_web_sessions_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(id = R.string.privacy_web_sessions_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            LiveWebSite.entries.forEach { site ->
                val status =
                    remember(site, refreshTick) {
                        val hasStoredData =
                            runCatching {
                                !site.cookieManager().getCookie(site.homeUrl).isNullOrBlank()
                            }.getOrDefault(false)
                        webSessionStatus(signedIn = site.hasSession(), hasStoredData = hasStoredData)
                    }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = site.name,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text =
                                    stringResource(
                                        id =
                                            if (status.signedIn) {
                                                R.string.privacy_web_session_active
                                            } else {
                                                R.string.privacy_web_session_clean
                                            },
                                        site.homeUrl.removePrefix("https://"),
                                    ),
                                style = MaterialTheme.typography.bodySmall,
                                color =
                                    if (status.signedIn) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            )
                        }
                        if (status.canClear) {
                            val pages = LocalLiveWebPages.current
                            TextButton(
                                onClick = {
                                    site.clearSession(context)
                                    pages?.clearAndReload(site)
                                    refreshTick++
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(R.string.privacy_web_session_cleared, site.name),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                },
                            ) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.TrashCan,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(13.dp),
                                )
                                Spacer(modifier = Modifier.size(6.dp))
                                Text(
                                    text = stringResource(id = R.string.privacy_web_session_clear),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
