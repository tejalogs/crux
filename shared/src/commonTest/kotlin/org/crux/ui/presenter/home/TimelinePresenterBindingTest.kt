package org.crux.ui.presenter.home

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.Path
import org.crux.createTestFileSystem
import org.crux.createTestRootPath
import org.crux.data.datastore.AppDataStore
import org.crux.data.io.OkioFileStorage
import org.crux.data.model.HomeTimelineTabItem
import org.crux.data.model.tab.GroupSource
import org.crux.data.model.tab.SYSTEM_HOME_MIXED_TIMELINE_ID
import org.crux.data.model.tab.TabSettingsV2
import org.crux.data.model.tab.TimelineFilterConfig
import org.crux.data.model.tab.TimelineMergePolicy
import org.crux.data.model.tab.TimelinePostKind
import org.crux.data.model.tab.TimelinePresentation
import org.crux.data.model.tab.TimelinePresenterFactory
import org.crux.data.model.tab.TimelineResolver
import org.crux.data.model.tab.TimelineSlot
import org.crux.data.model.tab.TimelineSlotContent
import org.crux.data.model.tab.UiGroupTimelineTabItem
import org.crux.data.model.tab.isSystemHomeMixedTimeline
import org.crux.data.model.tab.toTimelineSlotOrNull
import org.crux.data.repository.SettingsRepository
import org.crux.deleteTestRootPath
import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import org.crux.testPlatformRuntimeData
import org.crux.unavailableAccountService
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TimelinePresenterBindingTest {
    private lateinit var root: Path
    private lateinit var timelineResolver: TimelineResolver
    private lateinit var settingsRepository: SettingsRepository

    @BeforeTest
    fun setup() {
        root = createTestRootPath()
        val fileStorage = OkioFileStorage(createTestFileSystem(), root)
        timelineResolver = TimelineResolver(testPlatformRuntimeData(), unavailableAccountService())
        settingsRepository =
            SettingsRepository(
                fileStorage = fileStorage,
                appDataStore = AppDataStore(fileStorage),
                timelineResolver = timelineResolver,
            )
    }

    @AfterTest
    fun tearDown() {
        deleteTestRootPath(root)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun filterConfigObservationUsesFixedTimelineTabItemId() =
        runTest {
            val firstSlot =
                homeSlot(
                    id = "home:first",
                    filterConfig =
                        TimelineFilterConfig(
                            excludedKinds = listOf(TimelinePostKind.Reply),
                        ),
                )
            settingsRepository.updateTabSettingsV2 {
                TabSettingsV2(homeSlots = listOf(firstSlot))
            }

            val observed = mutableListOf<List<TimelinePostKind>>()
            val job =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    observeTimelineFilterConfig(
                        settingsRepository = settingsRepository,
                        timelineTabItemId = firstSlot.id,
                    ).map { it.excludedKinds }
                        .take(2)
                        .collect(observed::add)
                }
            advanceUntilIdle()
            settingsRepository.updateTabSettingsV2 {
                copy(
                    homeSlots =
                        listOf(
                            firstSlot.copy(
                                presentation =
                                    TimelinePresentation(
                                        filterConfig =
                                            TimelineFilterConfig(
                                                excludedKinds = listOf(TimelinePostKind.Repost),
                                            ),
                                    ),
                            ),
                        ),
                )
            }
            advanceUntilIdle()
            job.join()

            assertEquals(
                listOf(
                    listOf(TimelinePostKind.Reply),
                    listOf(TimelinePostKind.Repost),
                ),
                observed,
            )
        }

    @Test
    fun launchRefreshSettingOnlyAppliesToHomeTimeline() =
        runTest {
            var settingReads = 0
            val disabledSetting =
                suspend {
                    settingReads += 1
                    false
                }

            assertTrue(
                shouldRefreshTimelineOnInitialize(
                    isHomeTimeline = false,
                    refreshHomeTimelineOnLaunch = disabledSetting,
                ),
            )
            assertEquals(0, settingReads)
            assertFalse(
                shouldRefreshTimelineOnInitialize(
                    isHomeTimeline = true,
                    refreshHomeTimelineOnLaunch = disabledSetting,
                ),
            )
            assertEquals(1, settingReads)
        }

    @Test
    fun savingTabsPreservesExcludedSourcesAndGroupsInsideManualGroup() =
        runTest {
            val visible = homeSlot(accountId = "visible")
            val excluded = homeSlot(accountId = "excluded").copy(presentation = TimelinePresentation(enabled = false))
            val excludedGroup =
                TimelineSlot(
                    id = "excluded-group",
                    content = TimelineSlotContent.Group(children = listOf(homeSlot(accountId = "nested"))),
                    presentation = TimelinePresentation(enabled = false),
                )
            val group =
                TimelineSlot(
                    id = "custom",
                    content = TimelineSlotContent.Group(children = listOf(visible, excluded, excludedGroup)),
                )
            settingsRepository.updateTabSettingsV2 { TabSettingsV2(homeSlots = listOf(group)) }

            val tabs = settingsRepository.homeTimelineTabs.first()
            val item = assertIs<UiGroupTimelineTabItem>(tabs.single())
            val visibleOnly = group.copy(content = TimelineSlotContent.Group(children = listOf(visible)))
            assertEquals(timelineResolver.toTabItem(visibleOnly).contentSourceKey, item.contentSourceKey)
            assertEquals(setOf(MicroBlogKey("visible", "example.com")), timelineResolver.accountKeys(item))

            settingsRepository.replaceHomeTimelineTabs(tabs)

            assertEquals(listOf(group), settingsRepository.tabSettingsV2.first().homeSlots)
        }

    private suspend fun systemHomeMixedTab(): UiGroupTimelineTabItem {
        val tab =
            settingsRepository.homeTimelineTabs
                .map { tabs -> tabs.first { it.isSystemHomeMixedTimeline } }
                .first()
        return assertIs<UiGroupTimelineTabItem>(tab)
    }

    private fun systemHomeMixedSlot(vararg children: TimelineSlot): TimelineSlot =
        TimelineSlot(
            id = SYSTEM_HOME_MIXED_TIMELINE_ID,
            content =
                TimelineSlotContent.Group(
                    children = children.toList(),
                    source = GroupSource.SystemHome,
                    mergePolicy = TimelineMergePolicy.TimePerPage,
                ),
        )

    private fun homeSlot(accountId: String): TimelineSlot =
        requireNotNull(
            HomeTimelineTabItem(AccountType.Specific(MicroBlogKey(id = accountId, host = "example.com")))
                .toTimelineSlotOrNull(),
        )

    private fun homeSlot(
        id: String,
        filterConfig: TimelineFilterConfig,
    ) = requireNotNull(
        HomeTimelineTabItem(AccountType.Specific(MicroBlogKey(id = "home", host = "example.com")))
            .toTimelineSlotOrNull(),
    ).copy(
        id = id,
        presentation = TimelinePresentation(filterConfig = filterConfig),
    )
}
