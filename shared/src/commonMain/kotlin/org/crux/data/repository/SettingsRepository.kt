package org.crux.data.repository

import androidx.compose.runtime.Stable
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.crux.common.protobufSerializer
import org.crux.data.datastore.AppDataStore
import org.crux.data.datastore.createDataStoreStorage
import org.crux.data.datastore.model.AppSettings
import org.crux.data.io.FileStorage
import org.crux.data.model.AppearanceSettings
import org.crux.data.model.appearance.AppearanceBag
import org.crux.data.model.appearance.AppearanceKey
import org.crux.data.model.appearance.AppearancePatch
import org.crux.data.model.appearance.GlobalAppearance
import org.crux.data.model.appearance.TimelineAppearance
import org.crux.data.model.appearance.migrateAppearanceV1ToV2
import org.crux.data.model.appearance.toBag
import org.crux.data.model.appearance.toGlobalAppearance
import org.crux.data.model.appearance.toPatch
import org.crux.data.model.appearance.toTimelineAppearance
import org.crux.data.model.tab.LiveWebTimelineSpecs
import org.crux.data.model.tab.TabSettingsV2
import org.crux.data.model.tab.TimelineResolver
import org.crux.data.model.tab.TimelineSlot
import org.crux.data.model.tab.TimelineSlotContent
import org.crux.data.model.tab.UiTimelineTabItem
import org.crux.data.model.tab.findById
import org.crux.data.model.tab.isSystemHomeMixedTimeline
import org.crux.data.model.tab.migrateTabSettingsV1ToV2
import org.crux.data.model.tab.platformTabs
import org.crux.data.model.tab.syncPlatformTabs
import org.crux.data.model.tab.toUiTimelineTabItem
import org.crux.data.model.tab.withSystemHomeMixedTimelineEnabled
import org.koin.core.annotation.Single
import kotlin.native.HiddenFromObjC

