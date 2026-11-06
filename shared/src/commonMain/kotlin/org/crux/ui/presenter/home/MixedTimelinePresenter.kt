package org.crux.ui.presenter.home

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.crux.data.database.cache.CacheDatabase
import org.crux.data.datasource.microblog.MixedRemoteMediator
import org.crux.data.datasource.microblog.paging.CacheableRemoteLoader
import org.crux.data.datasource.microblog.paging.RemoteLoader
import org.crux.data.datasource.microblog.paging.notSupported
import org.crux.data.model.tab.TimelineMergePolicy
import org.crux.data.model.tab.TimelineResolver
import org.crux.data.model.tab.UiGroupTimelineTabItem
import org.crux.data.model.tab.UiTimelineTabItem
import org.crux.data.model.tab.isSystemHomeMixedTimeline
import org.crux.data.repository.SettingsRepository
import org.crux.di.koinInject
import org.crux.ui.model.UiTimelineV2

public class MixedTimelinePresenter(
    id: String,
    private val fallbackSubTimelinePresenter: List<TimelinePresenter> = emptyList(),
    private val fallbackMergePolicy: TimelineMergePolicy = TimelineMergePolicy.Time,
) : TimelinePresenter(tabId = id) {
    private val groupId = id

    public constructor(
        subTimelinePresenter: List<TimelinePresenter>,
        mergePolicy: TimelineMergePolicy = TimelineMergePolicy.Time,
    ) : this(
        id = "legacy_mixed_timeline",
        fallbackSubTimelinePresenter = subTimelinePresenter,
        fallbackMergePolicy = mergePolicy,
    )

    private val database: CacheDatabase by koinInject()
    private val settingsRepository: SettingsRepository by koinInject()
    private val timelineResolver: TimelineResolver by koinInject()

    private val groupTabFlow: Flow<UiGroupTimelineTabItem?> by lazy {
        settingsRepository
            .homeTimelineTab(groupId)
            .map { it as? UiGroupTimelineTabItem }
    }

    private val mergePolicyFlow: Flow<TimelineMergePolicy> by lazy {
        groupTabFlow
            .map { it?.mergePolicy ?: fallbackMergePolicy }
            .distinctUntilChanged()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val subTimelineLoadersFlow: Flow<List<RemoteLoader<UiTimelineV2>>> by lazy {
        groupTabFlow
            .map { group ->
                group
                    ?.children
                    ?.filter { it.enabled }
            }.distinctUntilChanged { old, new ->
                old.orEmpty().map { it.id } == new.orEmpty().map { it.id }
            }.flatMapLatest { tabs ->
                if (tabs == null) {
                    if (fallbackSubTimelinePresenter.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        combine(fallbackSubTimelinePresenter.map { it.loader }) { it.toList() }
                    }
                } else if (tabs.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(tabs.map { timelineResolver.resolveLoader(it) }) { it.toList() }
                }
            }
    }

    override val loader: Flow<RemoteLoader<UiTimelineV2>>
        get() =
            combine(subTimelineLoadersFlow, mergePolicyFlow) { loaders, mergePolicy ->
                if (loaders.isEmpty()) {
                    notSupported()
                } else {
                    MixedRemoteMediator(
                        database = database,
                        mediators = loaders.filterIsInstance<CacheableRemoteLoader<UiTimelineV2>>(),
                        mergePolicy = mergePolicy,
                    )
                }
            }
}

public class SystemHomeMixedTimelinePresenter(
    id: String,
    isHomeTimeline: Boolean = false,
) : TimelinePresenter(tabId = id, isHomeTimeline = isHomeTimeline) {
    private val groupId = id

    private val database: CacheDatabase by koinInject()
    private val settingsRepository: SettingsRepository by koinInject()
    private val timelineResolver: TimelineResolver by koinInject()

    private val groupTabFlow: Flow<UiGroupTimelineTabItem?> by lazy {
        settingsRepository
            .homeTimelineTab(groupId)
            .map { it as? UiGroupTimelineTabItem }
    }

    private val mergePolicyFlow: Flow<TimelineMergePolicy> by lazy {
        groupTabFlow
            .map { it?.mergePolicy ?: TimelineMergePolicy.Time }
            .distinctUntilChanged()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val subTimelineLoadersFlow: Flow<List<RemoteLoader<UiTimelineV2>>> by lazy {
        settingsRepository.homeTimelineTabs
            .map { tabs ->
                tabs
                    .filterNot { it.isSystemHomeMixedTimeline }
                    .filter { it.enabled }
            }.distinctUntilChangedByTabIds()
            .flatMapLatest { tabs ->
                if (tabs.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(tabs.map { timelineResolver.resolveLoader(it) }) { it.toList() }
                }
            }
    }

    override val loader: Flow<RemoteLoader<UiTimelineV2>>
        get() =
            combine(subTimelineLoadersFlow, mergePolicyFlow) { loaders, mergePolicy ->
                if (loaders.isEmpty()) {
                    notSupported()
                } else {
                    MixedRemoteMediator(
                        database = database,
                        mediators = loaders.filterIsInstance<CacheableRemoteLoader<UiTimelineV2>>(),
                        mergePolicy = mergePolicy,
                    )
                }
            }
}

private fun Flow<List<UiTimelineTabItem>>.distinctUntilChangedByTabIds(): Flow<List<UiTimelineTabItem>> =
    distinctUntilChanged { old, new ->
        old.map { it.id } == new.map { it.id }
    }
