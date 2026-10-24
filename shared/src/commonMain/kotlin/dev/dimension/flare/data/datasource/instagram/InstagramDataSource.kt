package dev.dimension.flare.data.datasource.instagram

import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.datasource.microblog.datasource.TimelineTabConfigurationDataSource
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.notSupported
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.ShortcutSpec
import dev.dimension.flare.data.model.tab.TimelineCandidate
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.platform.CommonTimelineSpecs
import dev.dimension.flare.data.platform.InstagramCredential
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow

public class InstagramDataSource(
    override val accountKey: MicroBlogKey,
    private val credentialFlow: Flow<InstagramCredential>,
    private val service: InstagramFeedService = InstagramFeedService(),
) : AuthenticatedMicroblogDataSource,
    TimelineTabConfigurationDataSource {

    override fun homeTimeline(): RemoteLoader<UiTimelineV2> =
        InstagramHomeTimelineRemoteLoader(
            service = service,
            accountKey = accountKey,
            credentialFlow = credentialFlow,
        )

    override fun userTimeline(
        userKey: MicroBlogKey,
        mediaOnly: Boolean,
    ): RemoteLoader<UiTimelineV2> = notSupported()

    override fun context(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> = notSupported()

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