@Stable
@Single
@HiddenFromObjC
public class SettingsRepository internal constructor(
    private val fileStorage: FileStorage,
    private val appDataStore: AppDataStore,
    private val timelineResolver: TimelineResolver,
) {
    private val appearanceBagStore by lazy {
        createDataStore(
            name = "appearance_bag.pb",
            serializer = protobufSerializer(AppearanceBag()),
        )
    }

    private val appearanceMigrationMutex = Mutex()
    private var appearanceMigrationCompleted = false
    private val tabSettingsMigrationMutex = Mutex()
    private var tabSettingsMigrationCompleted = false
    private val staleTabsMutex = Mutex()
    private var staleTabsPruned = false

    internal val appearanceBag: Flow<AppearanceBag> by lazy {
        flow {
            ensureAppearanceMigrated()
            emitAll(
                appearanceBagStore.data
                    .distinctUntilChanged(),
            )
        }
    }

    public val appearancePatch: Flow<AppearancePatch> by lazy {
        appearanceBag
            .map { it.toPatch() }
            .distinctUntilChanged()
    }
    public val globalAppearance: Flow<GlobalAppearance> by lazy {
        appearancePatch
            .map { it.toGlobalAppearance() }
            .distinctUntilChanged()
    }
    public val timelineAppearance: Flow<TimelineAppearance> by lazy {
        appearancePatch
            .map { it.toTimelineAppearance() }
            .distinctUntilChanged()
    }
    private val appSettingsStore: DataStore<AppSettings> by lazy { appDataStore.appSettingsStore }
    public val appSettings: Flow<AppSettings> by lazy {
        appSettingsStore.data
    }

    public suspend fun ensureAppearanceMigrated() {
        if (appearanceMigrationCompleted) return
        appearanceMigrationMutex.withLock {
            if (appearanceMigrationCompleted) return
            migrateAppearanceV1ToV2(fileStorage, appearanceBagStore)
            appearanceMigrationCompleted = true
        }
    }

    public suspend fun <T : Any> updateAppearance(
        key: AppearanceKey<T>,
        value: T,
    ) {
        updateAppearance { set(key, value) }
    }

    public suspend fun updateAppearance(block: AppearancePatch.() -> AppearancePatch) {
        ensureAppearanceMigrated()
        appearanceBagStore.updateData { bag ->
            bag.toPatch().block().toBag()
        }
    }

    public suspend fun clearAppearance(key: AppearanceKey<*>) {
        updateAppearance { clear(key) }
    }

    public suspend fun replaceAppearance(patch: AppearancePatch) {
        ensureAppearanceMigrated()
        appearanceBagStore.updateData { patch.toBag() }
    }

    internal suspend fun replaceAppearance(bag: AppearanceBag) {
        ensureAppearanceMigrated()
        appearanceBagStore.updateData { bag }
    }

    internal suspend fun replaceAppearance(settings: AppearanceSettings) {
        ensureAppearanceMigrated()
        appearanceBagStore.updateData { settings.toPatch().toBag() }
    }

//    private val tabSettingsStore by lazy {
//        createDataStore(
//            name = "tab_settings.pb",
//            serializer = protobufSerializer(TabSettings()),
//        )
//    }

//    public val tabSettings: Flow<TabSettings> by lazy {
//        tabSettingsStore.data
//    }
//
//    public suspend fun updateTabSettings(block: TabSettings.() -> TabSettings) {
//        tabSettingsStore.updateData(block)
//    }

    private val tabSettingsV2Store by lazy {
        createDataStore(
            name = "tab_settings_v2.pb",
            serializer = protobufSerializer(TabSettingsV2()),
        )
    }

    internal val tabSettingsV2: Flow<TabSettingsV2> by lazy {
        flow {
            ensureTabSettingsMigrated()
            dropStaleHomeTabsOnce()
            emitAll(tabSettingsV2Store.data)
        }
    }

    public val homeTimelineTabs: Flow<List<UiTimelineTabItem>> by lazy {
        tabSettingsV2
            .distinctUntilChangedBy { it.homeSlots }
            .map { settings ->
                val tabs =
                    pruneStaleSlots(settings.homeSlots, timelineResolver::hasSpec)
                        .map { timelineResolver.toTabItem(it) }
//                        .filter { it.enabled }
                tabs.withSystemHomeMixedTimelineEnabled(
                    enabled = tabs.any { it.isSystemHomeMixedTimeline },
                )
            }
    }

    internal fun homeTimelineTab(id: String): Flow<UiTimelineTabItem?> =
        homeTimelineTabs.map { tabs ->
            tabs.findById(id)
        }

    internal suspend fun updateTabSettingsV2(block: TabSettingsV2.() -> TabSettingsV2) {
        ensureTabSettingsMigrated()
        tabSettingsV2Store.updateData(block)
    }

    public suspend fun removeHomeTimelineTabBySourceId(sourceId: String) {
        updateTabSettingsV2 {
            copy(
                homeSlots =
                    homeSlots
                        .mapNotNull { it.removeSource(sourceId) }
                        .distinctBy { it.id },
            )
        }
    }

    internal suspend fun replaceHomeTimelineTabs(tabs: List<UiTimelineTabItem>) {
        updateTabSettingsV2 {
            val normalizedTabs =
                tabs.withSystemHomeMixedTimelineEnabled(
                    enabled = tabs.any { it.isSystemHomeMixedTimeline },
                )
            copy(
                homeSlots =
                    normalizedTabs
                        .distinctBy { it.id }
                        .map { timelineResolver.toSlot(it) },
            )
        }
    }

    /**
     * Once per launch, before any tab is emitted: forgets saved home tabs whose timeline spec no
     * longer exists (a removed platform's tabs), so they never reach the screen.
     */
    internal suspend fun dropStaleHomeTabsOnce() {
        if (staleTabsPruned) return
        staleTabsMutex.withLock {
            if (staleTabsPruned) return
            tabSettingsV2Store.updateData { settings ->
                val kept = pruneStaleSlots(settings.homeSlots, timelineResolver::hasSpec)
                if (kept == settings.homeSlots) settings else settings.copy(homeSlots = kept)
            }
            staleTabsPruned = true
        }
    }

    public suspend fun ensureTabSettingsMigrated() {
        if (tabSettingsMigrationCompleted) return
        tabSettingsMigrationMutex.withLock {
            if (tabSettingsMigrationCompleted) return
            migrateTabSettingsV1ToV2(
                fileStorage = fileStorage,
                tabSettingsV2Store = tabSettingsV2Store,
            )
            tabSettingsMigrationCompleted = true
        }
    }

    /** Adds the Reddit tab once, so removing it in tab settings sticks. */
    public suspend fun ensureRedditTabAdded() {
        val tabs = homeTimelineTabs.first()
        if (tabs.isEmpty()) return
        val redditTab = LiveWebTimelineSpecs.redditCandidate.toUiTimelineTabItem()
        if (appSettings.first().redditTabAdded) {
            // Tabs added before the Reddit icon existed saved a globe.
            if (tabs.any { it.id == redditTab.id && it.icon != redditTab.icon }) {
                replaceHomeTimelineTabs(tabs.map { if (it.id == redditTab.id) redditTab else it })
            }
            return
        }
        replaceHomeTimelineTabs(tabs + redditTab)
        updateAppSettings { copy(redditTabAdded = true) }
    }

    /** Replaces the old native X tabs with one live X tab, once. */
    internal suspend fun replaceNativeXTabs(xAccountKeys: Set<String>) {
        if (appSettings.first().xTabAdded) return
        val tabs = homeTimelineTabs.first()
        if (tabs.isEmpty()) return
        val kept = tabs.filterNot { tab -> xAccountKeys.any { tab.id.endsWith(":$it") } }
        replaceHomeTimelineTabs(kept + LiveWebTimelineSpecs.xCandidate.toUiTimelineTabItem())
        updateAppSettings { copy(xTabAdded = true) }
    }

    /**
     * Crux: makes the home pills (X, Instagram, LinkedIn, Reddit and "All RSS") match the platforms
     * the person picked, keeping their order. Only called when the platform choice changes, so pills
     * removed in tab settings stay removed.
     */
    public suspend fun syncLiveWebTabs(
        enabledPlatforms: Set<String>,
        platformsToAdd: Set<String> = enabledPlatforms,
    ) {
        val tabs = homeTimelineTabs.first()
        val updated =
            syncPlatformTabs(
                current = tabs,
                enabledPlatforms = enabledPlatforms,
                platformsToAdd = platformsToAdd,
            )
        if (updated.map { it.id } != tabs.map { it.id }) {
            replaceHomeTimelineTabs(updated)
        }
        updateAppSettings { copy(redditTabAdded = true, xTabAdded = true, liveWebTabsMigrated = true, homeTabsNormalized = true) }
    }

    /**
     * Once, for installs made before Instagram and LinkedIn had static pills: swaps their
     * account-based pills for the static ones and adds a pill for an enabled platform that has none
     * (it used to appear only after a sign-in). Skipped until onboarding is done, because the
     * onboarding choice does the same job.
     */
    public suspend fun ensureLiveWebTabsMigrated() {
        val settings = appSettings.first()
        if (!settings.onboardingCompleted) return
        if (settings.liveWebTabsMigrated && settings.homeTabsNormalized) return
        // Older installs can have the empty combined "Home" tab and no "All RSS" pill: give every
        // enabled platform its pill once, in place of the combined tab.
        val tabs = homeTimelineTabs.first()
        val updated = syncPlatformTabs(current = tabs, enabledPlatforms = settings.cruxPlatforms.toSet())
        if (updated.map { it.id } != tabs.map { it.id }) {
            replaceHomeTimelineTabs(updated)
        }
        updateAppSettings { copy(liveWebTabsMigrated = true, homeTabsNormalized = true) }
    }

    public suspend fun updateAppSettings(block: AppSettings.() -> AppSettings) {
        appSettingsStore.updateData(block)
    }

    private inline fun <reified T> createDataStore(
        name: String,
        serializer: OkioSerializer<T>,
    ): DataStore<T> =
        DataStoreFactory.create(
            storage =
                createDataStoreStorage(
                    name = name,
                    serializer = serializer,
                    fileStorage = fileStorage,
                ),
        )
}

private fun TimelineSlot.removeSource(sourceId: String): TimelineSlot? =
    when (val slotContent = content) {
        is TimelineSlotContent.Source -> {
            if (slotContent.source.id == sourceId) {
                null
            } else {
                this
            }
        }

        is TimelineSlotContent.Group -> {
            val sanitizedChildren =
                slotContent.children
                    .mapNotNull { it.removeSource(sourceId) }
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
