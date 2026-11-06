package org.crux.data.repository

import org.crux.data.model.tab.TimelineSlot
import org.crux.data.model.tab.TimelineSlotContent

/**
 * Drops saved tabs whose timeline spec no longer exists in the app, such as tabs left behind by a
 * removed platform. Inside a group only the stale children go, and a group left empty goes too.
 * Slots that are all current come back unchanged, so callers can compare and skip a write.
 */
internal fun pruneStaleSlots(
    slots: List<TimelineSlot>,
    hasSpec: (TimelineSlot) -> Boolean,
): List<TimelineSlot> = slots.mapNotNull { it.pruneStale(hasSpec) }

private fun TimelineSlot.pruneStale(hasSpec: (TimelineSlot) -> Boolean): TimelineSlot? =
    when (val slotContent = content) {
        is TimelineSlotContent.Source -> {
            takeIf(hasSpec)
        }

        is TimelineSlotContent.Group -> {
            val children = pruneStaleSlots(slotContent.children, hasSpec)
            when {
                children.isEmpty() && slotContent.children.isNotEmpty() -> null
                children == slotContent.children -> this
                else -> copy(content = slotContent.copy(children = children))
            }
        }
    }
