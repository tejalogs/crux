package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.FlareScrollBar
import dev.dimension.flare.ui.component.LocalAppSettings
import dev.dimension.flare.ui.model.isSuccess
import dev.dimension.flare.ui.model.takeSuccessOr
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.login.ReloginTarget
import dev.dimension.flare.ui.presenter.settings.AiAgentEnabledPresenter
import dev.dimension.flare.ui.presenter.settings.LinkOpenDefaultsPresenter
import dev.dimension.flare.ui.presenter.settings.MxgaSettingsPresenter
import dev.dimension.flare.ui.theme.LocalComposeWindow
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.composefluent.ExperimentalFluentApi
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.koin.compose.koinInject
import java.io.File

@OptIn(ExperimentalFluentApi::class)
@Composable
internal fun SettingsScreen(
    toLogin: () -> Unit,
    toRelogin: (ReloginTarget) -> Unit,
    toDraftBox: () -> Unit,
    toLocalCache: () -> Unit,
    toAgentHistory: () -> Unit,
    toAppLog: () -> Unit,
    toRSSManagement: () -> Unit,
    toNostrRelays: (MicroBlogKey) -> Unit,
    toPostActionLayout: () -> Unit,
    toPrivacyTrafficLog: () -> Unit = {},
    toPrivacyBlocklist: () -> Unit = {},
) {
    val window = LocalComposeWindow.current
    val state by producePresenter {
        presenter(
            onExportFilePicker = {
                java.awt
                    .FileDialog(window, "Export Data", java.awt.FileDialog.SAVE)
                    .apply {
                        file = "flare_data_export.json"
                        isVisible = true
                    }.let {
                        if (it.directory != null && it.file != null) {
                            java.io.File(it.directory, it.file)
                        } else {
                            null
                        }
                    }
            },
            onImportFilePicker = {
                java.awt
                    .FileDialog(window, "Import Data", java.awt.FileDialog.LOAD)
                    .apply {
                        isVisible = true
                    }.let {
                        if (it.directory != null && it.file != null) {
                            java.io.File(it.directory, it.file)
                        } else {
                            null
                        }
                    }
            },
        )
    }
    val linkOpenDefaultsState by producePresenter("link_open_defaults_settings") {
        remember { LinkOpenDefaultsPresenter() }.invoke()
    }
    val mxgaState by producePresenter("mxga_settings") {
        remember { MxgaSettingsPresenter() }.invoke()
    }
    val linkOpenTargets = linkOpenDefaultsState.targets.takeSuccessOr(persistentListOf())
    val appSettings = LocalAppSettings.current

    val scrollState = rememberScrollState()
    FlareScrollBar(
        state = scrollState,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(LocalWindowPadding.current)
                    .padding(horizontal = screenHorizontalPadding),
        ) {
            SettingsAccountsSection(
                accountState = state.accountState,
                toLogin = toLogin,
                toRelogin = toRelogin,
                toNostrRelays = toNostrRelays,
            )

            SettingsAppearanceSection(
                appearanceState = state.appearanceState,
                updateAppSettings = state::updateAppSettings,
                toPostActionLayout = toPostActionLayout,
            )

            SettingsBehaviorSection(
                appSettings = appSettings,
                updateAppSettings = state::updateAppSettings,
                linkOpenTargets = linkOpenTargets,
                linkOpenDefaultsState = linkOpenDefaultsState,
                mxgaState = mxgaState,
            )

            SettingsStorageSection(
                storageState = state.storageState,
                isAccountActive = state.accountState.activeAccount.isSuccess,
                isAiAgentEnabled = state.aiAgentEnabledState.enabled,
                toRSSManagement = toRSSManagement,
                toLocalCache = toLocalCache,
                toAgentHistory = toAgentHistory,
                toDraftBox = toDraftBox,
                toAppLog = toAppLog,
            )

            SettingsPrivacySection(
                toPrivacyTrafficLog = toPrivacyTrafficLog,
                toPrivacyBlocklist = toPrivacyBlocklist,
            )

            SettingsServicesSection(
                aiConfigState = state.aiConfigState,
            )

            SettingsAboutSection(
                expanded = state.aboutExpanded,
                onExpandedChanged = { state.setAboutExpanded(it) },
            )
        }
    }
}

@Composable
private fun presenter(
    onExportFilePicker: () -> File?,
    onImportFilePicker: () -> File?,
) = run {
    val scope = rememberCoroutineScope()
    val settingsRepository = koinInject<SettingsRepository>()
    val accountState = accountsPresenter()
    val appearanceState = appearancePresenter()
    val storageState = storagePresenter(onExportFilePicker, onImportFilePicker)
    val aiConfigState = aiConfigPresenter()
    val aiAgentEnabledState = remember { AiAgentEnabledPresenter() }.invoke()
    var aboutExpanded by remember { mutableStateOf(false) }

    object {
        val accountState = accountState
        val appearanceState = appearanceState
        val aiConfigState = aiConfigState
        val aiAgentEnabledState = aiAgentEnabledState
        val storageState = storageState
        val aboutExpanded = aboutExpanded

        fun setAboutExpanded(value: Boolean) {
            aboutExpanded = value
        }

        fun updateAppSettings(block: AppSettings.() -> AppSettings) {
            scope.launch {
                settingsRepository.updateAppSettings(block)
            }
        }
    }
}

