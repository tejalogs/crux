package org.crux.ui.presenter

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.first
import org.crux.common.encodeJson
import org.crux.data.model.SettingsExport
import org.crux.data.repository.SettingsRepository
import org.crux.di.koinInject

public class ExportSettingsPresenter : PresenterBase<ExportState>() {
    private val settingsRepository: SettingsRepository by koinInject()

    @Composable
    override fun body(): ExportState =
        object : ExportState {
            override suspend fun export(): String = this@ExportSettingsPresenter.export()
        }

    public suspend fun export(): String {
        val export =
            SettingsExport(
                appearanceBag = settingsRepository.appearanceBag.first(),
                appSettings = settingsRepository.appSettings.first(),
                tabSettingsV2 = settingsRepository.tabSettingsV2.first(),
            )
        return export.encodeJson(SettingsExport.serializer())
    }
}
