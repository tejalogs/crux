package org.crux.data.repository

import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.crux.data.datasource.microblog.datasource.TimelineTabConfigurationDataSource
import org.crux.data.model.MixedTimelineTabItem
import org.crux.data.model.TabSettings
import org.crux.data.model.TimelineTabItem
import org.crux.data.model.tab.GroupSource
import org.crux.data.model.tab.SYSTEM_HOME_MIXED_TIMELINE_ID
import org.crux.data.model.tab.TimelineMergePolicy
import org.crux.data.model.tab.TimelinePresentation
import org.crux.data.model.tab.TimelineResolver
import org.crux.data.model.tab.TimelineSlot
import org.crux.data.model.tab.TimelineSlotContent
import org.crux.data.platform.INSTAGRAM_PLATFORM_ID
import org.crux.data.platform.LINKEDIN_PLATFORM_ID
import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiAccount
import org.koin.core.annotation.Single

@Single(createdAtStart = true)
internal class AccountTabSyncCoordinator(
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val coroutineScope: CoroutineScope,
    private val timelineResolver: TimelineResolver,
) {
    init {
        coroutineScope.launch {
            removeTabsForDeletedAccounts()
        }
        coroutineScope.launch {
            accountRepository.onAdded.collect { account ->
                addDefaultTabs(account)
            }
        }
        coroutineScope.launch {
            accountRepository.onRemoved.collect { accountKey ->
                removeTabsForAccount(accountKey)
            }
        }
    }

    private suspend fun removeTabsForDeletedAccounts() {
        val existingAccounts =
            accountRepository.allAccounts
                .first()
                .mapTo(linkedSetOf()) { it.accountKey }
        settingsRepository.updateTabSettingsV2 {
            copy(
                homeSlots =
                    homeSlots
                        .mapNotNull { it.cleanupForExistingAccounts(existingAccounts) }
                        .normalizeSystemHomeMixedTimeline(
                            enabled = homeSlots.anySystemHomeMixedTimeline(),
                        ),
            )
        }
    }

    private suspend fun addDefaultTabs(account: UiAccount) {
        // Instagram and LinkedIn are live web pills owned by the platform choice, not by the
        // shadow account a sign-in registers, so a sign-in must not add a second pill.
        if (account.platformId in LIVE_WEB_ACCOUNT_PLATFORMS) {
            return
        }
        val defaultTabs =
            (accountRepository.getOrCreateDataSource(account) as? TimelineTabConfigurationDataSource)
                ?.defaultTabs
                .orEmpty()
        if (defaultTabs.isEmpty()) {
            return
        }
        settingsRepository.updateTabSettingsV2 {
            val defaultSlots = defaultTabs.map(timelineResolver::toSlot)
            val shouldEnableSystemHomeMixedTimeline =
                homeSlots.anySystemHomeMixedTimeline() ||
                    homeSlots.countNonSystemHomeTabs() < 2
            val newSlots =
                (homeSlots + defaultSlots)
                    .normalizeSystemHomeMixedTimeline(
                        enabled = shouldEnableSystemHomeMixedTimeline,
                    )
            val newSettings = copy(homeSlots = newSlots)
            if (newSettings == this) {
                this
            } else {
                newSettings
            }
        }
    }

    private suspend fun removeTabsForAccount(accountKey: MicroBlogKey) {
        settingsRepository.updateTabSettingsV2 {
            copy(
                homeSlots =
                    homeSlots
                        .mapNotNull { it.cleanupForRemovedAccount(accountKey) }
                        .normalizeSystemHomeMixedTimeline(
                            enabled = homeSlots.anySystemHomeMixedTimeline(),
                        ),
            )
        }
    }

    private fun TimelineSlot.cleanupForExistingAccounts(accountKeys: Set<MicroBlogKey>): TimelineSlot? =
        cleanupAccountSlots { accountKey ->
            accountKey == null || accountKey in accountKeys
        }

    private fun TimelineSlot.cleanupForRemovedAccount(accountKey: MicroBlogKey): TimelineSlot? =
        cleanupAccountSlots { slotAccountKey ->
            slotAccountKey == null || slotAccountKey != accountKey
        }

    private fun TimelineSlot.cleanupAccountSlots(shouldKeep: (MicroBlogKey?) -> Boolean): TimelineSlot? =
        when (val slotContent = content) {
            is TimelineSlotContent.Source -> {
                if (timelineResolver.hasSpec(this) && shouldKeep(timelineResolver.resolveAccountKey(this))) {
                    this
                } else {
                    null
                }
            }

            is TimelineSlotContent.Group -> {
                val sanitizedChildren =
                    slotContent.children
                        .mapNotNull { it.cleanupAccountSlots(shouldKeep) }
                        .distinctBy { it.id }
                if (sanitizedChildren.isEmpty()) {
                    null
                } else if (sanitizedChildren == slotContent.children) {
                    this
                } else {
                    copy(content = slotContent.copy(children = sanitizedChildren))
                }
            }
        }
}

