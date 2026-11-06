package org.crux.data.model.tab

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import org.crux.data.database.app.model.DbLocalFollow
import org.crux.data.datasource.microblog.paging.CacheableRemoteLoader
import org.crux.data.datasource.microblog.paging.PacedRemoteLoader
import org.crux.data.datasource.microblog.paging.PagingRequest
import org.crux.data.datasource.microblog.paging.PagingResult
import org.crux.data.datasource.microblog.paging.RemoteLoader
import org.crux.data.datasource.microblog.paging.RequestGate
import org.crux.data.platform.INSTAGRAM_PLATFORM_ID
import org.crux.data.platform.LINKEDIN_PLATFORM_ID
import org.crux.data.repository.LocalFollowRepository
import org.crux.di.koinInject
import org.crux.model.AccountType
import org.crux.ui.model.UiIcon
import org.crux.ui.model.UiStrings
import org.crux.ui.model.UiTimelineV2
import org.crux.ui.model.asType
import org.koin.core.annotation.Single
import kotlin.native.HiddenFromObjC

/** A null [groupId] means every local follow. */
@HiddenFromObjC
@Serializable
public data class LocalFollowsTimelineData(
    val groupId: String? = null,
) : TimelineSpec.Data

/**
 * Limits for reading many profiles in a row. Cookie-session platforms get one request every few
 * seconds and a small cap, because a burst of profile reads looks automated.
 */
internal object LocalFollowLimits {
    const val COOKIE_SESSION_MAX_ACCOUNTS = 20
    const val COOKIE_SESSION_GAP_MILLIS = 2_000L
    const val DEFAULT_GAP_MILLIS = 200L

    private val cookieSessionPlatforms = setOf("vvo")
    private val unsupportedPlatforms = setOf(INSTAGRAM_PLATFORM_ID, LINKEDIN_PLATFORM_ID, "xQt")

    fun isSupported(platformId: String): Boolean = platformId !in unsupportedPlatforms

    fun isCookieSession(platformId: String): Boolean = platformId in cookieSessionPlatforms

    /** Drops unsupported platforms and keeps the newest follows up to the cookie-session cap. */
    fun select(follows: List<DbLocalFollow>): List<DbLocalFollow> {
        val (cookieSession, others) =
            follows
                .filter { isSupported(it.platformId) }
                .partition { isCookieSession(it.platformId) }
        return others +
            cookieSession
                .sortedByDescending { it.createdAt }
                .take(COOKIE_SESSION_MAX_ACCOUNTS)
    }
}

@HiddenFromObjC
public object LocalFollowTimelineSpecs {
    public val follows: TimelineSpec<LocalFollowsTimelineData> =
        TimelineSpec(
            id = TimelineSpecIds.LOCAL_FOLLOWS,
            title = UiStrings.List,
            icon = UiIcon.List.asType(),
            serializer = LocalFollowsTimelineData.serializer(),
            targetId = { it.groupId ?: "all" },
            loaderFactory = KoinLocalFollowsLoaderFactory,
        )
}

@Single
internal class LocalFollowsLoaderFactory(
    private val repository: LocalFollowRepository,
) : TimelineLoaderFactory<LocalFollowsTimelineData> {
    // Shared by every local feed so two tabs can't add up to a burst.
    private val cookieSessionGate = RequestGate(LocalFollowLimits.COOKIE_SESSION_GAP_MILLIS)
    private val defaultGate = RequestGate(LocalFollowLimits.DEFAULT_GAP_MILLIS)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun create(
        data: LocalFollowsTimelineData,
        context: TimelineLoaderContext,
    ): Flow<RemoteLoader<UiTimelineV2>> =
        repository
            .follows(data.groupId)
            .map(LocalFollowLimits::select)
            .distinctUntilChanged { old, new ->
                old.map { it.accountKey to it.userKey } == new.map { it.accountKey to it.userKey }
            }.flatMapLatest { follows ->
                if (follows.isEmpty()) {
                    flowOf(NoLocalFollowsLoader)
                } else {
                    combine(follows.groupBy { it.accountKey }.values.map { loaders(it, context) }) { groups ->
                        val loaders = groups.toList().flatten()
                        if (loaders.isEmpty()) {
                            NoLocalFollowsLoader
                        } else {
                            MixedTimelineLoaderFactory.create(loaders, TimelineMergePolicy.Time)
                        }
                    }
                }
            }

    private fun loaders(
        follows: List<DbLocalFollow>,
        context: TimelineLoaderContext,
    ): Flow<List<RemoteLoader<UiTimelineV2>>> =
        context
            .accountServiceFlow(AccountType.Specific(follows.first().accountKey))
            .map { service ->
                follows.mapNotNull { follow ->
                    (service.userTimeline(follow.userKey) as? CacheableRemoteLoader<UiTimelineV2>)?.let {
                        PacedRemoteLoader(it, gateFor(follow.platformId))
                    }
                }
            }
            // A signed-out account must not take the rest of the feed down with it.
            .catch { emit(emptyList()) }

    private fun gateFor(platformId: String): RequestGate =
        if (LocalFollowLimits.isCookieSession(platformId)) cookieSessionGate else defaultGate
}

private object KoinLocalFollowsLoaderFactory : TimelineLoaderFactory<LocalFollowsTimelineData> {
    private val delegate: LocalFollowsLoaderFactory by koinInject()

    override fun create(
        data: LocalFollowsTimelineData,
        context: TimelineLoaderContext,
    ): Flow<RemoteLoader<UiTimelineV2>> = delegate.create(data, context)
}

private object NoLocalFollowsLoader : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "local_follows_empty"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> = PagingResult(endOfPaginationReached = true)
}
