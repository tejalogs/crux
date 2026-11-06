package org.crux.data.model.tab

import kotlinx.serialization.Serializable
import org.crux.data.datasource.microblog.paging.CacheableRemoteLoader
import org.crux.data.datasource.microblog.paging.PagingRequest
import org.crux.data.datasource.microblog.paging.PagingResult
import org.crux.data.model.IconType
import org.crux.ui.model.UiIcon
import org.crux.ui.model.UiStrings
import org.crux.ui.model.UiText
import org.crux.ui.model.UiTimelineV2
import org.crux.ui.model.asType
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
@Serializable
public data class LiveWebTimelineData(
    val site: String,
) : TimelineSpec.Data

/** Tabs that show a real website instead of a timeline, for sites with no account in Crux. */
@HiddenFromObjC
public object LiveWebTimelineSpecs {
    public val reddit: TimelineSpec<LiveWebTimelineData> =
        TimelineSpec(
            id = TimelineSpecIds.WEB_REDDIT,
            title = UiStrings.List,
            icon = UiIcon.Reddit.asType(),
            serializer = LiveWebTimelineData.serializer(),
            targetId = { it.site },
            loaderFactory = remoteLoaderFactory { NoLiveWebPostsLoader },
        )

    public val x: TimelineSpec<LiveWebTimelineData> =
        TimelineSpec(
            id = TimelineSpecIds.WEB_X,
            title = UiStrings.List,
            icon = UiIcon.X.asType(),
            serializer = LiveWebTimelineData.serializer(),
            targetId = { it.site },
            loaderFactory = remoteLoaderFactory { NoLiveWebPostsLoader },
        )

    public val instagram: TimelineSpec<LiveWebTimelineData> =
        TimelineSpec(
            id = TimelineSpecIds.WEB_INSTAGRAM,
            title = UiStrings.List,
            icon = UiIcon.Instagram.asType(),
            serializer = LiveWebTimelineData.serializer(),
            targetId = { it.site },
            loaderFactory = remoteLoaderFactory { NoLiveWebPostsLoader },
        )

    public val linkedin: TimelineSpec<LiveWebTimelineData> =
        TimelineSpec(
            id = TimelineSpecIds.WEB_LINKEDIN,
            title = UiStrings.List,
            icon = UiIcon.LinkedIn.asType(),
            serializer = LiveWebTimelineData.serializer(),
            targetId = { it.site },
            loaderFactory = remoteLoaderFactory { NoLiveWebPostsLoader },
        )

    public val xCandidate: TimelineCandidate<LiveWebTimelineData> =
        x.candidate(
            data = LiveWebTimelineData("x"),
            title = UiText.Raw("X"),
            icon = IconType.Material(UiIcon.X),
        )

    public val redditCandidate: TimelineCandidate<LiveWebTimelineData> =
        reddit.candidate(
            data = LiveWebTimelineData("reddit"),
            title = UiText.Raw("Reddit"),
            icon = IconType.Material(UiIcon.Reddit),
        )

    public val instagramCandidate: TimelineCandidate<LiveWebTimelineData> =
        instagram.candidate(
            data = LiveWebTimelineData("instagram"),
            title = UiText.Raw("Instagram"),
            icon = IconType.Material(UiIcon.Instagram),
        )

    public val linkedinCandidate: TimelineCandidate<LiveWebTimelineData> =
        linkedin.candidate(
            data = LiveWebTimelineData("linkedin"),
            title = UiText.Raw("LinkedIn"),
            icon = IconType.Material(UiIcon.LinkedIn),
        )
}

// The page draws its own content, so a merged feed gets nothing from these tabs.
private object NoLiveWebPostsLoader : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "live_web_empty"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> = PagingResult(endOfPaginationReached = true)
}
