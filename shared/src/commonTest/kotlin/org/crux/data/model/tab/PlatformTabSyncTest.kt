package org.crux.data.model.tab

import org.crux.data.model.IconType
import org.crux.ui.model.UiIcon
import org.crux.ui.model.UiText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlatformTabSyncTest {
    private val all = setOf("X", "Instagram", "LinkedIn", "Reddit", "Rss")

    private fun ids(tabs: List<UiTimelineTabItem>) = tabs.map { it.id }

    private fun source(id: String): UiSourceTimelineTabItem {
        val ref =
            TimelineSourceRef(
                id = id,
                specId = id.substringBefore(':'),
                title = UiText.Raw(id),
                icon = IconType.Material(UiIcon.Rss),
                data = "",
            )
        val presentation = TimelinePresentation()
        return UiSourceTimelineTabItem(
            id = id,
            source = ref,
            presentation = presentation,
            title = ref.title,
            icon = ref.icon,
            appearancePatch = presentation.appearance,
            enabled = true,
        )
    }

    private fun group(vararg children: UiTimelineTabItem) =
        UiGroupTimelineTabItem(
            id = "user_group",
            children = children.toList(),
            mergePolicy = TimelineMergePolicy.Time,
            source = GroupSource.Manual,
            presentation = TimelinePresentation(),
            title = UiText.Raw("Group"),
            icon = IconType.Material(UiIcon.Rss),
            appearancePatch = null,
            enabled = true,
        )

    @Test
    fun freshInstallGetsOnePillPerEnabledPlatformInOrder() {
        val result = syncPlatformTabs(emptyList(), all)
        assertEquals(
            listOf("web.x:x", "web.instagram:instagram", "web.linkedin:linkedin", "web.reddit:reddit", "rss.all:all"),
            ids(result),
        )
        assertTrue(result.none { it.isSystemHomeMixedTimeline }, "must not create the empty mixed Home group")
    }

    @Test
    fun onlyEnabledPlatformsGetPills() {
        assertEquals(listOf("web.instagram:instagram", "rss.all:all"), ids(syncPlatformTabs(emptyList(), setOf("Instagram", "Rss"))))
        assertEquals(emptyList(), ids(syncPlatformTabs(emptyList(), emptySet())))
    }

    @Test
    fun keepsTheUsersOrderAndDoesNotDuplicate() {
        val current = listOf(source("web.reddit:reddit"), source("rss.feed:https://a.example/feed"), source("web.x:x"))
        val result = syncPlatformTabs(current, setOf("X", "Reddit", "Instagram"))
        assertEquals(
            listOf("web.reddit:reddit", "rss.feed:https://a.example/feed", "web.x:x", "web.instagram:instagram"),
            ids(result),
        )
    }

    @Test
    fun disablingAPlatformRemovesOnlyItsPill() {
        val current = syncPlatformTabs(emptyList(), all)
        val result = syncPlatformTabs(current, all - "Reddit" - "Rss")
        assertEquals(listOf("web.x:x", "web.instagram:instagram", "web.linkedin:linkedin"), ids(result))
    }

    @Test
    fun legacyAccountPillsAreMigratedInPlaceWithoutDuplicates() {
        val current =
            listOf(
                source("common.home:123@instagram.com"),
                source("web.x:x"),
                source("common.home:me@linkedin.com"),
            )
        val result = syncPlatformTabs(current, setOf("X", "Instagram", "LinkedIn"))
        assertEquals(listOf("web.instagram:instagram", "web.x:x", "web.linkedin:linkedin"), ids(result))
    }

    @Test
    fun legacyPillIsReplacedEvenWhenStaticOneExists() {
        val current = listOf(source("web.instagram:instagram"), source("common.home:123@instagram.com"))
        assertEquals(listOf("web.instagram:instagram"), ids(syncPlatformTabs(current, setOf("Instagram"))))
    }

    @Test
    fun legacyPillIsDroppedWhenPlatformDisabled() {
        val current = listOf(source("common.home:123@instagram.com"), source("web.x:x"))
        assertEquals(listOf("web.x:x"), ids(syncPlatformTabs(current, setOf("X"))))
    }

    @Test
    fun otherAccountsHomeTabsAreNotTouched() {
        val current = listOf(source("common.home:me@mastodon.social"))
        assertEquals(listOf("common.home:me@mastodon.social"), ids(syncPlatformTabs(current, setOf("X"))).take(1))
    }

    @Test
    fun migrationOnlyModeDoesNotAddMissingPills() {
        val platforms = platformTabs().filter { it.platform == "Instagram" || it.platform == "LinkedIn" }
        val current = listOf(source("web.x:x"), source("common.home:123@instagram.com"))
        val result = syncPlatformTabs(current, all, platforms = platforms, addMissing = false)
        assertEquals(listOf("web.x:x", "web.instagram:instagram"), ids(result))
    }

    @Test
    fun userGroupChildrenAreMigratedAndEmptiedGroupsDropped() {
        val migrated = syncPlatformTabs(listOf(group(source("common.home:1@instagram.com"), source("rss.feed:a"))), setOf("Instagram"))
        val groupChildren = (migrated.first() as UiGroupTimelineTabItem).children
        assertEquals(listOf("web.instagram:instagram", "rss.feed:a"), ids(groupChildren))
        // The pill already lives in the group, so it is not appended a second time.
        assertEquals(1, migrated.size)

        val dropped = syncPlatformTabs(listOf(group(source("common.home:1@instagram.com"))), emptySet())
        assertTrue(dropped.isEmpty())
    }

    @Test
    fun dropsTheEmptyCombinedHomeTabAndAddsAllRss() {
        val tabs = listOf(source("web.x:x"), source("web.reddit:reddit"))
        val mixed =
            UiGroupTimelineTabItem(
                id = SYSTEM_HOME_MIXED_TIMELINE_ID,
                children = tabs,
                mergePolicy = TimelineMergePolicy.Time,
                source = GroupSource.SystemHome,
                presentation = TimelinePresentation(),
                title = UiText.Raw("Mixed"),
                icon = IconType.Material(UiIcon.Rss),
                appearancePatch = null,
                enabled = true,
            )
        val withMixed = listOf(mixed) + tabs
        assertTrue(withMixed.any { it.isSystemHomeMixedTimeline })
        val result = syncPlatformTabs(withMixed, setOf("X", "Reddit", "Rss"))
        assertTrue(result.none { it.isSystemHomeMixedTimeline })
        assertEquals(listOf("web.x:x", "web.reddit:reddit", "rss.all:all"), ids(result))
    }

    @Test
    fun syncOnlyAddsRequestedPlatformsSoUserRemovedPillsStayRemoved() {
        val current = listOf(source("web.x:x"), source("web.instagram:instagram"))
        val enabled = setOf("X", "Instagram", "Reddit", "LinkedIn")
        // Reddit was removed in tab settings; enabling LinkedIn must not resurrect Reddit.
        val afterEnableLinkedIn = syncPlatformTabs(current, enabled, platformsToAdd = setOf("LinkedIn"))
        assertEquals(listOf("web.x:x", "web.instagram:instagram", "web.linkedin:linkedin"), ids(afterEnableLinkedIn))
        // Disabling Instagram must not resurrect Reddit either.
        val afterDisableInstagram = syncPlatformTabs(afterEnableLinkedIn, enabled - "Instagram", platformsToAdd = emptySet())
        assertEquals(listOf("web.x:x", "web.linkedin:linkedin"), ids(afterDisableInstagram))
    }
}
