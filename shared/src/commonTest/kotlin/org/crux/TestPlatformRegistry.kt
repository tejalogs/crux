package org.crux

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import org.crux.data.datasource.microblog.MicroblogDataSource
import org.crux.data.model.tab.AllRssTimelineData
import org.crux.data.model.tab.LiveWebTimelineSpecs
import org.crux.data.model.tab.RssTimelineData
import org.crux.data.model.tab.SubscriptionTimelineData
import org.crux.data.model.tab.TimelineSpec
import org.crux.data.model.tab.TimelineSpecIds
import org.crux.data.model.tab.remoteLoaderFactory
import org.crux.data.platform.CommonTimelineSpecs
import org.crux.data.repository.AccountMicroblogDataSource
import org.crux.data.repository.AccountService
import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import org.crux.model.PlatformCapability
import org.crux.model.PlatformDataSourceContext
import org.crux.model.PlatformDeepLink
import org.crux.model.PlatformMetadata
import org.crux.model.PlatformRegistry
import org.crux.model.PlatformRuntimeData
import org.crux.model.PlatformSpec
import org.crux.model.xqtHost
import org.crux.model.xqtOldHost
import org.crux.ui.model.UiAccount
import org.crux.ui.model.UiIcon
import org.crux.ui.model.UiStrings
import org.crux.ui.model.asType
import org.crux.ui.route.DeeplinkRoute

internal fun testPlatformRegistry(): PlatformRegistry = PlatformRegistry(testPlatformRuntimeData())

internal fun testPlatformRuntimeData(): PlatformRuntimeData =
    PlatformRuntimeData(
        platformSpecs =
            listOf(
                TestNostrPlatformSpec,
                TestMastodonPlatformSpec,
                TestMisskeyPlatformSpec,
                TestBlueskyPlatformSpec,
                TestXqtPlatformSpec,
                TestVvoPlatformSpec,
            ),
        extraTimelineSpecs = testExtraTimelineSpecs,
    )

internal fun testTimelineSpecs(): List<TimelineSpec<out TimelineSpec.Data>> = testPlatformRuntimeData().timelineSpecs

internal fun unavailableAccountService(): AccountService =
    object : AccountService {
        override fun accountServiceFlow(accountType: AccountType): Flow<MicroblogDataSource> =
            error("Account service is not available in this test")

        override fun allAccountServicesFlow(): Flow<List<AccountMicroblogDataSource>> =
            error("Account service is not available in this test")

        override fun <T : Any> addAccount(
            account: UiAccount,
            credential: T,
            serializer: KSerializer<T>,
        ): Job = error("Account service is not available in this test")

        override fun <T : Any> credentialFlow(
            accountKey: MicroBlogKey,
            serializer: KSerializer<T>,
        ): Flow<T> = error("Account service is not available in this test")

        override fun <T : Any> updateCredential(
            accountKey: MicroBlogKey,
            credential: T,
            serializer: KSerializer<T>,
        ): Job = error("Account service is not available in this test")
    }

