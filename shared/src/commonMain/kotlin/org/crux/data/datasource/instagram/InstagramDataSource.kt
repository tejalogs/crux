package org.crux.data.datasource.instagram

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import org.crux.data.database.cache.CacheDatabase
import org.crux.data.database.cache.model.DbStatus
import org.crux.data.datasource.microblog.AuthenticatedMicroblogDataSource
import org.crux.data.datasource.microblog.ComposeConfig
import org.crux.data.datasource.microblog.ComposeData
import org.crux.data.datasource.microblog.ComposeDataSource
import org.crux.data.datasource.microblog.ComposeType
import org.crux.data.datasource.microblog.DatabaseUpdater
import org.crux.data.datasource.microblog.PostEvent
import org.crux.data.datasource.microblog.ProfileTab
import org.crux.data.datasource.microblog.datasource.PostDataSource
import org.crux.data.datasource.microblog.datasource.TimelineTabConfigurationDataSource
import org.crux.data.datasource.microblog.datasource.UserDataSource
import org.crux.data.datasource.microblog.handler.PostEventHandler
import org.crux.data.datasource.microblog.handler.PostHandler
import org.crux.data.datasource.microblog.handler.UserHandler
import org.crux.data.datasource.microblog.loader.PostLoader
import org.crux.data.datasource.microblog.loader.UserLoader
import org.crux.data.datasource.microblog.paging.RemoteLoader
import org.crux.data.datasource.microblog.paging.notSupported
import org.crux.data.model.IconType
import org.crux.data.model.tab.ShortcutSpec
import org.crux.data.model.tab.TimelineCandidate
import org.crux.data.model.tab.TimelineSpec
import org.crux.data.platform.CommonTimelineSpecs
import org.crux.data.platform.InstagramCredential
import org.crux.di.koinGet
import org.crux.model.AccountType
import org.crux.model.DbAccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiHandle
import org.crux.ui.model.UiHashtag
import org.crux.ui.model.UiIcon
import org.crux.ui.model.UiProfile
import org.crux.ui.model.UiStrings
import org.crux.ui.model.UiText
import org.crux.ui.model.UiTimelineV2
import org.crux.ui.presenter.compose.ComposeStatus
import kotlin.native.HiddenFromObjC

private const val INSTAGRAM_MAX_COMMENT_LENGTH: Int = 2200

