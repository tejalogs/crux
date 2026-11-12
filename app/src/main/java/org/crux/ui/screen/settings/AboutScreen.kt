package org.crux.ui.screen.settings

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.brands.Github
import compose.icons.fontawesomeicons.solid.Book
import compose.icons.fontawesomeicons.solid.Bug
import compose.icons.fontawesomeicons.solid.FileContract
import compose.icons.fontawesomeicons.solid.Lock
import compose.icons.fontawesomeicons.solid.RotateRight
import kotlinx.coroutines.launch
import org.crux.BuildConfig
import org.crux.CruxLinks
import org.crux.R
import org.crux.ui.component.BackButton
import org.crux.ui.component.CruxLargeFlexibleTopAppBar
import org.crux.ui.component.CruxScaffold
import org.crux.ui.component.listCard
import org.crux.ui.theme.segmentedShapes2
import org.crux.update.AppReleaseInfo
import org.crux.update.AppUpdateManager
import org.crux.update.AppUpdatePrefs
import org.crux.update.UpdateCheckResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AboutScreen(
    onBack: () -> Unit,
    toOpenSourceLicenses: () -> Unit,
) {
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val updatePrefs = remember(context) { AppUpdatePrefs.get(context) }
    val autoCheckUpdates by updatePrefs.autoCheckEnabled.collectAsState()
    var isCheckingUpdates by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<AppReleaseInfo?>(null) }
    var updateStatusSupporting by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    CruxScaffold(
        topBar = {
            CruxLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_about_title))
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
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(it),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_crux_mark),
                contentDescription = stringResource(id = R.string.app_name),
                modifier =
                    Modifier
                        .height(64.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(id = R.string.settings_about_description),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                modifier =
                    Modifier
                        .padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(id = R.string.settings_about_credit),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(16.dp))
            val rows =
                listOf(
                    AboutRow(
                        icon = FontAwesomeIcons.Solid.RotateRight,
                        title = R.string.settings_check_for_updates,
                        supporting = updateStatusSupporting ?: "v${BuildConfig.VERSION_NAME}",
                        onClick = {
                            if (!isCheckingUpdates) {
                                isCheckingUpdates = true
                                updateStatusSupporting = context.getString(R.string.settings_checking_for_updates)
                                coroutineScope.launch {
                                    val result = AppUpdateManager.checkForUpdates(BuildConfig.VERSION_NAME)
                                    isCheckingUpdates = false
                                    when (result) {
                                        is UpdateCheckResult.UpToDate -> {
                                            updatePrefs.recordCheckCompleted()
                                            val msg = context.getString(R.string.settings_up_to_date, BuildConfig.VERSION_NAME)
                                            updateStatusSupporting = msg
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }

                                        is UpdateCheckResult.UpdateAvailable -> {
                                            updatePrefs.recordCheckCompleted()
                                            updatePrefs.clearSkippedVersion()
                                            updateStatusSupporting =
                                                context.getString(
                                                    R.string.settings_update_available,
                                                    result.info.tagName,
                                                )
                                            updateInfo = result.info
                                        }

                                        is UpdateCheckResult.Error -> {
                                            val err = context.getString(R.string.settings_update_failed)
                                            updateStatusSupporting = err
                                            Toast.makeText(context, "$err: ${result.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }
                        },
                    ),
                    AboutRow(
                        icon = FontAwesomeIcons.Solid.RotateRight,
                        title = R.string.settings_auto_check_updates,
                        supportingRes = R.string.settings_auto_check_updates_description,
                        trailing = {
                            Switch(
                                checked = autoCheckUpdates,
                                onCheckedChange = { updatePrefs.setAutoCheckEnabled(it) },
                            )
                        },
                        onClick = { updatePrefs.setAutoCheckEnabled(!autoCheckUpdates) },
                    ),
                    AboutRow(
                        icon = FontAwesomeIcons.Brands.Github,
                        title = R.string.settings_about_source_code,
                        supporting = CruxLinks.REPOSITORY,
                        onClick = { uriHandler.openUri(CruxLinks.REPOSITORY) },
                    ),
                    AboutRow(
                        icon = FontAwesomeIcons.Brands.Github,
                        title = R.string.settings_about_upstream,
                        supporting = CruxLinks.UPSTREAM,
                        onClick = { uriHandler.openUri(CruxLinks.UPSTREAM) },
                    ),
                    AboutRow(
                        icon = FontAwesomeIcons.Solid.FileContract,
                        title = R.string.settings_about_license,
                        supportingRes = R.string.settings_about_license_description,
                        onClick = { uriHandler.openUri(CruxLinks.LICENSE) },
                    ),
                    AboutRow(
                        icon = FontAwesomeIcons.Solid.Book,
                        title = R.string.settings_about_open_source_licenses,
                        supportingRes = R.string.settings_about_open_source_licenses_description,
                        onClick = toOpenSourceLicenses,
                    ),
                    AboutRow(
                        icon = FontAwesomeIcons.Solid.Lock,
                        title = R.string.settings_privacy_policy,
                        supportingRes = R.string.settings_about_privacy_policy_description,
                        onClick = { uriHandler.openUri(CruxLinks.PRIVACY) },
                    ),
                    AboutRow(
                        icon = FontAwesomeIcons.Solid.Bug,
                        title = R.string.settings_about_report_issue,
                        supporting = CruxLinks.ISSUES,
                        onClick = { uriHandler.openUri(CruxLinks.ISSUES) },
                    ),
                )
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                rows.forEachIndexed { index, row ->
                    SegmentedListItem(
                        content = {
                            Text(text = stringResource(id = row.title))
                        },
                        supportingContent = {
                            Text(text = row.supporting ?: stringResource(id = requireNotNull(row.supportingRes)))
                        },
                        onClick = row.onClick,
                        shapes = ListItemDefaults.segmentedShapes2(index, rows.size),
                        leadingContent = {
                            Icon(
                                imageVector = row.icon,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        trailingContent = row.trailing,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    val currentUpdate = updateInfo
    if (currentUpdate != null) {
        AppUpdateBottomSheet(
            sheetState = sheetState,
            updateInfo = currentUpdate,
            onDismissRequest = { updateInfo = null },
        )
    }
}

private data class AboutRow(
    val icon: ImageVector,
    @StringRes val title: Int,
    val supporting: String? = null,
    @StringRes val supportingRes: Int? = null,
    val trailing: (@Composable () -> Unit)? = null,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ColorSpaceScreen(onBack: () -> Unit) {
    val colors =
        mapOf(
            "primary" to MaterialTheme.colorScheme.primary,
            "onPrimary" to MaterialTheme.colorScheme.onPrimary,
            "primaryContainer" to MaterialTheme.colorScheme.primaryContainer,
            "onPrimaryContainer" to MaterialTheme.colorScheme.onPrimaryContainer,
            "inversePrimary" to MaterialTheme.colorScheme.inversePrimary,
            "secondary" to MaterialTheme.colorScheme.secondary,
            "onSecondary" to MaterialTheme.colorScheme.onSecondary,
            "secondaryContainer" to MaterialTheme.colorScheme.secondaryContainer,
            "onSecondaryContainer" to MaterialTheme.colorScheme.onSecondaryContainer,
            "tertiary" to MaterialTheme.colorScheme.tertiary,
            "onTertiary" to MaterialTheme.colorScheme.onTertiary,
            "tertiaryContainer" to MaterialTheme.colorScheme.tertiaryContainer,
            "onTertiaryContainer" to MaterialTheme.colorScheme.onTertiaryContainer,
            "background" to MaterialTheme.colorScheme.background,
            "onBackground" to MaterialTheme.colorScheme.onBackground,
            "surface" to MaterialTheme.colorScheme.surface,
            "onSurface" to MaterialTheme.colorScheme.onSurface,
            "surfaceVariant" to MaterialTheme.colorScheme.surfaceVariant,
            "onSurfaceVariant" to MaterialTheme.colorScheme.onSurfaceVariant,
            "surfaceTint" to MaterialTheme.colorScheme.surfaceTint,
            "inverseSurface" to MaterialTheme.colorScheme.inverseSurface,
            "inverseOnSurface" to MaterialTheme.colorScheme.inverseOnSurface,
            "error" to MaterialTheme.colorScheme.error,
            "onError" to MaterialTheme.colorScheme.onError,
            "errorContainer" to MaterialTheme.colorScheme.errorContainer,
            "onErrorContainer" to MaterialTheme.colorScheme.onErrorContainer,
            "outline" to MaterialTheme.colorScheme.outline,
            "outlineVariant" to MaterialTheme.colorScheme.outlineVariant,
            "scrim" to MaterialTheme.colorScheme.scrim,
            "surfaceBright" to MaterialTheme.colorScheme.surfaceBright,
            "surfaceDim" to MaterialTheme.colorScheme.surfaceDim,
            "surfaceContainer" to MaterialTheme.colorScheme.surfaceContainer,
            "surfaceContainerHigh" to MaterialTheme.colorScheme.surfaceContainerHigh,
            "surfaceContainerHighest" to MaterialTheme.colorScheme.surfaceContainerHighest,
            "surfaceContainerLow" to MaterialTheme.colorScheme.surfaceContainerLow,
            "surfaceContainerLowest" to MaterialTheme.colorScheme.surfaceContainerLowest,
            "primaryFixed" to MaterialTheme.colorScheme.primaryFixed,
            "primaryFixedDim" to MaterialTheme.colorScheme.primaryFixedDim,
            "onPrimaryFixed" to MaterialTheme.colorScheme.onPrimaryFixed,
            "onPrimaryFixedVariant" to MaterialTheme.colorScheme.onPrimaryFixedVariant,
            "secondaryFixed" to MaterialTheme.colorScheme.secondaryFixed,
            "secondaryFixedDim" to MaterialTheme.colorScheme.secondaryFixedDim,
            "onSecondaryFixed" to MaterialTheme.colorScheme.onSecondaryFixed,
            "onSecondaryFixedVariant" to MaterialTheme.colorScheme.onSecondaryFixedVariant,
            "tertiaryFixed" to MaterialTheme.colorScheme.tertiaryFixed,
            "tertiaryFixedDim" to MaterialTheme.colorScheme.tertiaryFixedDim,
            "onTertiaryFixed" to MaterialTheme.colorScheme.onTertiaryFixed,
            "onTertiaryFixedVariant" to MaterialTheme.colorScheme.onTertiaryFixedVariant,
        )
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    CruxScaffold(
        topBar = {
            CruxLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_color_space_title))
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
                    .padding(horizontal = 16.dp),
            verticalArrangement =
                androidx.compose.foundation.layout.Arrangement
                    .spacedBy(2.dp),
        ) {
            colors.onEachIndexed { index, (name, color) ->
                ListItem(
                    modifier =
                        Modifier
                            .listCard(
                                index = index,
                                totalCount = colors.size, // Adjust this count base on the number of items
                            ),
                    overlineContent = {
                        Text(
                            text = "#${color.toArgb().toHexString().uppercase()}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    supportingContent = {
                        Box(
                            modifier =
                                Modifier
                                    .background(color)
                                    .fillMaxWidth()
                                    .height(36.dp),
                        )
                    },
                    elevation = ListItemDefaults.elevation(),
                    content = { Text(text = name) },
                )
            }
        }
    }
}