private val testExtraTimelineSpecs: List<TimelineSpec<out TimelineSpec.Data>> =
    listOf(
        CommonTimelineSpecs.home,
        CommonTimelineSpecs.discover,
        CommonTimelineSpecs.list,
        LiveWebTimelineSpecs.x,
        LiveWebTimelineSpecs.reddit,
        LiveWebTimelineSpecs.instagram,
        LiveWebTimelineSpecs.linkedin,
        rssTimelineSpec(),
        allRssTimelineSpec(),
        subscriptionTimelineSpec(),
        accountBasedTimelineSpec(TimelineSpecIds.MASTODON_LOCAL, UiStrings.MastodonLocal, UiIcon.Local),
        accountBasedTimelineSpec(TimelineSpecIds.MASTODON_PUBLIC, UiStrings.MastodonPublic, UiIcon.World),
        accountBasedTimelineSpec(TimelineSpecIds.MASTODON_BOOKMARK, UiStrings.Bookmark, UiIcon.Bookmark),
        accountBasedTimelineSpec(TimelineSpecIds.MASTODON_FAVOURITE, UiStrings.Favourite, UiIcon.Favourite),
        accountBasedTimelineSpec(TimelineSpecIds.MISSKEY_FAVOURITE, UiStrings.Favourite, UiIcon.Favourite),
        accountBasedTimelineSpec(TimelineSpecIds.MISSKEY_HYBRID, UiStrings.Social, UiIcon.World),
        accountBasedTimelineSpec(TimelineSpecIds.MISSKEY_LOCAL, UiStrings.MastodonLocal, UiIcon.Local),
        accountBasedTimelineSpec(TimelineSpecIds.MISSKEY_GLOBAL, UiStrings.MastodonPublic, UiIcon.World),
        accountResourceTimelineSpec(TimelineSpecIds.MISSKEY_ANTENNA, UiStrings.Antenna, UiIcon.Rss),
        accountResourceTimelineSpec(TimelineSpecIds.MISSKEY_CHANNEL, UiStrings.Channel, UiIcon.Channel),
        accountBasedTimelineSpec(TimelineSpecIds.BLUESKY_BOOKMARK, UiStrings.Bookmark, UiIcon.Bookmark),
        accountResourceTimelineSpec(TimelineSpecIds.BLUESKY_FEED, UiStrings.Feeds, UiIcon.Bluesky),
        accountBasedTimelineSpec(TimelineSpecIds.XQT_FEATURED, UiStrings.ForYou, UiIcon.Featured),
        accountBasedTimelineSpec(TimelineSpecIds.XQT_BOOKMARK, UiStrings.Bookmark, UiIcon.Bookmark),
        accountBasedTimelineSpec(TimelineSpecIds.XQT_DEVICE_FOLLOW, UiStrings.List, UiIcon.List),
        accountBasedTimelineSpec(TimelineSpecIds.VVO_FAVORITE, UiStrings.Favourite, UiIcon.Favourite),
        accountBasedTimelineSpec(TimelineSpecIds.VVO_LIKED, UiStrings.Liked, UiIcon.Heart),
    )

private fun accountBasedTimelineSpec(
    id: String,
    title: UiStrings,
    icon: UiIcon,
): TimelineSpec<TimelineSpec.AccountBasedData> =
    TimelineSpec(
        id = id,
        title = title,
        icon = icon.asType(),
        serializer = TimelineSpec.AccountBasedData.serializer(),
        targetId = { it.accountKey.toString() },
        loaderFactory = remoteLoaderFactory { unavailableLoader(id) },
    )

private fun accountResourceTimelineSpec(
    id: String,
    title: UiStrings,
    icon: UiIcon,
): TimelineSpec<TimelineSpec.AccountResourceData> =
    TimelineSpec(
        id = id,
        title = title,
        icon = icon.asType(),
        serializer = TimelineSpec.AccountResourceData.serializer(),
        targetId = { "${it.accountKey}:${it.resourceId}" },
        loaderFactory = remoteLoaderFactory { unavailableLoader(id) },
    )

private fun rssTimelineSpec(): TimelineSpec<RssTimelineData> =
    TimelineSpec(
        id = TimelineSpecIds.RSS_FEED,
        title = UiStrings.Rss,
        icon = UiIcon.Rss.asType(),
        serializer = RssTimelineData.serializer(),
        targetId = { it.feedUrl },
        loaderFactory = remoteLoaderFactory { unavailableLoader(TimelineSpecIds.RSS_FEED) },
    )

private fun allRssTimelineSpec(): TimelineSpec<AllRssTimelineData> =
    TimelineSpec(
        id = TimelineSpecIds.RSS_ALL,
        title = UiStrings.AllRssFeeds,
        icon = UiIcon.Rss.asType(),
        serializer = AllRssTimelineData.serializer(),
        targetId = { "all" },
        loaderFactory = remoteLoaderFactory { unavailableLoader(TimelineSpecIds.RSS_ALL) },
    )

private fun subscriptionTimelineSpec(): TimelineSpec<SubscriptionTimelineData> =
    TimelineSpec(
        id = TimelineSpecIds.RSS_SUBSCRIPTION,
        title = UiStrings.Rss,
        icon = UiIcon.Rss.asType(),
        serializer = SubscriptionTimelineData.serializer(),
        targetId = { "${it.subscriptionType.name}:${it.subscriptionUrl}" },
        loaderFactory = remoteLoaderFactory { unavailableLoader(TimelineSpecIds.RSS_SUBSCRIPTION) },
    )

private fun unavailableLoader(id: String): Nothing = throw UnsupportedOperationException("$id loader is not available in tests")

