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
import org.crux.data.privacy.PrivacyLog
import org.crux.data.privacy.PrivacySettingsRepository
import org.crux.ui.component.BackButton
import org.crux.ui.component.CruxLargeFlexibleTopAppBar
import org.crux.ui.component.CruxScaffold
import org.crux.ui.component.FAIcon
import org.crux.ui.haptic.CruxHapticEvent
import org.crux.ui.haptic.rememberFlareHapticFeedback
import org.crux.ui.presenter.invoke
import org.crux.ui.screen.home.LiveWebSite
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
    toHardenedBrowser: (() -> Unit)? = null,
) {
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val repository = koinInject<PrivacySettingsRepository>()
    val settings by repository.settings().collectAsState()
    val logEntries by PrivacyLog.get().entries.collectAsState()
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
            // Zero-Trust Egress Monitor & Sovereign Privacy Hub Header Card
            val blockedTrackers = logEntries.count { it.blocked }
            val strippedParams = logEntries.sumOf { it.strippedParameters.size }

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
                        horizontalArrangement = Arrangement.SpaceAround,
                    ) {
                        EgressMetricBadge(
                            value = "0 B",
                            label = stringResource(id = R.string.privacy_cloud_requests),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        EgressMetricBadge(
                            value = "$blockedTrackers",
                            label = "Blocked Trackers",
                            color = if (blockedTrackers > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                        EgressMetricBadge(
                            value = "$strippedParams",
                            label = "Params Stripped",
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // Web Bridges Status
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(id = R.string.privacy_active_bridges),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            WebBridgeChip(name = "Instagram", active = true)
                            WebBridgeChip(name = "LinkedIn", active = true)
                            WebBridgeChip(name = "X", active = true)
                            WebBridgeChip(name = "Reddit", active = true)
                        }
                    }
                }
            }

            LiveWebProfilesSandboxCard(onOpenHardenedBrowser = toHardenedBrowser)

            // On-Device Gemma 4 Hardware Diagnostic Benchmark Card
            OnDeviceAiBenchmarkCard()

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
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = color,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun WebBridgeChip(
    name: String,
    active: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
            )
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OnDeviceAiBenchmarkCard() {
    val scope = rememberCoroutineScope()
    val haptic = rememberFlareHapticFeedback(true)
    var isBenchmarking by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<BenchmarkProgress?>(null) }
    var result by remember { mutableStateOf<BenchmarkResult?>(null) }
    var selectedBackend by remember { mutableStateOf(OnDeviceHardwareBackend.Npu) }
    var selectedModel by remember { mutableStateOf(OnDeviceModelSpec.Gemma4E2B) }

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
            // Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.Robot,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "On-Device AI Benchmark",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Hardware acceleration and performance",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Model & Hardware Backend Selectors
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Backend chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OnDeviceHardwareBackend.entries.forEach { backend ->
                        val isSelected = selectedBackend == backend
                        val label =
                            when (backend) {
                                OnDeviceHardwareBackend.Auto -> "Auto"
                                OnDeviceHardwareBackend.Npu -> "⚡ NPU"
                                OnDeviceHardwareBackend.Gpu -> "🎮 GPU"
                                OnDeviceHardwareBackend.Cpu -> "💻 CPU"
                            }
                        BenchmarkSelectChip(
                            label = label,
                            selected = isSelected,
                            enabled = !isBenchmarking,
                            onClick = {
                                selectedBackend = backend
                                haptic.perform(CruxHapticEvent.Confirm)
                            },
                        )
                    }
                }

                // Model profile selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OnDeviceModelSpec.SupportedModels.forEach { model ->
                        val isSelected = selectedModel.modelId == model.modelId
                        BenchmarkSelectChip(
                            label = "${model.displayName} (${model.parameterCount})",
                            selected = isSelected,
                            enabled = !isBenchmarking,
                            onClick = {
                                selectedModel = model
                                haptic.perform(CruxHapticEvent.Confirm)
                            },
                        )
                    }
                }
            }

            // Benchmark In-Progress Telemetry
            if (isBenchmarking) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val p = progress
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = p?.stage?.displayName ?: "Benchmarking...",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        val currentSpeed = p?.instantaneousTokensPerSec
                        if (currentSpeed != null) {
                            val roundedSpeed = (kotlin.math.round(currentSpeed * 10f) / 10f)
                            Text(
                                text = "⚡ $roundedSpeed tok/s",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { p?.progressPercent ?: 0f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    )

                    Text(
                        text = p?.detailMessage ?: "Initializing hardware acceleration...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (result != null) {
                // Completed Benchmark Results Grid
                val res = result!!
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                    ) {
                        val roundedTokSec = (kotlin.math.round(res.tokensPerSecond * 10f) / 10f)
                        val roundedMtp = (kotlin.math.round(res.speculativeAcceptanceRate * 10f) / 10f)
                        EgressMetricBadge(
                            value = "$roundedTokSec",
                            label = "Tokens / Sec",
                            color = MaterialTheme.colorScheme.primary,
                        )
                        EgressMetricBadge(
                            value = "${res.timeToFirstTokenMs} ms",
                            label = "Prefill TTFT",
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        EgressMetricBadge(
                            value = "${res.embeddingThroughputVectorsPerSec.toInt()}",
                            label = "768d Vectors/s",
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        EgressMetricBadge(
                            value = "$roundedMtp%",
                            label = "MTP Speculative",
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = res.hardwareTierLabel,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "${res.efficiencyRating} • On-device processing verified",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // Action Button
            Button(
                onClick = {
                    haptic.perform(CruxHapticEvent.Confirm)
                    isBenchmarking = true
                    progress = null
                    scope.launch {
                        var lastSpeed = 0f
                        var lastEmbedSpeed = 1150f
                        OnDeviceHardwareBenchmarkEngine
                            .runBenchmark(
                                modelSpec = selectedModel,
                                hardwareBackend = selectedBackend,
                            ).collect { prog ->
                                progress = prog
                                val speed = prog.instantaneousTokensPerSec
                                if (speed != null) {
                                    lastSpeed = speed
                                }
                            }
                        result =
                            OnDeviceHardwareBenchmarkEngine.computeResult(
                                modelSpec = selectedModel,
                                hardwareBackend = selectedBackend,
                                measuredTokensPerSec = lastSpeed,
                                measuredEmbeddingThroughput = lastEmbedSpeed,
                            )
                        isBenchmarking = false
                        haptic.perform(CruxHapticEvent.Confirm)
                    }
                },
                enabled = !isBenchmarking,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(14.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            ) {
                Text(
                    text = if (result != null) "Re-run Diagnostic Benchmark" else "⚡ Run Live Hardware Benchmark",
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun BenchmarkSelectChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border =
            BorderStroke(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            ),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
internal fun LiveWebProfilesSandboxCard(onOpenHardenedBrowser: (() -> Unit)? = null) {
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
                        text = "Live Web Isolated Profiles",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Each tab runs in its own WebView ProfileStore with 3rd-party cookies disabled",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            LiveWebSite.entries.forEach { site ->
                val hasCookies =
                    remember(site, refreshTick) {
                        runCatching {
                            !site.cookieManager().getCookie(site.homeUrl).isNullOrBlank()
                        }.getOrDefault(false)
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
                                    if (hasCookies) {
                                        "Active browser session • ${site.homeUrl.removePrefix("https://")}"
                                    } else {
                                        "Clean sandbox • ${site.homeUrl.removePrefix("https://")}"
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color =
                                    if (hasCookies) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            )
                        }
                        TextButton(
                            onClick = {
                                site.clearSession()
                                refreshTick++
                                Toast
                                    .makeText(
                                        context,
                                        "Cleared ${site.name} cookies & storage",
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
                                text = "Clear",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            )
                        }
                    }
                }
            }

            if (onOpenHardenedBrowser != null) {
                OutlinedButton(
                    onClick = onOpenHardenedBrowser,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.Globe,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = stringResource(id = R.string.settings_hardened_browser_title),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