private val LIVE_WEB_ACCOUNT_PLATFORMS = setOf(INSTAGRAM_PLATFORM_ID, LINKEDIN_PLATFORM_ID)

internal fun List<TimelineSlot>.normalizeSystemHomeMixedTimeline(enabled: Boolean): List<TimelineSlot> {
    val deduplicatedSlots = distinctBy { it.id }
    val existingSystemHomeGroup = deduplicatedSlots.firstOrNull { it.isSystemHomeMixedTimeline() }
    val slotsWithoutSystemHomeGroup = deduplicatedSlots.filterNot { it.isSystemHomeMixedTimeline() }
    if (!enabled || slotsWithoutSystemHomeGroup.size < 2) {
        return slotsWithoutSystemHomeGroup
    }

    val systemHomeGroup =
        TimelineSlot(
            id = SYSTEM_HOME_MIXED_TIMELINE_ID,
            content =
                TimelineSlotContent.Group(
                    children = slotsWithoutSystemHomeGroup,
                    source = GroupSource.SystemHome,
                    mergePolicy =
                        (existingSystemHomeGroup?.content as? TimelineSlotContent.Group)?.mergePolicy
                            ?: TimelineMergePolicy.Time,
                ),
            presentation = existingSystemHomeGroup?.presentation ?: TimelinePresentation(),
        )
    val targetIndex = deduplicatedSlots.indexOfFirst { it.isSystemHomeMixedTimeline() }.takeIf { it >= 0 } ?: 0
    return slotsWithoutSystemHomeGroup
        .toMutableList()
        .apply {
            add(minOf(targetIndex, size), systemHomeGroup)
        }
}

private fun List<TimelineSlot>.anySystemHomeMixedTimeline(): Boolean = any { it.isSystemHomeMixedTimeline() }

private fun List<TimelineSlot>.countNonSystemHomeTabs(): Int = count { !it.isSystemHomeMixedTimeline() }

private fun TimelineSlot.isSystemHomeMixedTimeline(): Boolean = (content as? TimelineSlotContent.Group)?.source == GroupSource.SystemHome

internal fun TabSettings.sanitizeDuplicateTabKeys(): TabSettings {
    val sanitizedTabs =
        mainTabs
            .mapNotNull { it.sanitizeDuplicateTabKeys() }
            .distinctBy { it.key }
    return if (sanitizedTabs == mainTabs) {
        this
    } else {
        copy(mainTabs = sanitizedTabs)
    }
}

private fun TimelineTabItem.sanitizeDuplicateTabKeys(): TimelineTabItem? =
    when (this) {
        is MixedTimelineTabItem -> {
            val sanitizedSubTabs =
                subTimelineTabItem
                    .mapNotNull { it.sanitizeDuplicateTabKeys() }
                    .distinctBy { it.key }
                    .toImmutableList()
            if (sanitizedSubTabs.isEmpty()) {
                null
            } else if (sanitizedSubTabs == subTimelineTabItem) {
                this
            } else {
                copy(subTimelineTabItem = sanitizedSubTabs)
            }
        }

        else -> {
            this
        }
    }
