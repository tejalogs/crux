package org.crux.ui.presenter

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import org.crux.common.JSON
import org.crux.data.network.ai.withLocalAiOnly
import org.crux.data.readinglist.ReadingListRepository
import org.crux.data.repository.SettingsRepository
import org.crux.di.koinInject
import org.crux.rss.RssRepository

public class ImportDataPresenter(
    private val json: String,
) : PresenterBase<ImportDataPresenter.State>() {
    private val context: Context by koinInject()
    private val settingsRepository: SettingsRepository by koinInject()
    private val readingListRepository: ReadingListRepository by koinInject()

    @Immutable
    public interface State {
        public suspend fun import()
    }

    @Composable
    override fun body(): State =
        object : State {
            override suspend fun import() {
                val payload = JSON.decodeFromString(CruxBackupPayload.serializer(), json)
                payload.appSettings?.let { imported ->
                    settingsRepository.updateAppSettings {
                        imported.copy(aiConfig = imported.aiConfig.withLocalAiOnly())
                    }
                }
                payload.appearanceBag?.let { bag ->
                    settingsRepository.replaceAppearance(bag)
                }
                payload.tabSettingsV2?.let { tabs ->
                    settingsRepository.updateTabSettingsV2 { tabs }
                }
                payload.opmlXml?.takeIf { it.isNotBlank() }?.let { opml ->
                    RssRepository.get(context).importOpml(opml)
                }
                payload.bookmarkFolders.forEach { folder ->
                    readingListRepository.createFolder(folder.name)
                }
                payload.readingList.forEach { item ->
                    readingListRepository.save(item)
                }
            }
        }
}
