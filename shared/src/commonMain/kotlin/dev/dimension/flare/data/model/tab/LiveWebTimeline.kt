package dev.dimension.flare.data.model.tab

import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.asType
import kotlinx.serialization.Serializable
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
}

// The page draws its own content, so a merged feed gets nothing from these tabs.
private object NoLiveWebPostsLoader : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "live_web_empty"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> = PagingResult(endOfPaginationReached = true)
}