private abstract class TestDeepLinkPlatformSpec(
    final override val platformId: String,
    displayName: String,
    icon: UiIcon,
    final override val isDefaultGuest: Boolean = false,
) : PlatformSpec {
    final override val metadata: PlatformMetadata =
        PlatformMetadata(
            displayName = displayName,
            icon = icon,
        )
    final override val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> = persistentListOf()

    final override fun createDataSource(context: PlatformDataSourceContext): MicroblogDataSource =
        throw UnsupportedOperationException("$platformId data source is not available in tests")

    final override fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource = throw UnsupportedOperationException("$platformId guest data source is not available in tests")
}

private data object TestNostrPlatformSpec : TestDeepLinkPlatformSpec(
    platformId = "Nostr",
    displayName = "Nostr",
    icon = UiIcon.Nostr,
) {
    override fun deepLinks(accountKey: MicroBlogKey): ImmutableList<PlatformDeepLink<*>> = persistentListOf()
}

private data object TestBlueskyPlatformSpec : TestDeepLinkPlatformSpec(
    platformId = "Bluesky",
    displayName = "Bluesky",
    icon = UiIcon.Bluesky,
) {
    override fun deepLinks(accountKey: MicroBlogKey): ImmutableList<PlatformDeepLink<*>> =
        buildList {
            add(blueskyProfileDeepLink(accountKey, "https://${accountKey.host}/profile/{handle}"))
            add(blueskyPostDeepLink(accountKey, "https://${accountKey.host}/profile/{handle}/post/{id}"))
            add(blueskyProfileDeepLink(accountKey, "https://bsky.app/profile/{handle}"))
            add(blueskyPostDeepLink(accountKey, "https://bsky.app/profile/{handle}/post/{id}"))
        }.toImmutableList()
}

private data object TestMastodonPlatformSpec : TestDeepLinkPlatformSpec(
    platformId = "Mastodon",
    displayName = "Mastodon",
    icon = UiIcon.Mastodon,
    isDefaultGuest = true,
) {
    override fun deepLinks(accountKey: MicroBlogKey): ImmutableList<PlatformDeepLink<*>> =
        persistentListOf(
            PlatformDeepLink(
                uriPattern = "https://${accountKey.host}/@{handle}",
                serializer = TestProfileDeepLink.serializer(),
                callback = { data -> profileRoute(accountKey, data.handle) },
            ),
            PlatformDeepLink(
                uriPattern = "https://${accountKey.host}/@{handle}/{id}",
                serializer = TestPostDeepLink.serializer(),
                callback = { data ->
                    DeeplinkRoute.Status.Detail(
                        accountType = AccountType.Specific(accountKey),
                        statusKey = MicroBlogKey(data.id, accountKey.host),
                    )
                },
            ),
        )
}

private data object TestMisskeyPlatformSpec : TestDeepLinkPlatformSpec(
    platformId = "Misskey",
    displayName = "Misskey",
    icon = UiIcon.Misskey,
) {
    override fun deepLinks(accountKey: MicroBlogKey): ImmutableList<PlatformDeepLink<*>> =
        persistentListOf(
            PlatformDeepLink(
                uriPattern = "https://${accountKey.host}/@{handle}",
                serializer = TestProfileDeepLink.serializer(),
                callback = { data -> profileRoute(accountKey, data.handle) },
            ),
            PlatformDeepLink(
                uriPattern = "https://${accountKey.host}/notes/{id}",
                serializer = TestIdDeepLink.serializer(),
                callback = { data ->
                    DeeplinkRoute.Status.Detail(
                        accountType = AccountType.Specific(accountKey),
                        statusKey = MicroBlogKey(data.id, accountKey.host),
                    )
                },
            ),
        )
}