@HiddenFromObjC
public class InstagramDataSource(
    override val accountKey: MicroBlogKey,
    private val credentialFlow: Flow<InstagramCredential>,
    private val service: InstagramFeedService = InstagramFeedService(),
) : AuthenticatedMicroblogDataSource,
    TimelineTabConfigurationDataSource,
    PostDataSource,
    UserDataSource,
    ComposeDataSource,
    PostEventHandler.Handler {
    private val syncService: InstagramSyncService by lazy {
        runCatching { koinGet<InstagramSyncService>() }.getOrNull() ?: InstagramSyncService()
    }

    private val database: CacheDatabase? by lazy {
        runCatching { koinGet<CacheDatabase>() }.getOrNull()
    }

    private val userLoader by lazy {
        object : UserLoader {
            override suspend fun userByHandleAndHost(uiHandle: UiHandle): UiProfile {
                val cachedUser =
                    syncService.ensureLoaded().firstNotNullOfOrNull { item ->
                        item.user?.takeIf { it.username.equals(uiHandle.raw, ignoreCase = true) }
                    }
                if (cachedUser != null) {
                    InstagramTimelineMapper.renderProfile(cachedUser, accountKey)?.let {
                        return it
                    }
                }
                throw NoSuchElementException("Instagram user @${uiHandle.raw} not found")
            }

            override suspend fun userById(id: String): UiProfile {
                val cachedUser =
                    syncService.ensureLoaded().firstNotNullOfOrNull { item ->
                        item.user?.takeIf {
                            it.resolvedId == id || it.username.equals(id, ignoreCase = true)
                        }
                    }
                if (cachedUser != null) {
                    InstagramTimelineMapper.renderProfile(cachedUser, accountKey)?.let {
                        return it
                    }
                }
                throw NoSuchElementException("Instagram user $id not found")
            }
        }
    }

    override val userHandler: UserHandler by lazy {
        UserHandler(
            host = "instagram.com",
            loader = userLoader,
        )
    }

    private val postLoader by lazy {
        object : PostLoader {
            override suspend fun status(statusKey: MicroBlogKey): UiTimelineV2 {
                val cleanId = statusKey.id.substringBefore("#")
                val numericTarget = InstagramFeedService.resolveNumericMediaId(cleanId)
                val cached =
                    syncService.ensureLoaded().firstOrNull {
                        it.id == cleanId ||
                            it.pk == cleanId ||
                            it.code == cleanId ||
                            it.pk == numericTarget ||
                            it.id.substringBefore("_") == numericTarget
                    }
                if (cached != null) {
                    val rendered =
                        InstagramTimelineMapper.render(
                            mediaOrAd = cached,
                            accountKey = accountKey,
                            statusKeyOverride = statusKey,
                        )
                    if (rendered != null) return rendered
                }

                // Check Room CacheDatabase for offline persistence across cold starts
                val cachedDb =
                    database
                        ?.statusDao()
                        ?.getByKeys(
                            statusKeys = listOf(MicroBlogKey(cleanId, "instagram.com")),
                            accountType = AccountType.Specific(accountKey),
                        )?.firstOrNull()
                        ?.content
                if (cachedDb != null) return cachedDb

                throw NoSuchElementException("Instagram post ${statusKey.id} could not be loaded")
            }

            override suspend fun deleteStatus(statusKey: MicroBlogKey): Unit =
                throw UnsupportedOperationException("Instagram post deletion is not supported")
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
            text = ComposeConfig.Text(INSTAGRAM_MAX_COMMENT_LENGTH),
        )

    override suspend fun publish(
        data: ComposeData,
        progress: () -> Unit,
    ): Unit =
        throw UnsupportedOperationException(
            "To protect your account from bot detection, posting from Crux is disabled. Please open Instagram in the web browser to post or comment.",
        )

    override fun homeTimeline(): RemoteLoader<UiTimelineV2> =
        InstagramHomeTimelineRemoteLoader(
            service = service,
            accountKey = accountKey,
            credentialFlow = credentialFlow,
            syncService = syncService,
        )

    override fun userTimeline(
        userKey: MicroBlogKey,
        mediaOnly: Boolean,
    ): RemoteLoader<UiTimelineV2> =
        InstagramUserTimelineRemoteLoader(
            userKey = userKey,
            accountKey = accountKey,
            service = service,
            credentialFlow = credentialFlow,
            syncService = syncService,
        )

    override fun context(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> =
        InstagramContextRemoteLoader(
            statusKey = statusKey,
            accountKey = accountKey,
            service = service,
            credentialFlow = credentialFlow,
            syncService = syncService,
        )

    override fun searchStatus(query: String): RemoteLoader<UiTimelineV2> = notSupported()

    override fun searchUser(query: String): RemoteLoader<UiProfile> = notSupported()

    override fun discoverUsers(): RemoteLoader<UiProfile> = notSupported()

    override fun discoverStatuses(): RemoteLoader<UiTimelineV2> = notSupported()

    override fun discoverHashtags(): RemoteLoader<UiHashtag> = notSupported()

    override fun following(userKey: MicroBlogKey): RemoteLoader<UiProfile> = notSupported()

    override fun fans(userKey: MicroBlogKey): RemoteLoader<UiProfile> = notSupported()

    override suspend fun profileTabs(userKey: MicroBlogKey): ImmutableList<ProfileTab> =
        persistentListOf(
            ProfileTab(
                name = UiStrings.Posts,
                displayType = ProfileTab.DisplayType.Timeline,
                loader = userTimeline(userKey = userKey, mediaOnly = false),
            ),
            ProfileTab(
                name = UiStrings.Media,
                displayType = ProfileTab.DisplayType.Gallery,
                loader = userTimeline(userKey = userKey, mediaOnly = true),
            ),
        )

    override val defaultTabs: ImmutableList<TimelineCandidate<*>> by lazy {
        persistentListOf(
            CommonTimelineSpecs.home.candidate(
                data = TimelineSpec.AccountBasedData(accountKey),
                icon = IconType.Material(UiIcon.Instagram),
                title = UiText.Raw("Instagram"),
            ),
        )
    }

    override val builtInTimelineTabs: ImmutableList<TimelineCandidate<*>> by lazy {
        persistentListOf(
            CommonTimelineSpecs.home.candidate(
                data = TimelineSpec.AccountBasedData(accountKey),
                icon = IconType.Material(UiIcon.Instagram),
                title = UiText.Raw("Instagram"),
            ),
        )
    }

    override val shortcuts: ImmutableList<ShortcutSpec> = persistentListOf()
}
