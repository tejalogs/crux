package org.crux.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.filter
import androidx.paging.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.crux.common.InAppNotification
import org.crux.common.Message
import org.crux.common.PagingState
import org.crux.common.PlatformDispatchers
import org.crux.common.emptyFlow
import org.crux.common.onEmpty
import org.crux.common.onError
import org.crux.common.onSuccess
import org.crux.common.toPagingState
import org.crux.data.database.cache.CacheDatabase
import org.crux.data.database.cache.model.TranslationDisplayOptions
import org.crux.data.datasource.microblog.offsetPagingConfig
import org.crux.data.datasource.microblog.paging.CacheableRemoteLoader
import org.crux.data.datasource.microblog.paging.NotSupportRemoteLoader
import org.crux.data.datasource.microblog.paging.OffsetFromStartPagingSource
import org.crux.data.datasource.microblog.paging.PostContextLoader
import org.crux.data.datasource.microblog.paging.PostContextRemoteMediator
import org.crux.data.datasource.microblog.paging.RemoteLoader
import org.crux.data.datasource.microblog.paging.TimelineDbPageCache
import org.crux.data.datasource.microblog.paging.TimelineDbPageLoader
import org.crux.data.datasource.microblog.paging.TimelinePageItem
import org.crux.data.datasource.microblog.paging.TimelineRemoteMediator
import org.crux.data.datasource.microblog.paging.notSupported
import org.crux.data.datasource.microblog.paging.toTimelinePagingSource
import org.crux.data.datasource.microblog.pagingConfig
import org.crux.data.datastore.AppDataStore
import org.crux.data.model.tab.TimelineFilterConfig
import org.crux.data.model.tab.TimelinePostContent
import org.crux.data.model.tab.TimelinePostKind
import org.crux.data.repository.KeywordFilterPattern
import org.crux.data.repository.LocalFilterRepository
import org.crux.data.repository.LoginExpiredException
import org.crux.data.repository.MxgaRepository
import org.crux.data.repository.SettingsRepository
import org.crux.data.repository.isMxgaMatch
import org.crux.data.translation.PreTranslationService
import org.crux.data.translation.TranslationSettingsSupport
import org.crux.di.koinInject
import org.crux.model.PlatformRegistry
import org.crux.model.ReferenceType
import org.crux.ui.model.UiMedia
import org.crux.ui.model.UiTimelineV2
import org.crux.ui.model.asTimelinePostItem
import org.crux.ui.presenter.PresenterBase
import org.crux.web.shared.WebPresenter

public class TimelinePresenterOptions(
    public val allowLongTextTranslationDisplay: (RemoteLoader<UiTimelineV2>) -> Boolean = { false },
    public val transform: suspend (UiTimelineV2) -> UiTimelineV2 = { it },
)

@OptIn(ExperimentalPagingApi::class)
@WebPresenter(name = "timeline", creatable = false)
public open class TimelinePresenter : PresenterBase<TimelineState> {
    private val baseLoader: Flow<RemoteLoader<UiTimelineV2>>
    public open val loader: Flow<RemoteLoader<UiTimelineV2>>
        get() = baseLoader
    private val options: TimelinePresenterOptions

    private val database: CacheDatabase by koinInject()
    private val appDataStore: AppDataStore by koinInject()
    private val preTranslationService: PreTranslationService by koinInject()
    private val settingsRepository: SettingsRepository by koinInject()
    private val mxgaRepository: MxgaRepository by koinInject()
    private val platformRegistry: PlatformRegistry by koinInject()

    private val localFilterRepository: LocalFilterRepository by koinInject()
    private val inAppNotification: InAppNotification by koinInject()

    private val filterFlow: Flow<List<KeywordFilterPattern>> by lazy {
        localFilterRepository.getFlow(forTimeline = true)
    }

    private val timelineTabItemId: String?
    private val isHomeTimeline: Boolean
    private val contextMediator = MutableStateFlow<PostContextRemoteMediator?>(null)

    private val timelineFilterConfigFlow: Flow<TimelineFilterConfig> by lazy {
        observeTimelineFilterConfig(
            settingsRepository = settingsRepository,
            timelineTabItemId = timelineTabItemId,
        )
    }

    private val mxgaEnabledFlow: Flow<Boolean> by lazy {
        settingsRepository.appSettings
            .map { it.mxgaEnabled }
            .distinctUntilChanged()
    }

    private val translationSettingsFlow: Flow<TranslationDisplayOptions> by lazy {
        TranslationSettingsSupport.displayOptionsFlow(appDataStore)
    }

