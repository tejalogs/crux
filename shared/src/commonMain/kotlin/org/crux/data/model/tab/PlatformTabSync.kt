package org.crux.data.model.tab

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.protobuf.ProtoBuf
import org.crux.ui.model.UiIcon
import org.crux.ui.model.UiStrings
import org.crux.ui.model.asText
import org.crux.ui.model.asType

/**
 * Crux: the home pill each platform owns. Platform names are the `CruxPlatform` enum names, in the
 * order the UI lists them. Instagram and LinkedIn used to get an account-based pill
 * (`common.home:<key>@instagram.com`) after sign-in; those legacy pills are recognised here so they
 * can be migrated to the static ones instead of duplicated.
 */
internal class PlatformTab(
    val platform: String,
    val tab: UiTimelineTabItem,
    private val legacyIdSuffix: String? = null,
) {
    fun owns(id: String): Boolean =
        id == tab.id ||
            (legacyIdSuffix != null && id.startsWith("${TimelineSpecIds.COMMON_HOME}:") && id.endsWith(legacyIdSuffix))
}

internal fun platformTabs(): List<PlatformTab> =
    listOf(
        PlatformTab("X", LiveWebTimelineSpecs.xCandidate.toUiTimelineTabItem()),
        PlatformTab("Instagram", LiveWebTimelineSpecs.instagramCandidate.toUiTimelineTabItem(), "@instagram.com"),
        PlatformTab("LinkedIn", LiveWebTimelineSpecs.linkedinCandidate.toUiTimelineTabItem(), "@linkedin.com"),
        PlatformTab("Reddit", LiveWebTimelineSpecs.redditCandidate.toUiTimelineTabItem()),
        PlatformTab("Rss", allRssTabItem()),
    )

/** The "All RSS" pill, built without the subscription module that owns the spec. */
@OptIn(ExperimentalSerializationApi::class)
internal fun allRssTabItem(): UiSourceTimelineTabItem {
    val source =
        TimelineSourceRef(
            id = "${TimelineSpecIds.RSS_ALL}:all",
            specId = TimelineSpecIds.RSS_ALL,
            title = UiStrings.AllRssFeeds.asText(),
            icon = UiIcon.Rss.asType(),
            data = ProtoBuf.encodeToHexString(AllRssTimelineData.serializer(), AllRssTimelineData),
        )
    val presentation = TimelinePresentation()
    return UiSourceTimelineTabItem(
        id = source.id,
        source = source,
        presentation = presentation,
        title = source.title,
        icon = source.icon,
        appearancePatch = presentation.appearance,
        enabled = presentation.enabled,
    )
}

/**
 * Makes the home pills match [enabled] platforms.
 *
 *  - A disabled platform's pill (static or legacy) is removed.
 *  - A legacy Instagram/LinkedIn account pill is replaced in place by the static one.
 *  - An enabled platform with no pill gets one appended, in platform order.
 *  - Everything else keeps its place, so the person's order and removals survive.
 *
 * The system "Home" mixed group is dropped: with only live web tabs and RSS there is nothing for
 * it to merge, so it would always be empty.
 */
internal fun syncPlatformTabs(
    current: List<UiTimelineTabItem>,
    enabledPlatforms: Set<String>,
    platforms: List<PlatformTab> = platformTabs(),
    addMissing: Boolean = true,
): List<UiTimelineTabItem> {
    val migrated = current.mapNotNull { it.syncPlatform(enabledPlatforms, platforms) }.distinctBy { it.id }
    if (!addMissing) return migrated
    val present = migrated.flatMap { it.realIds() }.toSet()
    val missing =
        platforms
            .filter { it.platform in enabledPlatforms && it.tab.id !in present }
            .map { it.tab }
    return migrated + missing
}

private fun UiTimelineTabItem.syncPlatform(
    enabledPlatforms: Set<String>,
    platforms: List<PlatformTab>,
): UiTimelineTabItem? =
    when (this) {
        is UiSourceTimelineTabItem -> {
            val owner = platforms.firstOrNull { it.owns(id) }
            when {
                owner == null -> this
                owner.platform !in enabledPlatforms -> null
                owner.tab.id == id -> this
                else -> owner.tab
            }
        }

        is UiGroupTimelineTabItem -> {
            if (isSystemHomeMixedTimeline) {
                // Crux has no API feeds left to merge, so the combined "Home" tab only ever shows
                // nothing. Each platform has its own pill instead.
                null
            } else {
                val children = children.mapNotNull { it.syncPlatform(enabledPlatforms, platforms) }.distinctBy { it.id }
                when {
                    children.isEmpty() -> {
                        null
                    }

                    children == this.children -> {
                        this
                    }

                    else -> {
                        UiGroupTimelineTabItem(
                            id = id,
                            children = children,
                            mergePolicy = mergePolicy,
                            source = source,
                            presentation = presentation,
                            title = title,
                            icon = icon,
                            appearancePatch = appearancePatch,
                            enabled = this.enabled,
                        )
                    }
                }
            }
        }
    }

private fun UiTimelineTabItem.realIds(): List<String> =
    when (this) {
        is UiSourceTimelineTabItem -> listOf(id)
        is UiGroupTimelineTabItem -> if (isSystemHomeMixedTimeline) emptyList() else children.flatMap { it.realIds() }
    }
