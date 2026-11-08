package org.crux.ui.component

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import org.crux.data.datastore.model.AppSettings
import org.crux.data.model.appearance.GlobalAppearance
import org.crux.data.model.appearance.TimelineAppearance

public val LocalGlobalAppearance: ProvidableCompositionLocal<GlobalAppearance> =
    staticCompositionLocalOf {
        error("No GlobalAppearance provided")
    }

public val LocalTimelineAppearance: ProvidableCompositionLocal<TimelineAppearance> =
    staticCompositionLocalOf {
        error("No TimelineAppearance provided")
    }

public val LocalAppSettings: ProvidableCompositionLocal<AppSettings> =
    staticCompositionLocalOf {
        AppSettings(version = "")
    }

/**
 * Asks the app to run [onAccepted] once the person has accepted where translated text is sent.
 * It runs at once when that was already accepted or nothing leaves the device. Null means no
 * app-level check, so translation starts directly.
 */
public val LocalTranslationDisclosure: ProvidableCompositionLocal<((onAccepted: () -> Unit) -> Unit)?> =
    staticCompositionLocalOf { null }
