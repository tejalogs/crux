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
import org.crux.common.InAppNotification
import org.crux.common.Message
import org.crux.common.PagingState
import org.crux.common.emptyFlow
import org.crux.common.onEmpty
import org.crux.common.onError
import org.crux.common.onSuccess
import org.crux.common.toPagingState
import org.crux.data.database.cache.CacheDatabase
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
import org.crux.data.model.tab.TimelineFilterConfig
import org.crux.data.model.tab.TimelinePostContent
import org.crux.data.model.tab.TimelinePostKind
import org.crux.data.repository.KeywordFilterPattern
import org.crux.data.repository.LocalFilterRepository
import org.crux.data.repository.LoginExpiredException
import org.crux.data.repository.MxgaRepository
import org.crux.data.repository.SettingsRepository
import org.crux.data.repository.isMxgaMatch
import org.crux.di.koinInject
import org.crux.model.PlatformRegistry
import org.crux.model.ReferenceType
import org.crux.ui.model.UiMedia
import org.crux.ui.model.UiTimelineV2
import org.crux.ui.model.asTimelinePostItem
import org.crux.ui.presenter.PresenterBase
import org.crux.web.shared.WebPresenter

public class TimelinePresenterOptions(
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
                        ).cachedIn(scope).map { pagingData ->
                            pagingData.map { item -> item.toUi() }
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
                        // Imported filters are sanitised when the backup is imported; the user's own
                        // filters are applied as they wrote them.
                        val keywordFilters = filterList
                        pager
                            .filter { item ->
                                item.matchesKeywordFilters(keywordFilters) &&
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
                        notifyError = notifyError,
                    ).also { contextMediator.value = it }
                } else {
                    TimelineRemoteMediator(
                        loader = loader,
                        database = database,
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
        // Bounds the work a single regex filter can do per post, even for a pattern that slips past
        // the backtracking checks below.
        filter.regex?.containsMatchIn(take(MAX_TIMELINE_REGEX_INPUT_LENGTH)) == true
    } else {
        // Plain keywords are literal text; a blank one would otherwise match (and hide) every post.
        filter.keyword.isNotBlank() && contains(filter.keyword, ignoreCase = true)
    }

/** At most this many keyword filters are applied to a timeline; the rest are ignored. */
internal const val MAX_TIMELINE_KEYWORD_FILTERS: Int = 500

/** Plain keywords longer than this are ignored (no real phrase filter needs more). */
internal const val MAX_TIMELINE_KEYWORD_LENGTH: Int = 500

/** Regex filters longer than this are ignored. */
internal const val MAX_TIMELINE_REGEX_LENGTH: Int = 200

/** Regex filters only look at this many characters of a post's text. */
internal const val MAX_TIMELINE_REGEX_INPUT_LENGTH: Int = 2_000

/**
 * A regex filter may use at most one unbounded quantifier (`*`, `+`, `{n,}`): two or more over
 * overlapping text (`.*.*!`, `\w*\w*!`) backtrack polynomially and can take minutes on one post.
 */
private const val MAX_REGEX_UNBOUNDED_QUANTIFIERS = 1

/**
 * Upper bound on the product of the bounded choice points in a regex filter (`?` counts 2,
 * `{2,5}` counts 4, `{3}` counts 1), which bounds how much backtracking they can add.
 */
private const val MAX_REGEX_CHOICE_PRODUCT = 32

/** A bounded repeat wider than this (`{0,100}`) is treated as unbounded. */
private const val MAX_BOUNDED_REPEAT_WIDTH = 16

/**
 * Ordinary short posts in several scripts. A filter that matches at least
 * [MATCH_EVERYTHING_PROBE_THRESHOLD] of them (`e`, `\w`, `\b`, `[a-z]`, `\s`, `[^!]`...) would hide
 * practically every post, so it is dropped.
 */
private val MATCH_EVERYTHING_PROBES =
    listOf(
        "Hello world",
        "Breaking news today",
        "12 photos from the trip",
        "Have a great day",
        "Watch the full video at 7pm",
        "こんにちは、元気ですか",
        "Привет всем",
        "¿Qué tal? 😀",
        "OK",
        "#weekend vibes 🎉",
    )

private const val MATCH_EVERYTHING_PROBE_THRESHOLD = 4

/**
 * Drops keyword filters that would freeze or empty the timeline instead of filtering it: blank or
 * overlong keywords, invalid or overlong regexes, regexes that can backtrack catastrophically
 * (see [hasCatastrophicBacktrackingRisk]; filters run on every item as the timeline loads), and
 * filters that match the empty string or most ordinary posts. Filters can come from an imported
 * backup, so they are not trusted to be well-formed. Plain (non-regex) keywords are always
 * matched as literal text.
 */
internal fun List<KeywordFilterPattern>.safeForTimeline(): List<KeywordFilterPattern> =
    asSequence()
        .filter { isSafeKeywordFilter(keyword = it.keyword, isRegex = it.isRegex, regex = it.regex) }
        .take(MAX_TIMELINE_KEYWORD_FILTERS)
        .toList()

internal fun isSafeKeywordFilter(
    keyword: String,
    isRegex: Boolean,
    regex: Regex?,
): Boolean {
    if (keyword.isBlank()) return false
    if (!isRegex) {
        return keyword.length <= MAX_TIMELINE_KEYWORD_LENGTH &&
            !matchesMostProbes { it.contains(keyword, ignoreCase = true) }
    }
    if (regex == null || keyword.length > MAX_TIMELINE_REGEX_LENGTH) return false
    if (hasCatastrophicBacktrackingRisk(keyword)) return false
    if (regex.containsMatchIn("")) return false
    return !matchesMostProbes { regex.containsMatchIn(it) }
}

private fun matchesMostProbes(matches: (String) -> Boolean): Boolean =
    MATCH_EVERYTHING_PROBES.count(matches) >= MATCH_EVERYTHING_PROBE_THRESHOLD

private class RegexGroupFrame {
    var hasQuantifier = false
    var hasAlternation = false
}

/**
 * True when [pattern] has a shape behind catastrophic backtracking:
 * - a repeated (`*`, `+`, `{..}`) group that contains a quantifier or an alternation
 *   (`(a+)+`, `(a*b?)*`, `(\w|\d)+`);
 * - more than [MAX_REGEX_UNBOUNDED_QUANTIFIERS] unbounded quantifier (`.*.*!`, `\w*\w*!`);
 * - bounded choice points whose combined width exceeds [MAX_REGEX_CHOICE_PRODUCT] (`a?a?a?...`).
 *
 * Escapes and character classes are skipped. The check is deliberately conservative: a filter it
 * rejects is ignored, never applied.
 */
internal fun hasCatastrophicBacktrackingRisk(pattern: String): Boolean {
    val frames = ArrayDeque<RegexGroupFrame>().apply { addLast(RegexGroupFrame()) }
    var unbounded = 0
    var choiceProduct = 1
    var i = 0
    while (i < pattern.length) {
        when (pattern[i]) {
            '\\' -> {
                i += 2
            }

            '[' -> {
                i = skipCharacterClass(pattern, i)
            }

            '(' -> {
                frames.addLast(RegexGroupFrame())
                i = skipGroupModifier(pattern, i + 1)
            }

            ')' -> {
                val inner = if (frames.size > 1) frames.removeLast() else RegexGroupFrame()
                // `?` after a group is zero-or-one, not repetition.
                val repeated = parseQuantifier(pattern, i + 1) != null && pattern[i + 1] != '?'
                if (repeated && (inner.hasQuantifier || inner.hasAlternation)) return true
                if (inner.hasQuantifier) frames.last().hasQuantifier = true
                i++
            }

            '|' -> {
                frames.last().hasAlternation = true
                i++
            }

            else -> {
                val quantifier = parseQuantifier(pattern, i)
                if (quantifier == null) {
                    i++
                } else {
                    frames.last().hasQuantifier = true
                    if (quantifier.width == UNBOUNDED_WIDTH) {
                        unbounded++
                    } else {
                        choiceProduct = (choiceProduct * quantifier.width).coerceAtMost(MAX_REGEX_CHOICE_PRODUCT + 1)
                    }
                    if (unbounded > MAX_REGEX_UNBOUNDED_QUANTIFIERS || choiceProduct > MAX_REGEX_CHOICE_PRODUCT) {
                        return true
                    }
                    i = quantifier.end
                }
            }
        }
    }
    return false
}

private const val UNBOUNDED_WIDTH = -1

/** A quantifier spanning up to [end] (exclusive) that allows [width] repeat counts, or [UNBOUNDED_WIDTH]. */
private class RegexQuantifier(
    val width: Int,
    val end: Int,
)

/** Parses `*`, `+`, `?` or `{n}`/`{n,}`/`{n,m}` at [start], plus a lazy `?` or possessive `+` suffix. */
private fun parseQuantifier(
    pattern: String,
    start: Int,
): RegexQuantifier? {
    val (width, end) =
        when (pattern.getOrNull(start)) {
            '*', '+' -> UNBOUNDED_WIDTH to start + 1

            '?' -> 2 to start + 1

            '{' -> parseBraceQuantifier(pattern, start) ?: return null

            else -> return null
        }
    val suffix = pattern.getOrNull(end)
    return RegexQuantifier(width = width, end = if (suffix == '?' || suffix == '+') end + 1 else end)
}

private fun parseBraceQuantifier(
    pattern: String,
    start: Int,
): Pair<Int, Int>? {
    val close = pattern.indexOf('}', start)
    if (close < 0) return null
    val parts = pattern.substring(start + 1, close).split(',')
    val min = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val width =
        when {
            parts.size == 1 -> 1

            parts.size == 2 && parts[1].isEmpty() -> UNBOUNDED_WIDTH

            parts.size == 2 -> {
                val max = parts[1].toIntOrNull() ?: return null
                val span = max - min + 1
                if (span > MAX_BOUNDED_REPEAT_WIDTH) UNBOUNDED_WIDTH else span.coerceAtLeast(1)
            }

            else -> return null
        }
    return width to close + 1
}

/** Returns the index just after the `]` that closes the character class opened at [start]. */
private fun skipCharacterClass(
    pattern: String,
    start: Int,
): Int {
    var i = start + 1
    if (pattern.getOrNull(i) == '^') i++
    if (pattern.getOrNull(i) == ']') i++
    while (i < pattern.length && pattern[i] != ']') {
        i += if (pattern[i] == '\\') 2 else 1
    }
    return i + 1
}

/** Skips `?:`, `?=`, `?!`, `?<=`, `?<!`, `?<name>` and inline flags such as `?i` after `(`. */
private fun skipGroupModifier(
    pattern: String,
    start: Int,
): Int {
    if (pattern.getOrNull(start) != '?') return start
    var i = start + 1
    when (pattern.getOrNull(i)) {
        ':', '=', '!', '>' -> {
            return i + 1
        }

        '<' -> {
            val next = pattern.getOrNull(i + 1)
            if (next == '=' || next == '!') return i + 2
            while (i < pattern.length && pattern[i] != '>') i++
            return i + 1
        }
    }
    while (i < pattern.length && (pattern[i].isLetter() || pattern[i] == '-')) i++
    if (pattern.getOrNull(i) == ':') i++
    return i
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
