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
