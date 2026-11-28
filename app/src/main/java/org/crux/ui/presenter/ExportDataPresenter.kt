package org.crux.ui.presenter

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import org.crux.common.JSON
import org.crux.data.datastore.model.AppSettings
import org.crux.data.model.appearance.AppearanceBag
import org.crux.data.model.tab.TabSettingsV2
import org.crux.data.readinglist.BookmarkFolder
import org.crux.data.readinglist.ReadingListItem
import org.crux.data.readinglist.ReadingListRepository
import org.crux.data.repository.SettingsRepository
import org.crux.di.koinInject
import org.crux.rss.RssRepository

@Serializable
internal data class CruxBackupPayload(
    val version: Int = 2,
    val appSettings: AppSettings? = null,
    val appearanceBag: AppearanceBag? = null,
    val tabSettingsV2: TabSettingsV2? = null,
    val opmlXml: String? = null,
    val readingList: List<ReadingListItem> = emptyList(),
    val bookmarkFolders: List<BookmarkFolder> = emptyList(),
)

public class ExportDataPresenter : PresenterBase<ExportDataPresenter.State>() {
    private val context: Context by koinInject()
    private val settingsRepository: SettingsRepository by koinInject()
    private val readingListRepository: ReadingListRepository by koinInject()

    @Immutable
    public interface State {
        public suspend fun export(): String
    }

    @Composable
    override fun body(): State =
        object : State {
            override suspend fun export(): String {
                val appSettings = settingsRepository.appSettings.first()
                val appearance = settingsRepository.appearanceBag.first()
                val tabs = settingsRepository.tabSettingsV2.first()
                val opml = RssRepository.get(context).exportOpml()
                val payload =
                    CruxBackupPayload(
                        appSettings = appSettings,
                        appearanceBag = appearance,
                        tabSettingsV2 = tabs,
                        opmlXml = opml,
                        readingList = readingListRepository.items.value,
                        bookmarkFolders = readingListRepository.folders.value,
                    )
                return JSON.encodeToString(CruxBackupPayload.serializer(), payload)
            }
        }
}