    public constructor(
        tabId: String? = null,
        loader: Flow<RemoteLoader<UiTimelineV2>> = flowOf(notSupported()),
        options: TimelinePresenterOptions = TimelinePresenterOptions(),
        isHomeTimeline: Boolean = false,
    ) : super() {
        this.baseLoader = loader
        this.options = options
        this.timelineTabItemId = tabId
        this.isHomeTimeline = isHomeTimeline
    }

    internal open fun allowLongTextTranslationDisplay(loader: RemoteLoader<UiTimelineV2>): Boolean =
        options.allowLongTextTranslationDisplay(loader)

    @OptIn(ExperimentalCoroutinesApi::class)
    internal fun createPager(scope: CoroutineScope): Flow<PagingData<UiTimelineV2>> =
        loader
            .flatMapLatest { remoteLoader ->
                contextMediator.value = null
                when (remoteLoader) {
                    is NotSupportRemoteLoader<UiTimelineV2> -> {
                        PagingData.emptyFlow(isError = false)
                    }

                    is CacheableRemoteLoader<UiTimelineV2> -> {
                        cachePager(
                            loader = remoteLoader,
                        ).cachedIn(scope).flatMapLatest { pagingData ->
                            translationSettingsFlow
                                .map { translationDisplayOptions ->
                                    withContext(PlatformDispatchers.IO) {
                                        pagingData.map { item ->
                                            item.toUi(translationDisplayOptions)
                                        }
                                    }
                                }
                        }
                    }

                    else -> {
                        networkPager(
                            loader = remoteLoader,
                        ).cachedIn(scope)
                    }
                }.flatMapLatest { pager ->
                    combine(
                        filterFlow,
                        timelineFilterConfigFlow,
                        mxgaEnabledFlow,
                        mxgaRepository.snapshot,
                    ) { filterList, timelineFilterConfig, mxgaEnabled, mxgaSnapshot ->
                        pager
                            .filter { item ->
                                item.matchesKeywordFilters(filterList) &&
                                    item.matchesTimelineFilter(timelineFilterConfig) &&
                                    (!mxgaEnabled || !item.isMxgaMatch(mxgaSnapshot, platformRegistry))
                            }.map {
                                transform(it)
                            }
                    }
                }
            }.catch {
                emitAll(PagingData.emptyFlow(isError = true))
            }

    private fun cachePager(loader: CacheableRemoteLoader<UiTimelineV2>): Flow<PagingData<TimelinePageItem>> =
        run {
            val allowLongText = allowLongTextTranslationDisplay(loader)
            val pageCache = TimelineDbPageCache()
            val notifyError: (Throwable) -> Unit = { e ->
                if (e is LoginExpiredException) {
                    inAppNotification.onError(Message.LoginExpired, e)
                }
            }
            val mediator =
                if (loader is PostContextLoader) {
                    PostContextRemoteMediator(
                        loader = loader,
                        database = database,
                        preTranslationService = preTranslationService,
                        notifyError = notifyError,
                    ).also { contextMediator.value = it }
                } else {
                    TimelineRemoteMediator(
                        loader = loader,
                        database = database,
                        allowLongText = allowLongText,
                        preTranslationService = preTranslationService,
                        notifyError = notifyError,
                        refreshOnInitialize = {
                            shouldRefreshTimelineOnInitialize(isHomeTimeline) {
                                settingsRepository.appSettings.first().refreshHomeTimelineOnLaunch
                            }
                        },
                    )
                }
            Pager(
                config = offsetPagingConfig,
                remoteMediator = mediator,
                pagingSourceFactory = {
                    OffsetFromStartPagingSource(
                        TimelineDbPageLoader(
                            database = database,
                            pagingKey = loader.pagingKey,
                            pageCache = pageCache,
                        ),
                    )
                },
            ).flow
        }

    protected open suspend fun transform(data: UiTimelineV2): UiTimelineV2 = options.transform(data)

    private fun networkPager(loader: RemoteLoader<UiTimelineV2>): Flow<PagingData<UiTimelineV2>> =
        Pager(
            config = pagingConfig,
            pagingSourceFactory = {
                loader.toTimelinePagingSource()
            },
        ).flow

