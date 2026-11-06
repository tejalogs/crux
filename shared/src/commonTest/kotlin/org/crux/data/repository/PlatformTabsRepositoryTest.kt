package org.crux.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.Path
import org.crux.createTestFileSystem
import org.crux.createTestRootPath
import org.crux.data.datastore.AppDataStore
import org.crux.data.io.OkioFileStorage
import org.crux.data.model.IconType
import org.crux.data.model.tab.LiveWebTimelineSpecs
import org.crux.data.model.tab.TimelineResolver
import org.crux.data.model.tab.TimelineSpec
import org.crux.data.model.tab.isSystemHomeMixedTimeline
import org.crux.data.model.tab.toUiTimelineTabItem
import org.crux.data.platform.CommonTimelineSpecs
import org.crux.deleteTestRootPath
import org.crux.model.MicroBlogKey
import org.crux.testPlatformRuntimeData
import org.crux.ui.model.UiIcon
import org.crux.ui.model.UiText
import org.crux.unavailableAccountService
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlatformTabsRepositoryTest {
    private lateinit var root: Path
    private lateinit var repository: SettingsRepository

    @BeforeTest
    fun setup() {
        root = createTestRootPath()
        val fileStorage = OkioFileStorage(createTestFileSystem(), root)
        repository =
            SettingsRepository(
                fileStorage = fileStorage,
                appDataStore = AppDataStore(fileStorage),
                timelineResolver = TimelineResolver(testPlatformRuntimeData(), unavailableAccountService()),
            )
    }

    @AfterTest
    fun tearDown() {
        deleteTestRootPath(root)
    }

    private suspend fun tabIds() = repository.homeTimelineTabs.first().map { it.id }

    private fun legacyTab(host: String) =
        CommonTimelineSpecs.home
            .candidate(
                data = TimelineSpec.AccountBasedData(MicroBlogKey("123", host)),
                title = UiText.Raw(host),
                icon = IconType.Material(UiIcon.Rss),
            ).toUiTimelineTabItem()

    @Test
    fun choosingPlatformsOnAFreshInstallCreatesTheirTabsWithoutAMixedGroup() =
        runTest {
            repository.syncLiveWebTabs(setOf("X", "Instagram", "LinkedIn", "Reddit", "Rss"))
            assertEquals(
                listOf("web.x:x", "web.instagram:instagram", "web.linkedin:linkedin", "web.reddit:reddit", "rss.all:all"),
                tabIds(),
            )
            assertTrue(repository.homeTimelineTabs.first().none { it.isSystemHomeMixedTimeline })
        }

    @Test
    fun changingTheChoiceKeepsOrderAndOnlyTouchesTheChangedPlatforms() =
        runTest {
            repository.syncLiveWebTabs(setOf("X", "Instagram", "Reddit"))
            repository.syncLiveWebTabs(setOf("X", "Reddit", "LinkedIn"))
            assertEquals(listOf("web.x:x", "web.reddit:reddit", "web.linkedin:linkedin"), tabIds())
        }

    @Test
    fun noPlatformsMeansNoTabs() =
        runTest {
            repository.syncLiveWebTabs(setOf("X"))
            repository.syncLiveWebTabs(emptySet())
            assertEquals(emptyList(), tabIds())
        }

    @Test
    fun legacyAccountPillsAreMigratedOnceAndMissingEnabledPillsAreAdded() =
        runTest {
            repository.updateAppSettings {
                copy(onboardingCompleted = true, cruxPlatforms = listOf("X", "Instagram", "LinkedIn"))
            }
            repository.replaceHomeTimelineTabs(
                listOf(legacyTab("instagram.com"), LiveWebTimelineSpecs.xCandidate.toUiTimelineTabItem()),
            )

            repository.ensureLiveWebTabsMigrated()
            assertEquals(listOf("web.instagram:instagram", "web.x:x", "web.linkedin:linkedin"), tabIds())

            // A pill the person removes afterwards stays removed.
            repository.removeHomeTimelineTabBySourceId("web.linkedin:linkedin")
            repository.ensureLiveWebTabsMigrated()
            assertEquals(listOf("web.instagram:instagram", "web.x:x"), tabIds())
        }

    @Test
    fun migrationWaitsForOnboardingSoItDoesNotPreemptTheChoice() =
        runTest {
            repository.ensureLiveWebTabsMigrated()
            assertEquals(emptyList(), tabIds())
            assertEquals(false, repository.appSettings.first().liveWebTabsMigrated)
        }
}
