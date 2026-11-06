package org.crux.ui.presenter.home

import org.crux.data.model.HomeTimelineTabItem
import org.crux.data.model.IconType
import org.crux.data.model.ListTimelineTabItem
import org.crux.data.model.TabMetaData
import org.crux.data.model.TitleType
import org.crux.data.model.tab.TabSettingsV2
import org.crux.data.model.tab.TimelineMergePolicy
import org.crux.data.model.tab.TimelinePresentation
import org.crux.data.model.tab.TimelineResolver
import org.crux.data.model.tab.TimelineSlot
import org.crux.data.model.tab.TimelineSlotContent
import org.crux.data.model.tab.UiGroupTimelineTabItem
import org.crux.data.model.tab.toTimelineSlotOrNull
import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import org.crux.testPlatformRuntimeData
import org.crux.ui.model.UiIcon
import org.crux.unavailableAccountService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GroupConfigPresenterTest {
    @Test
    fun editingGroupPreservesExcludedChildren() {
        assertEditingPreservesChildren(
            listOf(
                homeSlot("visible"),
                homeSlot("excluded").copy(presentation = TimelinePresentation(enabled = false)),
            ),
        )
    }

    @Test
    fun editingGroupKeepsGroupWhenAllChildrenAreExcluded() {
        assertEditingPreservesChildren(
            listOf(
                homeSlot("excluded").copy(presentation = TimelinePresentation(enabled = false)),
            ),
        )
    }

    @Test
    fun upsertGroupConfigReplacesDuplicateKeyInsteadOfAppending() {
        val timelineResolver = TimelineResolver(testPlatformRuntimeData(), unavailableAccountService())
        val accountKey = MicroBlogKey("1872639344760254464", "x.com")
        val accountType = AccountType.Specific(accountKey)
        val homeTab = timelineResolver.toTabItem(HomeTimelineTabItem(accountType).toTimelineSlotOrNull()!!)
        val listTab =
            timelineResolver.toTabItem(
                ListTimelineTabItem(
                    account = accountType,
                    listId = "1681353064253640704",
                    metaData =
                        TabMetaData(
                            title = TitleType.Text("list"),
                            icon = IconType.Material(UiIcon.List),
                        ),
                ).toTimelineSlotOrNull()!!,
            )
        val existingGroup =
            TabSettingsV2()
                .upsertGroupConfig(
                    initialItem = null,
                    name = "엑스",
                    icon = IconType.Material(UiIcon.Rss),
                    appearancePatch = null,
                    enabled = true,
                    tabs = listOf(homeTab, listTab),
                    defaultGroupName = "Group",
                    timelineResolver = timelineResolver,
                ).homeSlots
                .single()
                .let(timelineResolver::toTabItem)
        val existingGroupItem = assertIs<UiGroupTimelineTabItem>(existingGroup)

        val updated =
            TabSettingsV2(homeSlots = listOf(timelineResolver.toSlot(existingGroupItem)))
                .upsertGroupConfig(
                    initialItem = null,
                    name = "엑스",
                    icon = IconType.Material(UiIcon.Rss),
                    appearancePatch = null,
                    enabled = true,
                    tabs = listOf(homeTab, homeTab, listTab),
                    mergePolicy = TimelineMergePolicy.Staggered,
                    defaultGroupName = "Group",
                    timelineResolver = timelineResolver,
                )

        assertEquals(1, updated.homeSlots.size)
        assertEquals(existingGroupItem.id, updated.homeSlots.single().id)
        val updatedGroup = assertIs<UiGroupTimelineTabItem>(timelineResolver.toTabItem(updated.homeSlots.single()))
        assertEquals(
            listOf(homeTab.id, listTab.id),
            updatedGroup.children.map { it.id },
        )
        assertEquals(TimelineMergePolicy.Staggered, updatedGroup.mergePolicy)
    }

    private fun assertEditingPreservesChildren(children: List<TimelineSlot>) {
        val resolver = TimelineResolver(testPlatformRuntimeData(), unavailableAccountService())
        val slot = TimelineSlot(id = "custom", content = TimelineSlotContent.Group(children = children))
        val item = assertIs<UiGroupTimelineTabItem>(resolver.toTabItem(slot))

        val updated =
            TabSettingsV2(homeSlots = listOf(slot)).upsertGroupConfig(
                initialItem = item,
                name = "Custom",
                icon = item.icon,
                appearancePatch = item.appearancePatch,
                enabled = item.enabled,
                tabs = item.children,
                defaultGroupName = "Group",
                timelineResolver = resolver,
            )

        assertEquals(1, updated.homeSlots.size)
        assertEquals(children, assertIs<TimelineSlotContent.Group>(updated.homeSlots.single().content).children)
    }

    private fun homeSlot(accountId: String): TimelineSlot =
        requireNotNull(
            HomeTimelineTabItem(AccountType.Specific(MicroBlogKey(accountId, "example.com"))).toTimelineSlotOrNull(),
        )
}
