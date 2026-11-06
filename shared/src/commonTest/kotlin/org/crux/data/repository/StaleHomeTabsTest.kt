package org.crux.data.repository

import org.crux.data.model.IconType
import org.crux.data.model.tab.TimelineSlot
import org.crux.data.model.tab.TimelineSlotContent
import org.crux.data.model.tab.TimelineSourceRef
import org.crux.ui.model.UiIcon
import org.crux.ui.model.UiText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class StaleHomeTabsTest {
    private fun source(
        id: String,
        specId: String,
    ) = TimelineSlot(
        id = id,
        content =
            TimelineSlotContent.Source(
                TimelineSourceRef(
                    id = id,
                    specId = specId,
                    title = UiText.Raw(id),
                    icon = IconType.Material(UiIcon.Rss),
                    data = "",
                ),
            ),
    )

    private fun group(
        id: String,
        vararg children: TimelineSlot,
    ) = TimelineSlot(id = id, content = TimelineSlotContent.Group(children = children.toList()))

    private val known = setOf("web.x", "rss")
    private val hasSpec: (TimelineSlot) -> Boolean = { slot ->
        (slot.content as TimelineSlotContent.Source).source.specId in known
    }

    @Test
    fun dropsTabsWhoseSpecIsGone() {
        val slots = listOf(source("x", "web.x"), source("old", "web.mastodon"), source("feed", "rss"))
        assertEquals(listOf("x", "feed"), pruneStaleSlots(slots, hasSpec).map { it.id })
    }

    @Test
    fun keepsEverythingWhenAllSpecsExist() {
        val slots = listOf(source("x", "web.x"), source("feed", "rss"))
        assertEquals(slots, pruneStaleSlots(slots, hasSpec))
    }

    @Test
    fun dropsStaleChildrenAndEmptiedGroups() {
        val mixed = group("mixed", source("x", "web.x"), source("old", "web.mastodon"))
        val dead = group("dead", source("old2", "web.mastodon"))
        val pruned = pruneStaleSlots(listOf(mixed, dead), hasSpec)
        assertEquals(listOf("mixed"), pruned.map { it.id })
        val children = (pruned.single().content as TimelineSlotContent.Group).children
        assertEquals(listOf("x"), children.map { it.id })
    }

    @Test
    fun unchangedGroupIsReturnedAsIs() {
        val mixed = group("mixed", source("x", "web.x"))
        assertSame(mixed, pruneStaleSlots(listOf(mixed), hasSpec).single())
    }
}
