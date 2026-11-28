package org.crux.ui.screen.settings

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.DialogSceneStrategy
import org.crux.ui.route.Route
import org.crux.ui.route.reloginRoute

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal fun EntryProviderScope<NavKey>.settingsSelectEntryBuilder(
    navigate: (Route) -> Unit,
    onBack: () -> Unit
) {
    entry<Route.Settings.Main>(
        metadata = ListDetailSceneStrategy.listPane(
            sceneKey = "Settings",
            detailPlaceholder = {
                SettingsDetailPlaceholder()
            }
        )
    ) {
        SettingsScreen(
            toAccounts = {
                navigate(Route.Settings.Accounts)
            },
            toAppearanceTheme = {
                navigate(Route.Settings.AppearanceTheme)
            },
            toBehavior = {
                navigate(Route.Settings.Behavior)
            },
            toPrivacy = {
                navigate(Route.Settings.Privacy)
            },
            toStorage = {
                navigate(Route.Settings.Storage)
            },
            toAbout = {
                navigate(Route.Settings.About)
            },
            toAiConfig = {
                navigate(Route.Settings.AiConfig)
            },
            toColorSpace = {
                navigate(Route.Settings.ColorSpace)
            },
            onBack = onBack,
        )
    }

    entry<Route.Settings.Accounts>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "Settings"
        )
    ) {
        AccountsScreen(
            onBack = onBack,
            navigate = navigate,
        )
    }

    entry<Route.Settings.AppearanceTheme>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "Settings"
        )
    ) {
        AppearanceThemeScreen(
            onBack = onBack,
            toColorPicker = {
                navigate(Route.Settings.ColorPicker)
            },
            initialTab = AppearanceStudioTab.Theme,
        )
    }

    entry<Route.Settings.AppearanceLayout>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "Settings"
        )
    ) {
        AppearanceThemeScreen(
            onBack = onBack,
            toColorPicker = {
                navigate(Route.Settings.ColorPicker)
            },
            initialTab = AppearanceStudioTab.Layout,
        )
    }

    entry<Route.Settings.AppearanceDisplay>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "Settings"
        )
    ) {
        AppearanceThemeScreen(
            onBack = onBack,
            toColorPicker = {
                navigate(Route.Settings.ColorPicker)
            },
            initialTab = AppearanceStudioTab.Display,
        )
    }

    entry<Route.Settings.AppearanceMedia>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "Settings"
        )
    ) {
        AppearanceThemeScreen(
            onBack = onBack,
            toColorPicker = {
                navigate(Route.Settings.ColorPicker)
            },
            initialTab = AppearanceStudioTab.Media,
        )
    }

    entry<Route.Settings.Behavior>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "Settings"
        )
    ) {
        BehaviorScreen(
            toLinkOpenDefaults = {
                navigate(Route.Settings.LinkOpenDefaults)
            },
            toHardenedBrowser = {
                navigate(Route.Settings.HardenedBrowser)
            },
            onBack = onBack,
        )
    }

    entry<Route.Settings.Privacy>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "Settings"
        )
    ) {
        PrivacySettingsScreen(
            toTrafficLog = {
                navigate(Route.Settings.PrivacyTrafficLog)
            },
            toBlocklist = {
                navigate(Route.Settings.PrivacyBlocklist)
            },
            toPrivacyTweaks = {
                navigate(Route.Settings.PrivacyTweaks)
            },
            toPrivacyCheckup = {
                navigate(Route.Settings.PrivacyCheckup)
            },
            onBack = onBack,
        )
    }

    entry<Route.Settings.PrivacyTrafficLog>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "Settings"
        )
    ) {
        PrivacyTrafficLogScreen(onBack = onBack)
    }

    entry<Route.Settings.PrivacyBlocklist>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "Settings"
        )
    ) {
        PrivacyBlocklistScreen(onBack = onBack)
    }

    entry<Route.Settings.LinkOpenDefaults>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "Settings"
        )
    ) {
        LinkOpenDefaultsScreen(
            onBack = onBack,
        )
    }

    entry<Route.Settings.Storage>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "Settings"
        )
    ) {
        StorageScreen(
            onBack = onBack,
            toAppLog = {
                navigate(Route.Settings.AppLogging)
            }
        )
    }

    entry<Route.Settings.About>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "Settings"
        )
    ) {
        AboutScreen(
            onBack = onBack,
            toOpenSourceLicenses = {
                navigate(Route.Settings.OpenSourceLicenses)
            },
        )
    }

    entry<Route.Settings.OpenSourceLicenses>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "Settings"
        )
    ) {
        OpenSourceLicensesScreen(
            onBack = onBack
        )
    }

    entry<Route.Settings.AiConfig>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "Settings"
        )
    ) {
        AiConfigScreen(
            onBack = onBack
        )
    }

    entry<Route.Settings.ColorPicker>(
        metadata = DialogSceneStrategy.dialog()
    ) {
        ColorPickerDialog(
            onBack = onBack
        )
    }

    entry<Route.Settings.AppLogging>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "Settings"
        )
    ) {
        AppLoggingScreen(
            onBack = onBack
        )
    }

    entry<Route.Settings.ColorSpace>(
        metadata = ListDetailSceneStrategy.extraPane(
            sceneKey = "Settings"
        )
    ) { args ->
        ColorSpaceScreen(
            onBack = onBack
        )
    }

    entry<Route.Settings.HardenedBrowser>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "Settings"
        )
    ) {
        HardenedBrowserScreen(
            onBack = onBack
        )
    }

    entry<Route.Settings.PrivacyTweaks>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "Settings"
        )
    ) {
        PrivacyTweaksScreen(
            onBack = onBack,
            onOpenSiteUrl = { site, url ->
                navigate(Route.LiveWeb(site = site.name, initialUrl = url))
            },
        )
    }

    entry<Route.Settings.PrivacyCheckup>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "Settings"
        )
    ) {
        PrivacyCheckupScreen(
            onBack = onBack,
            onOpenRoute = navigate,
        )
    }
}
