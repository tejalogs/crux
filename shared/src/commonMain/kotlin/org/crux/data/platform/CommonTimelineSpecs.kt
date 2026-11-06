package org.crux.data.platform

import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import org.crux.data.datasource.microblog.MicroblogDataSource
import org.crux.data.datasource.microblog.datasource.ListDataSource
import org.crux.data.model.IconType
import org.crux.data.model.tab.TimelineCandidate
import org.crux.data.model.tab.TimelineLoaderFactory
import org.crux.data.model.tab.TimelineSpec
import org.crux.data.model.tab.TimelineSpecIds
import org.crux.data.model.tab.accountLoader
import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiIcon
import org.crux.ui.model.UiList
import org.crux.ui.model.UiStrings
import org.crux.ui.model.UiText
import org.crux.ui.model.asType
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public object CommonTimelineSpecs {
    public val home: TimelineSpec<TimelineSpec.AccountBasedData> =
        TimelineSpec(
            id = TimelineSpecIds.COMMON_HOME,
            title = UiStrings.Home,
            icon = UiIcon.Home.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory =
                accountLoader<MicroblogDataSource, TimelineSpec.AccountBasedData> {
                    homeTimeline()
                },
        )

    public val guestHome: TimelineSpec<GuestHomeData> =
        TimelineSpec(
            id = TimelineSpecIds.COMMON_GUEST_HOME,
            title = UiStrings.Home,
            icon = UiIcon.Home.asType(),
            serializer = GuestHomeData.serializer(),
            targetId = { it.host },
            loaderFactory =
                TimelineLoaderFactory { data, context ->
                    context
                        .accountServiceFlow(AccountType.GuestHost(data.host))
                        .map { service -> service.homeTimeline() }
                },
        )

    public val discover: TimelineSpec<TimelineSpec.AccountBasedData> =
        TimelineSpec(
            id = TimelineSpecIds.COMMON_DISCOVER,
            title = UiStrings.Discover,
            icon = UiIcon.Search.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory =
                accountLoader<MicroblogDataSource, TimelineSpec.AccountBasedData> {
                    discoverStatuses()
                },
        )

    public val list: TimelineSpec<TimelineSpec.AccountResourceData> =
        TimelineSpec(
            id = TimelineSpecIds.COMMON_LIST,
            title = UiStrings.List,
            icon = UiIcon.List.asType(),
            serializer = TimelineSpec.AccountResourceData.serializer(),
            targetId = { "${it.accountKey}:${it.resourceId}" },
            loaderFactory =
                accountLoader<ListDataSource, TimelineSpec.AccountResourceData> {
                    listTimeline(listId = it.resourceId)
                },
        )

    @Serializable
    public data class GuestHomeData(
        val host: String,
    ) : TimelineSpec.Data
}

@HiddenFromObjC
public fun UiList.List.toTimelineCandidate(accountKey: MicroBlogKey): TimelineCandidate<*> =
    CommonTimelineSpecs.list
        .candidate(
            data = TimelineSpec.AccountResourceData(accountKey, id),
            title = UiText.Raw(title),
            icon = avatar?.let { IconType.Url(it) } ?: UiIcon.List.asType(),
        )
