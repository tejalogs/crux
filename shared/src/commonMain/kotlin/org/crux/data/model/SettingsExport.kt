package org.crux.data.model

import kotlinx.serialization.Serializable
import org.crux.data.datastore.model.AppSettings
import org.crux.data.model.appearance.AppearanceBag
import org.crux.data.model.tab.TabSettingsV2

@Serializable
internal data class SettingsExport(
    val appearanceBag: AppearanceBag,
    val appSettings: AppSettings,
    val tabSettingsV2: TabSettingsV2,
)

@Serializable
internal data class LegacySettingsExport(
    val appearanceBag: AppearanceBag,
    val appSettings: AppSettings,
    val tabSettings: TabSettings,
)

@Serializable
internal data class LegacyAppearanceSettingsExport(
    val appearanceSettings: AppearanceSettings,
    val appSettings: AppSettings,
    val tabSettingsV2: TabSettingsV2,
)

@Serializable
internal data class LegacyAppearanceSettingsAndTabsExport(
    val appearanceSettings: AppearanceSettings,
    val appSettings: AppSettings,
    val tabSettings: TabSettings,
)
