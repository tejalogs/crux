package org.crux.data.repository

import org.crux.data.model.HomeTimelineTabItem
import org.crux.data.model.IconType
import org.crux.data.model.ListTimelineTabItem
import org.crux.data.model.MixedTimelineTabItem
import org.crux.data.model.TabMetaData
import org.crux.data.model.TabSettings
import org.crux.data.model.TitleType
import org.crux.data.model.tab.GroupSource
import org.crux.data.model.tab.SYSTEM_HOME_MIXED_TIMELINE_ID
import org.crux.data.model.tab.TimelinePresentation
import org.crux.data.model.tab.TimelineSlot
import org.crux.data.model.tab.TimelineSlotContent
import org.crux.data.model.tab.toTimelineSlotOrNull
import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiIcon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class AccountTabSyncCoordinatorSanitizerTest {
    @Test
    fun normalizeSystemHomeMixedTimelineAddsSystemMixedWhenSecondDefaultTabIsAdded() {
        val firstAccountKey = MicroBlogKey("1872639344760254464", "x.com")
        val secondAccountKey = MicroBlogKey("1711111111111111111", "mastodon.social")

        val normalized =
            listOf(
                HomeTimelineTabItem(AccountType.Specific(firstAccountKey)).toTimelineSlotOrNull()!!,
                HomeTimelineTabItem(AccountType.Specific(secondAccountKey)).toTimelineSlotOrNull()!!,
            ).normalizeSystemHomeMixedTimeline(enabled = true)

        assertEquals(
            listOf(
                SYSTEM_HOME_MIXED_TIMELINE_ID,
                "common.home:$firstAccountKey",
                "common.home:$secondAccountKey",
            ),
            normalized.map { it.id },
        )
    }

    @Test
    fun normalizeSystemHomeMixedTimelineDoesNotReAddSystemMixedAfterItWasRemoved() {
        val firstAccountKey = MicroBlogKey("1872639344760254464", "x.com")
        val secondAccountKey = MicroBlogKey("1711111111111111111", "mastodon.social")

        val normalized =
            listOf(
                HomeTimelineTabItem(AccountType.Specific(firstAccountKey)).toTimelineSlotOrNull()!!,
                HomeTimelineTabItem(AccountType.Specific(secondAccountKey)).toTimelineSlotOrNull()!!,
            ).normalizeSystemHomeMixedTimeline(enabled = true)
                .filterNot { it.id == SYSTEM_HOME_MIXED_TIMELINE_ID }
                .normalizeSystemHomeMixedTimeline(enabled = false)

        assertEquals(
            listOf(
                "common.home:$firstAccountKey",
                "common.home:$secondAccountKey",
            ),
            normalized.map { it.id },
        )
    }

    @Test
    fun normalizeSystemHomeMixedTimelinePreservesDisabledChildrenInManualGroup() {
        val accountKey = MicroBlogKey("1872639344760254464", "x.com")
        val secondAccountKey = MicroBlogKey("1711111111111111111", "mastodon.social")
        val enabledChild = HomeTimelineTabItem(AccountType.Specific(accountKey)).toTimelineSlotOrNull()!!
        val disabledChild =
            HomeTimelineTabItem(AccountType.Specific(secondAccountKey))
                .toTimelineSlotOrNull()!!
                .copy(presentation = TimelinePresentation(enabled = false))
        val manualGroup =
            TimelineSlot(
                id = "manual_group",
                content =
                    TimelineSlotContent.Group(
                        children = listOf(enabledChild, disabledChild),
                        source = GroupSource.Manual,
                    ),
            )

        val normalized =
            listOf(
                enabledChild,
                HomeTimelineTabItem(AccountType.Specific(MicroBlogKey("1999999999999999999", "mastodon.cloud"))).toTimelineSlotOrNull()!!,
                manualGroup,
            ).normalizeSystemHomeMixedTimeline(enabled = true)

        val preservedGroup = assertNotNull(normalized.firstOrNull { it.id == manualGroup.id })
        val groupContent = assertIs<TimelineSlotContent.Group>(preservedGroup.content)
        assertEquals(listOf(enabledChild.id, disabledChild.id), groupContent.children.map { it.id })
        assertFalse(groupContent.children[1].presentation.enabled)
    }

    @Test
    fun sanitizeDuplicateTabKeysRemovesDuplicateMixedTabsAndSubTabs() {
        val accountKey = MicroBlogKey("1872639344760254464", "x.com")
        val accountType = AccountType.Specific(accountKey)
        val homeTab = HomeTimelineTabItem(accountType)
        val listTab =
            ListTimelineTabItem(
                account = accountType,
                listId = "1681353064253640704",
                metaData =
                    TabMetaData(
                        title = TitleType.Text("list"),
                        icon = IconType.Material(UiIcon.List),
                    ),
            )
        val mixedTab =
            MixedTimelineTabItem(
                subTimelineTabItem = listOf(homeTab, homeTab, listTab),
                metaData =
                    TabMetaData(
                        title = TitleType.Text("엑스"),
                        icon = IconType.Material(UiIcon.Rss),
                    ),
            )

        val sanitized =
            TabSettings(
                mainTabs = listOf(mixedTab, mixedTab),
            ).sanitizeDuplicateTabKeys()

        assertEquals(1, sanitized.mainTabs.size)
        val sanitizedMixedTab = assertIs<MixedTimelineTabItem>(sanitized.mainTabs.single())
        assertEquals(listOf(homeTab.key, listTab.key), sanitizedMixedTab.subTimelineTabItem.map { it.key })
    }
}
