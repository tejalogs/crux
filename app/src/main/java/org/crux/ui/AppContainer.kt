package org.crux.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import moe.tlaster.precompose.molecule.producePresenter
import org.crux.R
import org.crux.data.datastore.model.AppSettings
import org.crux.data.model.appearance.GlobalAppearance
import org.crux.data.model.appearance.TimelineAppearance
import org.crux.ui.common.AppUriHandler
import org.crux.ui.component.LocalAppSettings
import org.crux.ui.component.LocalGlobalAppearance
import org.crux.ui.component.LocalTimelineAppearance
import org.crux.ui.model.takeSuccessOr
import org.crux.ui.presenter.EnvironmentSettingsPresenter
import org.crux.ui.presenter.invoke
import org.crux.ui.screen.home.HomeScreen
import org.crux.ui.theme.ApplyWindowGrayscale
import org.crux.ui.theme.CruxTheme

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
            AppUriHandler(originalUriHandler) {
                Toast.makeText(context, R.string.unable_to_open_link, Toast.LENGTH_SHORT).show()
            }
        }

    val appSettings = state.appSettings.takeSuccessOr(AppSettings(""))
    val openAIConfig = appSettings.aiConfig.type as? AppSettings.AiConfig.Type.OpenAI
    CompositionLocalProvider(
        LocalAppSettings provides appSettings,
        LocalUriHandler provides uriHandler,
        LocalGlobalAppearance provides globalAppearance,
        LocalTimelineAppearance provides
            remember(globalAppearance, timelineAppearance, appSettings.translateConfig, appSettings.aiConfig) {
                timelineAppearance.copy(
                    aiConfig =
                        TimelineAppearance.AiConfig(
                            translation = true,
                            tldr = appSettings.aiConfig.tldr,
                            agent =
                                appSettings.aiConfig.agent &&
                                    (
                                        appSettings.aiConfig.type is AppSettings.AiConfig.Type.OnDevice ||
                                            !openAIConfig?.model.isNullOrBlank()
                                    ),
                            showOriginalWithTranslation =
                                appSettings.translateConfig.showOriginalWithTranslation,
                        ),
                )
            },
    ) {
        // Crux calm mode: grays out everything drawn in this window, live site WebViews included.
        ApplyWindowGrayscale(enabled = globalAppearance.grayscale, warm = globalAppearance.paperTint)
        content()
    }
}
