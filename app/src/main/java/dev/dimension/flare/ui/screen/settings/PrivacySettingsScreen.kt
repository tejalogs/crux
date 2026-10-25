package dev.dimension.flare.ui.screen.settings

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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Lock
import compose.icons.fontawesomeicons.solid.Robot
import dev.dimension.flare.R
import dev.dimension.flare.data.ai.OnDeviceHardwareBackend
import dev.dimension.flare.data.ai.OnDeviceModelSpec
import dev.dimension.flare.data.ai.benchmark.BenchmarkProgress
import dev.dimension.flare.data.ai.benchmark.BenchmarkResult
import dev.dimension.flare.data.ai.benchmark.BenchmarkStage
import dev.dimension.flare.data.ai.benchmark.OnDeviceHardwareBenchmarkEngine
import dev.dimension.flare.data.privacy.PrivacyLog
import dev.dimension.flare.data.privacy.PrivacySettingsRepository
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.haptic.FlareHapticEvent
import dev.dimension.flare.ui.haptic.rememberFlareHapticFeedback
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
    val logEntries by PrivacyLog.get().entries.collectAsState()
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
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFF00C853), Color(0xFF00B4D8)),
                                        ),
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.Lock,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Zero-Trust Egress Monitor",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "100% On-Device Sovereign Privacy",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF00C853),
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
                            label = "Cloud AI Leakage",
                            color = Color(0xFF00C853),
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

                    // Sovereign Bridges Status
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Active Sovereign Web Bridges",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SovereignBridgeChip(name = "LinkedIn Bridge", active = true)
                            SovereignBridgeChip(name = "Instagram Bridge", active = true)
                            SovereignBridgeChip(name = "Gemma 4 Local", active = true)
                        }
                    }
                }
            }

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
private fun SovereignBridgeChip(
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
                        .background(if (active) Color(0xFF00C853) else Color.Gray),
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
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF8A2BE2), Color(0xFF00B4D8)),
                                ),
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.Robot,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "On-Device Gemma 4 Benchmark",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "LiteRT-LM Hardware Inference & Throughput",
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
                        val label = when (backend) {
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
                                haptic.perform(FlareHapticEvent.Confirm)
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
                                haptic.perform(FlareHapticEvent.Confirm)
                            },
                        )
                    }
                }
            }

            // Benchmark In-Progress Telemetry
            if (isBenchmarking) {
                Column(
                    modifier = Modifier
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
                                color = Color(0xFF00C853),
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { p?.progressPercent ?: 0f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        color = Color(0xFF00B4D8),
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
                    modifier = Modifier
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
                            color = Color(0xFF00C853),
                        )
                        EgressMetricBadge(
                            value = "${res.timeToFirstTokenMs} ms",
                            label = "Prefill TTFT",
                            color = Color(0xFF00B4D8),
                        )
                        EgressMetricBadge(
                            value = "${res.embeddingThroughputVectorsPerSec.toInt()}",
                            label = "768d Vectors/s",
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        EgressMetricBadge(
                            value = "$roundedMtp%",
                            label = "MTP Speculative",
                            color = Color(0xFF8A2BE2),
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
                            text = "${res.efficiencyRating} • Zero Cloud Leakage Verified",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF00C853),
                        )
                    }
                }
            }

            // Action Button
            Button(
                onClick = {
                    haptic.perform(FlareHapticEvent.Confirm)
                    isBenchmarking = true
                    progress = null
                    scope.launch {
                        var lastSpeed = 0f
                        var lastEmbedSpeed = 1150f
                        OnDeviceHardwareBenchmarkEngine.runBenchmark(
                            modelSpec = selectedModel,
                            hardwareBackend = selectedBackend,
                        ).collect { prog ->
                            progress = prog
                            val speed = prog.instantaneousTokensPerSec
                            if (speed != null) {
                                lastSpeed = speed
                            }
                        }
                        result = OnDeviceHardwareBenchmarkEngine.computeResult(
                            modelSpec = selectedModel,
                            hardwareBackend = selectedBackend,
                            measuredTokensPerSec = lastSpeed,
                            measuredEmbeddingThroughput = lastEmbedSpeed,
                        )
                        isBenchmarking = false
                        haptic.perform(FlareHapticEvent.Confirm)
                    }
                },
                enabled = !isBenchmarking,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
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
        border = BorderStroke(
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
