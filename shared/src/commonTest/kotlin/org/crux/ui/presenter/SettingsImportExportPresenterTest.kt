package org.crux.ui.presenter

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okio.FileSystem
import okio.Path
import org.crux.createTestFileSystem
import org.crux.createTestRootPath
import org.crux.data.datastore.AppDataStore
import org.crux.data.datastore.model.AppSettings
import org.crux.data.io.OkioFileStorage
import org.crux.data.model.AppearanceSettings
import org.crux.data.model.HomeTimelineTabItem
import org.crux.data.model.IconType
import org.crux.data.model.LegacyAppearanceSettingsAndTabsExport
import org.crux.data.model.LegacyAppearanceSettingsExport
import org.crux.data.model.LegacySettingsExport
import org.crux.data.model.Mastodon
import org.crux.data.model.SettingsExport
import org.crux.data.model.TabMetaData
import org.crux.data.model.TabSettings
import org.crux.data.model.Theme
import org.crux.data.model.TitleType
import org.crux.data.model.appearance.AppearanceBag
import org.crux.data.model.tab.GroupSource
import org.crux.data.model.tab.SYSTEM_HOME_MIXED_TIMELINE_ID
import org.crux.data.model.tab.TabSettingsV2
import org.crux.data.model.tab.TimelineFilterConfig
import org.crux.data.model.tab.TimelineMergePolicy
import org.crux.data.model.tab.TimelinePostKind
import org.crux.data.model.tab.TimelinePresentation
import org.crux.data.model.tab.TimelineResolver
import org.crux.data.model.tab.TimelineSlot
import org.crux.data.model.tab.TimelineSlotContent
import org.crux.data.model.tab.UiGroupTimelineTabItem
import org.crux.data.model.tab.toTimelineSlotOrNull
import org.crux.data.repository.SettingsRepository
import org.crux.deleteTestRootPath
import org.crux.di.startKoin
import org.crux.di.testSingle
import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import org.crux.testPlatformRuntimeData
import org.crux.ui.model.UiIcon
import org.crux.unavailableAccountService
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsImportExportPresenterTest {
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var root: Path
    private lateinit var settingsRepository: SettingsRepository

    @BeforeTest
    fun setup() {
        root = createTestRootPath()
        val fileStorage = OkioFileStorage(createTestFileSystem(), root)
        settingsRepository =
            SettingsRepository(
                fileStorage = fileStorage,
                appDataStore = AppDataStore(fileStorage),
                timelineResolver = TimelineResolver(testPlatformRuntimeData(), unavailableAccountService()),
            )
        startKoin {
            modules(
                module {
                    testSingle { settingsRepository }
                },
            )
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
        deleteTestRootPath(root)
    }

    @Test
    fun exportUsesTabSettingsV2FieldOnly() =
        runTest {
            val slot = homeSlot()
            settingsRepository.updateTabSettingsV2 {
                TabSettingsV2(homeSlots = listOf(slot))
            }

            val exported = ExportSettingsPresenter().export()
            val root = json.parseToJsonElement(exported).jsonObject

            assertTrue("tabSettingsV2" in root)
            assertFalse("tabSettings" in root)
            assertTrue("appearanceBag" in root)
            assertFalse("appearanceSettings" in root)
            assertEquals(
                listOf(slot.id),
                json
                    .decodeFromString<SettingsExport>(exported)
                    .tabSettingsV2.homeSlots
                    .map { it.id },
            )
        }

    @Test
    fun importV2SettingsReplacesHomeSlots() =
        runTest {
            val oldSlot = localSlot()
            val newSlot = homeSlot()
            settingsRepository.updateTabSettingsV2 {
                TabSettingsV2(homeSlots = listOf(oldSlot))
            }
            val exported =
                json.encodeToString(
                    SettingsExport(
                        appearanceBag = AppearanceBag(),
                        appSettings = AppSettings(version = "v2"),
                        tabSettingsV2 = TabSettingsV2(homeSlots = listOf(newSlot)),
                    ),
                )

            ImportSettingsPresenter(exported).import()

            val settings = settingsRepository.tabSettingsV2.first()
            assertEquals(listOf(newSlot.id), settings.homeSlots.map { it.id })
        }

    @Test
    fun importLegacyAppearanceSettingsConvertsToAppearanceBagInRepository() =
        runTest {
            val newSlot = homeSlot()
            val exported =
                json.encodeToString(
                    LegacyAppearanceSettingsExport(
                        appearanceSettings =
                            AppearanceSettings(
                                theme = Theme.DARK,
                                showNumbers = false,
                            ),
                        appSettings = AppSettings(version = "legacy-appearance"),
                        tabSettingsV2 = TabSettingsV2(homeSlots = listOf(newSlot)),
                    ),
                )

            ImportSettingsPresenter(exported).import()

            assertEquals(Theme.DARK, settingsRepository.globalAppearance.first().theme)
            assertEquals(false, settingsRepository.timelineAppearance.first().showNumbers)
            assertTrue(
                settingsRepository.appearanceBag
                    .first()
                    .entries
                    .isNotEmpty(),
            )
        }

    @Test
    fun importLegacyV1SettingsMigratesTabsToV2WithMixedTimelineDisabled() =
        runTest {
            val accountKey = MicroBlogKey(id = "alice", host = "example.com")
            val exported =
                json.encodeToString(
                    LegacyAppearanceSettingsAndTabsExport(
                        appearanceSettings = AppearanceSettings(),
                        appSettings = AppSettings(version = "v1"),
                        tabSettings =
                            TabSettings(
                                enableMixedTimeline = false,
                                mainTabs =
                                    listOf(
                                        HomeTimelineTabItem(AccountType.Specific(accountKey)),
                                        Mastodon.LocalTimelineTabItem(
                                            account = AccountType.Specific(accountKey),
                                            metaData = localMetaData(),
                                        ),
                                    ),
                            ),
                    ),
                )

            ImportSettingsPresenter(exported).import()

            val settings = settingsRepository.tabSettingsV2.first()
            assertEquals(
                listOf(
                    "common.home:$accountKey",
                    "mastodon.local:$accountKey",
                ),
                settings.homeSlots.map { it.id },
            )
        }

    @Test
    fun importLegacyV1SettingsMigratesTabsToV2WithMixedTimelineEnabled() =
        runTest {
            val accountKey = MicroBlogKey(id = "alice", host = "example.com")
            val exported =
                json.encodeToString(
                    LegacySettingsExport(
                        appearanceBag = AppearanceBag(),
                        appSettings = AppSettings(version = "v1"),
                        tabSettings =
                            TabSettings(
                                enableMixedTimeline = true,
                                mainTabs =
                                    listOf(
                                        HomeTimelineTabItem(AccountType.Specific(accountKey)),
                                        Mastodon.LocalTimelineTabItem(
                                            account = AccountType.Specific(accountKey),
                                            metaData = localMetaData(),
                                        ),
                                    ),
                            ),
                    ),
                )

            ImportSettingsPresenter(exported).import()

            val settings = settingsRepository.tabSettingsV2.first()
            assertEquals(
                listOf(
                    SYSTEM_HOME_MIXED_TIMELINE_ID,
                    "common.home:$accountKey",
                    "mastodon.local:$accountKey",
                ),
                settings.homeSlots.map { it.id },
            )
        }

    @Test
    fun homeTimelineTabResolvesNestedItemsByIdAndEmitsConfigUpdates() =
        runTest {
            val initialChild = homeSlot()
            val initialGroup =
                TimelineSlot(
                    id = "manual_group",
                    content =
                        TimelineSlotContent.Group(
                            children =
                                listOf(
                                    initialChild.copy(
                                        presentation =
                                            TimelinePresentation(
                                                filterConfig =
                                                    TimelineFilterConfig(
                                                        excludedKinds = listOf(TimelinePostKind.Reply),
                                                    ),
                                            ),
                                    ),
                                ),
                            source = GroupSource.Manual,
                            mergePolicy = TimelineMergePolicy.Staggered,
                        ),
                    presentation =
                        TimelinePresentation(
                            filterConfig =
                                TimelineFilterConfig(
                                    excludedKinds = listOf(TimelinePostKind.Quote),
                                ),
                        ),
                )
            settingsRepository.updateTabSettingsV2 {
                TabSettingsV2(homeSlots = listOf(initialGroup))
            }

            val groupItem = settingsRepository.homeTimelineTab(initialGroup.id).first() as? UiGroupTimelineTabItem
            val childItem = settingsRepository.homeTimelineTab(initialChild.id).first()
            assertEquals(TimelineMergePolicy.Staggered, groupItem?.mergePolicy)
            assertEquals(listOf(TimelinePostKind.Quote), groupItem?.filterConfig?.excludedKinds)
            assertEquals(listOf(TimelinePostKind.Reply), childItem?.filterConfig?.excludedKinds)

            val updatedGroup =
                initialGroup.copy(
                    content =
                        TimelineSlotContent.Group(
                            children =
                                listOf(
                                    initialChild.copy(
                                        presentation =
                                            TimelinePresentation(
                                                filterConfig =
                                                    TimelineFilterConfig(
                                                        excludedKinds = listOf(TimelinePostKind.Repost),
                                                    ),
                                            ),
                                    ),
                                ),
                            source = GroupSource.Manual,
                            mergePolicy = TimelineMergePolicy.TimePerPage,
                        ),
                    presentation =
                        TimelinePresentation(
                            filterConfig =
                                TimelineFilterConfig(
                                    excludedKinds = listOf(TimelinePostKind.Original),
                                ),
                        ),
                )
            val nextGroup = async { settingsRepository.homeTimelineTab(initialGroup.id).drop(1).first() as? UiGroupTimelineTabItem }
            val nextChild = async { settingsRepository.homeTimelineTab(initialChild.id).drop(1).first() }

            settingsRepository.updateTabSettingsV2 {
                TabSettingsV2(homeSlots = listOf(updatedGroup))
            }

            val updatedGroupItem = nextGroup.await()
            val updatedChildItem = nextChild.await()
            assertEquals(TimelineMergePolicy.TimePerPage, updatedGroupItem?.mergePolicy)
            assertEquals(listOf(TimelinePostKind.Original), updatedGroupItem?.filterConfig?.excludedKinds)
            assertEquals(listOf(TimelinePostKind.Repost), updatedChildItem?.filterConfig?.excludedKinds)
        }

    private fun homeSlot() =
        HomeTimelineTabItem(AccountType.Specific(MicroBlogKey(id = "home", host = "example.com")))
            .toTimelineSlotOrNull()
            ?: error("Home slot should be migratable")

    private fun localSlot() =
        Mastodon
            .LocalTimelineTabItem(
                account = AccountType.Specific(MicroBlogKey(id = "local", host = "example.com")),
                metaData = localMetaData(),
            ).toTimelineSlotOrNull()
            ?: error("Local slot should be migratable")

    private fun localMetaData() =
        TabMetaData(
            title = TitleType.Localized(TitleType.Localized.LocalizedKey.MastodonLocal),
            icon = IconType.Material(UiIcon.Local),
        )
}
