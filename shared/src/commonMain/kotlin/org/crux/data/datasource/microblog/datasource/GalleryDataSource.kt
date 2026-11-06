package org.crux.data.datasource.microblog.datasource

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import org.crux.common.Cacheable
import org.crux.common.SerializableImmutableList
import org.crux.data.datasource.microblog.paging.RemoteLoader
import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.humanizer.Formatter.humanize
import org.crux.ui.model.ClickContext
import org.crux.ui.model.ClickEvent
import org.crux.ui.model.UiIcon
import org.crux.ui.model.UiMedia
import org.crux.ui.model.UiProfile
import org.crux.ui.model.UiTimelineV2
import org.crux.ui.model.onClicked
import org.crux.ui.render.UiDateTime
import org.crux.ui.render.UiRichText
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public interface GalleryDataSource {
    public fun galleryDetail(statusKey: MicroBlogKey): Cacheable<GalleryDetail>

    public fun galleryComments(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2>

    public fun galleryRecommendations(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2>
}

@Serializable
@Immutable
public data class GalleryDetail(
    val orientation: GalleryOrientation,
    val statusKey: MicroBlogKey,
    val accountType: AccountType,
    val url: String,
    val images: SerializableImmutableList<UiMedia>,
    val title: String,
    val author: UiProfile?,
    val createdAt: UiDateTime,
    val content: UiRichText?,
    val isBookmarked: Boolean,
    val bookmarkAction: ClickEvent,
    val matrix: SerializableImmutableList<Matrix>,
) {
    val onBookmark: ClickContext.() -> Unit by lazy {
        bookmarkAction.onClicked
    }

    @Serializable
    @Immutable
    public data class Matrix(
        public val icon: UiIcon,
        public val count: Long,
    ) {
        public val humanizedCount: String by lazy {
            count.humanize()
        }
    }
}

@Serializable
@Immutable
public enum class GalleryOrientation {
    Horizontal,
    Vertical,
}