private data object TestXqtPlatformSpec : TestDeepLinkPlatformSpec(
    platformId = "xQt",
    displayName = "X",
    icon = UiIcon.X,
) {
    override val capabilities: Set<PlatformCapability> = setOf(PlatformCapability.MxgaFiltering)

    override fun deepLinks(accountKey: MicroBlogKey): ImmutableList<PlatformDeepLink<*>> {
        val profile =
            listOf(
                "https://$xqtHost/{handle}",
                "https://$xqtOldHost/{handle}",
                "https://www.$xqtHost/{handle}",
                "https://www.$xqtOldHost/{handle}",
            )
        val post =
            listOf(
                "https://$xqtHost/{handle}/status/{id}",
                "https://$xqtOldHost/{handle}/status/{id}",
                "https://www.$xqtHost/{handle}/status/{id}",
                "https://www.$xqtOldHost/{handle}/status/{id}",
            )
        val media =
            listOf(
                "https://$xqtHost/{handle}/status/{id}/photo/{index}",
                "https://$xqtOldHost/{handle}/status/{id}/photo/{index}",
                "https://www.$xqtHost/{handle}/status/{id}/photo/{index}",
                "https://www.$xqtOldHost/{handle}/status/{id}/photo/{index}",
            )
        return (
            profile.map { xqtProfileDeepLink(accountKey, it) } +
                post.map { xqtPostDeepLink(accountKey, it) } +
                media.map { xqtPostMediaDeepLink(accountKey, it) }
        ).toImmutableList()
    }
}

private data object TestVvoPlatformSpec : TestDeepLinkPlatformSpec(
    platformId = "VVo",
    displayName = "VVo",
    icon = UiIcon.Weibo,
) {
    override fun deepLinks(accountKey: MicroBlogKey): ImmutableList<PlatformDeepLink<*>> = persistentListOf()
}

private fun blueskyProfileDeepLink(
    accountKey: MicroBlogKey,
    uriPattern: String,
): PlatformDeepLink<TestProfileDeepLink> =
    PlatformDeepLink(
        uriPattern = uriPattern,
        serializer = TestProfileDeepLink.serializer(),
        callback = { data -> profileRoute(accountKey, data.handle) },
    )

private fun blueskyPostDeepLink(
    accountKey: MicroBlogKey,
    uriPattern: String,
): PlatformDeepLink<TestPostDeepLink> =
    PlatformDeepLink(
        uriPattern = uriPattern,
        serializer = TestPostDeepLink.serializer(),
        callback = { data ->
            DeeplinkRoute.Status.Detail(
                accountType = AccountType.Specific(accountKey),
                statusKey =
                    MicroBlogKey(
                        id = "at://${data.handle}/app.bsky.feed.post/${data.id}",
                        host = accountKey.host,
                    ),
            )
        },
    )

private fun xqtProfileDeepLink(
    accountKey: MicroBlogKey,
    uriPattern: String,
): PlatformDeepLink<TestProfileDeepLink> =
    PlatformDeepLink(
        uriPattern = uriPattern,
        serializer = TestProfileDeepLink.serializer(),
        callback = { data -> profileRoute(accountKey, data.handle) },
    )

private fun xqtPostDeepLink(
    accountKey: MicroBlogKey,
    uriPattern: String,
): PlatformDeepLink<TestPostDeepLink> =
    PlatformDeepLink(
        uriPattern = uriPattern,
        serializer = TestPostDeepLink.serializer(),
        callback = { data ->
            DeeplinkRoute.Status.Detail(
                accountType = AccountType.Specific(accountKey),
                statusKey = MicroBlogKey(data.id, accountKey.host),
            )
        },
    )

private fun xqtPostMediaDeepLink(
    accountKey: MicroBlogKey,
    uriPattern: String,
): PlatformDeepLink<TestPostMediaDeepLink> =
    PlatformDeepLink(
        uriPattern = uriPattern,
        serializer = TestPostMediaDeepLink.serializer(),
        callback = { data ->
            DeeplinkRoute.Media.StatusMedia(
                accountType = AccountType.Specific(accountKey),
                statusKey = MicroBlogKey(data.id, accountKey.host),
                index = data.index,
                preview = null,
            )
        },
    )

private fun profileRoute(
    accountKey: MicroBlogKey,
    handle: String,
): DeeplinkRoute {
    val target =
        if (handle.contains('@')) {
            MicroBlogKey.valueOf(handle)
        } else {
            MicroBlogKey(handle, accountKey.host)
        }
    return DeeplinkRoute.Profile.UserNameWithHost(
        accountType = AccountType.Specific(accountKey),
        userName = target.id,
        host = target.host,
    )
}

@Serializable
private data class TestProfileDeepLink(
    val handle: String,
)

@Serializable
private data class TestPostDeepLink(
    val handle: String,
    val id: String,
)

@Serializable
private data class TestIdDeepLink(
    val id: String,
)

@Serializable
private data class TestPostMediaDeepLink(
    val handle: String,
    val id: String,
    val index: Int,
)
