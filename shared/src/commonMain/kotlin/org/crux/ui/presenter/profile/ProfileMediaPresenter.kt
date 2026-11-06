package org.crux.ui.presenter.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.flatMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.crux.common.PagingState
import org.crux.common.refreshSuspend
import org.crux.common.toPagingState
import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiMedia
import org.crux.ui.model.UiTimelineV2
import org.crux.ui.model.contentPostOrNull
import org.crux.ui.presenter.PresenterBase
import org.crux.ui.presenter.home.TimelinePresenter

public class ProfileMediaPresenter(
    private val mediaTimelinePresenter: TimelinePresenter,
    private val showAllImages: Boolean,
) : PresenterBase<ProfileMediaState>() {
    @Composable
    override fun body(): ProfileMediaState {
        val scope = rememberCoroutineScope()
        val state =
            remember(mediaTimelinePresenter, showAllImages) {
                mediaTimelinePresenter.createTransformedPager(
                    scope = scope,
                    showAllImages = showAllImages,
                )
            }.collectAsLazyPagingItems()
                .toPagingState()
        return object : ProfileMediaState {
            override val mediaState = state

            override suspend fun refreshSuspend() {
                mediaState.refreshSuspend()
            }
        }
    }

    public fun getMediaTimelinePresenter(): TimelinePresenter = mediaTimelinePresenter
}

private fun TimelinePresenter.createTransformedPager(
    scope: CoroutineScope,
    showAllImages: Boolean,
): Flow<PagingData<ProfileMedia>> =
    createPager(scope).map { data ->
        data.flatMap { status ->
            val post = status.contentPostOrNull()
            if (post != null) {
                if (showAllImages) {
                    post.images.mapIndexed { index, it ->
                        ProfileMedia(
                            it,
                            post,
                            post.statusKey,
                            index,
                        )
                    }
                } else {
                    post.images
                        .firstOrNull()
                        ?.let {
                            listOf(
                                ProfileMedia(
                                    it,
                                    post,
                                    post.statusKey,
                                    0,
                                ),
                            )
                        }.orEmpty()
                }
            } else {
                emptyList()
            }
        }
    }

@Immutable
public interface ProfileMediaState {
    public val mediaState: PagingState<ProfileMedia>

    public suspend fun refreshSuspend()
}

@Immutable
public data class ProfileMedia internal constructor(
    val media: UiMedia,
    val status: UiTimelineV2,
    val statusKey: MicroBlogKey,
    val index: Int,
) {
    val key: String = "$statusKey-$index"
}
