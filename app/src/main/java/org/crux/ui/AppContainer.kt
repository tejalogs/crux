package org.crux.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.crux.R
import org.crux.data.datastore.model.AppSettings
import org.crux.data.model.appearance.GlobalAppearance
import org.crux.data.model.appearance.TimelineAppearance
import org.crux.data.repository.SettingsRepository
import org.crux.data.translation.TranslationDisclosure
import org.crux.ui.common.AppUriHandler
import org.crux.ui.component.LocalAppSettings
import org.crux.ui.component.LocalGlobalAppearance
import org.crux.ui.component.LocalTimelineAppearance
import org.crux.ui.component.LocalTranslationDisclosure
import org.crux.ui.model.takeSuccessOr
import org.crux.ui.presenter.EnvironmentSettingsPresenter
import org.crux.ui.presenter.invoke
import org.crux.ui.screen.home.HomeScreen
import org.crux.ui.screen.home.LiveWebPrivacyCounters
import org.crux.ui.screen.rss.TranslationDisclosureDialog
import org.crux.ui.theme.ApplyWindowGrayscale
import org.crux.ui.theme.CruxTheme
import org.koin.compose.koinInject

@Composable
fun AppContainer(afterInit: () -> Unit) {
    CruxApp {
        CruxTheme {
            HomeScreen(
                afterInit = afterInit,
            )
        }
    }
}

@Composable
fun CruxApp(content: @Composable () -> Unit) {
    val state by producePresenter("env") {
        remember { EnvironmentSettingsPresenter() }.invoke()
    }
    val globalAppearance = state.globalAppearance.takeSuccessOr(GlobalAppearance.Default)
    val timelineAppearance = state.timelineAppearance.takeSuccessOr(TimelineAppearance.Default)
    val originalUriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val uriHandler =
        remember(context, originalUriHandler) {
            AppUriHandler(
                fallback = originalUriHandler,
                onFailure = { Toast.makeText(context, R.string.unable_to_open_link, Toast.LENGTH_SHORT).show() },
                onLinkCleaned = { original, cleaned ->
                    LiveWebPrivacyCounters.get(context).recordLinkCleaned(original, cleaned)
                },
            )
        }

    val appSettings = state.appSettings.takeSuccessOr(AppSettings(""))
    val settingsRepository = koinInject<SettingsRepository>()
    val disclosureScope = rememberCoroutineScope()
    var pendingDisclosure by remember { mutableStateOf<PendingTranslationDisclosure?>(null) }
    val requestTranslation: (() -> Unit) -> Unit =
        remember(appSettings) {
            { onAccepted ->
                val destination = TranslationDisclosure.destination(appSettings)
                if (destination != null && TranslationDisclosure.isRequired(appSettings)) {
                    pendingDisclosure = PendingTranslationDisclosure(destination, onAccepted)
                } else {
                    onAccepted()
                }
            }
        }
    CompositionLocalProvider(
        LocalAppSettings provides appSettings,
        LocalTranslationDisclosure provides requestTranslation,
        LocalUriHandler provides uriHandler,
        LocalGlobalAppearance provides globalAppearance,
        LocalTimelineAppearance provides
            remember(globalAppearance, timelineAppearance, appSettings.translateConfig, appSettings.aiConfig) {
                timelineAppearance.copy(
                    aiConfig =
                        TimelineAppearance.AiConfig(
                            translation = true,
                            tldr = appSettings.aiConfig.tldr,
                            agent = appSettings.aiConfig.agent,
                            showOriginalWithTranslation =
                                appSettings.translateConfig.showOriginalWithTranslation,
                        ),
                )
            },
    ) {
        // Crux calm mode: grays out everything drawn in this window, live site WebViews included.
        ApplyWindowGrayscale(enabled = globalAppearance.grayscale, warm = globalAppearance.paperTint)
        content()
        pendingDisclosure?.let { pending ->
            CruxTheme {
                TranslationDisclosureDialog(
                    destination = pending.destination,
                    onContinue = {
                        pendingDisclosure = null
                        disclosureScope.launch {
                            settingsRepository.updateAppSettings {
                                copy(translateConfig = TranslationDisclosure.acknowledge(translateConfig, pending.destination))
                            }
                            pending.onAccepted()
                        }
                    },
                    onCancel = { pendingDisclosure = null },
                    onChangeProvider = null,
                )
            }
        }
    }
}

/** A translation waiting for the person to accept where its text is sent. */
private class PendingTranslationDisclosure(
    val destination: TranslationDisclosure.Destination,
    val onAccepted: () -> Unit,
)
