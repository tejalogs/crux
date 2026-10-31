package dev.dimension.flare.data.datasource.linkedin

import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.ComposeDataSource
import dev.dimension.flare.data.datasource.microblog.ComposeType
import dev.dimension.flare.data.datasource.microblog.DatabaseUpdater
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.datasource.microblog.datasource.PostDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.TimelineTabConfigurationDataSource
import dev.dimension.flare.data.datasource.microblog.handler.PostEventHandler
import dev.dimension.flare.data.datasource.microblog.handler.PostHandler
import dev.dimension.flare.data.datasource.microblog.loader.PostLoader
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.notSupported
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.ShortcutSpec
import dev.dimension.flare.data.model.tab.TimelineCandidate
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.platform.CommonTimelineSpecs
import dev.dimension.flare.data.platform.LinkedInCredential
import dev.dimension.flare.di.koinGet
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.presenter.compose.ComposeStatus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public class LinkedInDataSource(
    override val accountKey: MicroBlogKey,
    private val credentialFlow: Flow<LinkedInCredential>,
    private val service: LinkedInFeedService = LinkedInFeedService(),
) : AuthenticatedMicroblogDataSource,
    TimelineTabConfigurationDataSource,
    PostDataSource,
    PostEventHandler.Handler,
    ComposeDataSource {
    private val syncService: LinkedInSyncService by lazy {
        runCatching { koinGet<LinkedInSyncService>() }.getOrNull() ?: LinkedInSyncService()
    }

    private val database: CacheDatabase? by lazy {
        runCatching { koinGet<CacheDatabase>() }.getOrNull()
    }

    private val postLoader by lazy {
        object : PostLoader {
            override suspend fun status(statusKey: MicroBlogKey): UiTimelineV2 {
                val cleanId = statusKey.id
                val cached =
                    syncService.ensureLoaded().firstOrNull {
                        it.id == cleanId ||
                            it.urn == cleanId ||
                            it.entityUrn == cleanId ||
                            it.trackingUrn == cleanId ||
                            (it.urn != null && (cleanId.contains(it.urn) || it.urn.contains(cleanId))) ||
                            (it.id.isNotBlank() && (cleanId.contains(it.id) || it.id.contains(cleanId)))
                    }
                if (cached != null) {
                    val rendered = LinkedInTimelineMapper.render(cached, accountKey)
                    if (rendered != null) return rendered
                }

                // Check Room CacheDatabase for offline persistence across cold starts
                val cachedDb =
                    database
                        ?.statusDao()
                        ?.getByKeys(
                            statusKeys = listOf(statusKey, MicroBlogKey(cleanId, "linkedin.com")),
                            accountType = AccountType.Specific(accountKey),
                        )?.firstOrNull()
                        ?.content
                if (cachedDb != null) return cachedDb

                throw NoSuchElementException("LinkedIn post $cleanId could not be loaded")
            }

            override suspend fun deleteStatus(statusKey: MicroBlogKey): Unit =
                throw UnsupportedOperationException("LinkedIn post deletion is not supported")
        }
    }

    override val postHandler: PostHandler by lazy {
        PostHandler(
            accountType = AccountType.Specific(accountKey),
            loader = postLoader,
        )
    }

    override val postEventHandler: PostEventHandler by lazy {
        PostEventHandler(
            accountType = AccountType.Specific(accountKey),
            handler = this,
        )
    }

    override suspend fun handle(
        event: PostEvent,
        updater: DatabaseUpdater,
    ) {
    }

    override fun composeConfig(type: ComposeType): ComposeConfig =
        ComposeConfig(
            text = ComposeConfig.Text(3000),
            media =
                if (type == ComposeType.Reply) {
                    null
                } else {
                    ComposeConfig.Media(
                        maxCount = 9,
                        canSensitive = false,
                        altTextMaxLength = 1000,
                        allowMediaOnly = false,
                    )
                },
        )

    override suspend fun publish(
        data: ComposeData,
        progress: () -> Unit,
    ): Unit =
        throw UnsupportedOperationException(
            "To protect your account from bot detection, posting from Crux is disabled. Please open LinkedIn in the web browser to create posts or comments.",
        )

    override fun homeTimeline(): RemoteLoader<UiTimelineV2> =
        LinkedInHomeTimelineRemoteLoader(
            service = service,
            accountKey = accountKey,
            credentialFlow = credentialFlow,
            syncService = syncService,
        )

    override fun userTimeline(
        userKey: MicroBlogKey,
        mediaOnly: Boolean,
    ): RemoteLoader<UiTimelineV2> = notSupported()

    override fun context(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> =
        LinkedInContextRemoteLoader(
            statusKey = statusKey,
            accountKey = accountKey,
            service = service,
            credentialFlow = credentialFlow,
            postLoader = postLoader,
            syncService = syncService,
        )

    override fun searchStatus(query: String): RemoteLoader<UiTimelineV2> = notSupported()

    override fun searchUser(query: String): RemoteLoader<UiProfile> = notSupported()

    override fun discoverUsers(): RemoteLoader<UiProfile> = notSupported()

    override fun discoverStatuses(): RemoteLoader<UiTimelineV2> = notSupported()

    override fun discoverHashtags(): RemoteLoader<UiHashtag> = notSupported()

    override fun following(userKey: MicroBlogKey): RemoteLoader<UiProfile> = notSupported()

    override fun fans(userKey: MicroBlogKey): RemoteLoader<UiProfile> = notSupported()

    override suspend fun profileTabs(userKey: MicroBlogKey): ImmutableList<ProfileTab> = persistentListOf()

    override val defaultTabs: ImmutableList<TimelineCandidate<*>> by lazy {
        persistentListOf(
            CommonTimelineSpecs.home.candidate(
                data = TimelineSpec.AccountBasedData(accountKey),
                icon = IconType.Material(UiIcon.LinkedIn),
                title = UiText.Raw("LinkedIn"),
            ),
        )
    }

    override val builtInTimelineTabs: ImmutableList<TimelineCandidate<*>> by lazy {
        persistentListOf(
            CommonTimelineSpecs.home.candidate(
                data = TimelineSpec.AccountBasedData(accountKey),
                icon = IconType.Material(UiIcon.LinkedIn),
                title = UiText.Raw("LinkedIn"),
            ),
        )
    }

    override val shortcuts: ImmutableList<ShortcutSpec> = persistentListOf()
}
