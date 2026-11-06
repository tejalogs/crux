package org.crux.ui.presenter

import androidx.compose.runtime.Composable
import kotlinx.serialization.json.Json
import org.crux.common.encodeJson
import org.crux.data.model.DataExport
import org.crux.ui.presenter.settings.ExportAppDatabasePresenter

public class ExportDataPresenter : PresenterBase<ExportState>() {
    private val exportAppDatabasePresenter = ExportAppDatabasePresenter()
    private val exportSettingsPresenter = ExportSettingsPresenter()
    private val json = Json { ignoreUnknownKeys = true }

    @Composable
    override fun body(): ExportState =
        object : ExportState {
            override suspend fun export(): String = this@ExportDataPresenter.export()
        }

    public suspend fun export(): String {
        val appDatabaseJson = exportAppDatabasePresenter.export()
        val settingsJson = exportSettingsPresenter.export()

        val export =
            DataExport(
                appDatabase = json.parseToJsonElement(appDatabaseJson),
                settings = json.parseToJsonElement(settingsJson),
            )
        return export.encodeJson(DataExport.serializer())
    }
}