    @OptIn(ExperimentalCoroutinesApi::class)
    @Composable
    final override fun body(): TimelineState {
        val scope = rememberCoroutineScope()
        val items =
            remember {
                createPager(scope)
            }.collectAsLazyPagingItems()
        val contextLoadStates by remember {
            contextMediator.flatMapLatest { it?.loadStates ?: flowOf(null) }
        }.collectAsState(null)
        val retry =
            remember(items) {
                {
                    contextMediator.value?.prepareRetry()
                    items.retry()
                }
            }
        val listState = items.toPagingState(contextLoadStates, retry)
        return object : TimelineState {
            override val listState = listState

            override fun refreshAsync() {
                scope.launch {
                    refresh()
                }
            }

            override suspend fun refresh() {
                listState
                    .onSuccess {
                        refreshSuspend()
                    }.onEmpty {
                        refresh()
                    }.onError {
                        onRetry()
                    }
            }
        }
    }
}

internal suspend fun shouldRefreshTimelineOnInitialize(
    isHomeTimeline: Boolean,
    refreshHomeTimelineOnLaunch: suspend () -> Boolean,
): Boolean = !isHomeTimeline || refreshHomeTimelineOnLaunch()

@Immutable
public interface TimelineState {
    public val listState: PagingState<UiTimelineV2>

    public fun refreshAsync()

    public suspend fun refresh()
}

internal fun UiTimelineV2.matchesKeywordFilters(filters: List<KeywordFilterPattern>): Boolean =
    if (filters.isEmpty()) {
        true
    } else {
        !filters.any { filter ->
            searchText.orEmpty().matches(filter)
        }
    }

private fun String.matches(filter: KeywordFilterPattern): Boolean =
    if (filter.isRegex) {
        filter.regex?.containsMatchIn(this) == true
    } else {
        contains(filter.keyword, ignoreCase = true)
    }

internal fun UiTimelineV2.matchesTimelineFilter(filterConfig: TimelineFilterConfig): Boolean {
    if (filterConfig.excludedKinds.isEmpty() && filterConfig.excludedContents.isEmpty()) {
        return true
    }
    val post = asTimelinePostItem() ?: return true
    val traits = post.traits()
    return filterConfig.excludedKinds.none(traits.kinds::contains) &&
        filterConfig.excludedContents.none(traits.contents::contains)
}

internal data class TimelinePostTraits(
    val kinds: Set<TimelinePostKind>,
    val contents: Set<TimelinePostContent>,
)

internal fun UiTimelineV2.TimelinePostItem.traits(): TimelinePostTraits {
    val visiblePost = displayPost
    val kinds =
        buildSet {
            val currentUserKey = visiblePost.user?.key
            val hasParentFromOtherUser =
                currentUserKey != null &&
                    presentation.inlineParents.any { parent ->
                        parent.displayPost.user
                            ?.key
                            ?.let { it != currentUserKey } == true
                    }
            if (hasParentFromOtherUser) {
                add(TimelinePostKind.Reply)
            }
            if (presentation.repost != null) {
                add(TimelinePostKind.Repost)
            }
            if (presentation.quotes.isNotEmpty()) {
                add(TimelinePostKind.Quote)
            }
            if (
                visiblePost.references.none { it.type == ReferenceType.Reply } &&
                presentation.inlineParents.isEmpty() &&
                presentation.repost == null &&
                presentation.quotes.isEmpty()
            ) {
                add(TimelinePostKind.Original)
            }
        }
    val contents =
        buildSet {
            val hasVisualMedia =
                visiblePost.images.any {
                    it is UiMedia.Image || it is UiMedia.Gif ||
                        it is UiMedia.Video
                }
            if (visiblePost.content.original.raw
                    .isNotBlank() && !hasVisualMedia
            ) {
                add(TimelinePostContent.Text)
            }
            if (visiblePost.images.any { it is UiMedia.Image || it is UiMedia.Gif }) {
                add(TimelinePostContent.Image)
            }
            if (visiblePost.images.any { it is UiMedia.Video }) {
                add(TimelinePostContent.Video)
            }
            if (visiblePost.isTimelineFilterEmpty()) {
                add(TimelinePostContent.Other)
            }
        }
    return TimelinePostTraits(
        kinds = kinds,
        contents = contents,
    )
}

private fun UiTimelineV2.Post.isTimelineFilterEmpty(): Boolean =
    content.original.raw.isBlank() &&
        contentWarning?.original?.raw.isNullOrBlank() &&
        images.isEmpty() &&
        poll == null &&
        card == null

internal fun observeTimelineFilterConfig(
    settingsRepository: SettingsRepository,
    timelineTabItemId: String?,
): Flow<TimelineFilterConfig> =
    if (timelineTabItemId == null) {
        flowOf(TimelineFilterConfig())
    } else {
        settingsRepository
            .homeTimelineTab(timelineTabItemId)
            .map { it?.filterConfig ?: TimelineFilterConfig() }
            .distinctUntilChanged()
    }
