package org.crux.ui.presenter

import androidx.compose.runtime.Composable
import org.crux.common.decodeJson
import org.crux.data.model.DataExport
import org.crux.ui.presenter.settings.ImportAppDatabasePresenter

public class ImportDataPresenter(
    private val jsonContent: String,
) : PresenterBase<ImportState>() {
    @Composable
    override fun body(): ImportState =
        object : ImportState {
            override suspend fun import() {
                this@ImportDataPresenter.import()
            }
        }

    internal suspend fun import() {
        val export = jsonContent.decodeJson(DataExport.serializer())

        val importAppDatabasePresenter = ImportAppDatabasePresenter(export.appDatabase.toString())
        importAppDatabasePresenter.import()

        val importSettingsPresenter = ImportSettingsPresenter(export.settings.toString())
        importSettingsPresenter.import()
    }
}
