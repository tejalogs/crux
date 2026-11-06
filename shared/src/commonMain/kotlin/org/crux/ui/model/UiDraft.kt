package org.crux.ui.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import org.crux.data.datasource.microblog.ComposeData
import org.crux.model.MicroBlogKey
import org.crux.ui.render.UiDateTime
import kotlin.native.HiddenFromObjC

@Immutable
public data class UiDraft(
    val groupId: String,
    val status: UiDraftStatus,
    val updatedAt: UiDateTime,
    val accounts: ImmutableList<UiDraftAccount>,
    val data: ComposeData,
    val medias: ImmutableList<UiDraftMedia>,
)

@Immutable
public data class UiDraftAccount(
    val account: UiAccount,
    val avatar: UiMedia.Image? = null,
)

@Immutable
public data class UiDraftMedia(
    val cachePath: String,
    val fileName: String?,
    val type: UiDraftMediaType,
    val altText: String?,
)

@Immutable
public enum class UiDraftMediaType {
    IMAGE,
    VIDEO,
    OTHER,
}

@Immutable
public enum class UiDraftStatus {
    SENDING,
    FAILED,
    DRAFT,
}

@HiddenFromObjC
public val UiDraft.primaryAccountKey: MicroBlogKey?
    get() = accounts.firstOrNull()?.account?.accountKey
