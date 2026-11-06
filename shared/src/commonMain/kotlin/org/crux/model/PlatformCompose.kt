package org.crux.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import org.crux.data.datasource.microblog.ComposeType
import org.crux.ui.model.UiHandle
import org.crux.ui.model.UiTimelineV2
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public data class ComposeInitialTextContext(
    public val post: UiTimelineV2.Post,
    public val quotes: ImmutableList<UiTimelineV2.Post>,
    public val composeType: ComposeType,
    public val currentUserHandle: UiHandle,
    public val selectedAccountKey: MicroBlogKey,
)

@Immutable
public data class InitialText(
    public val text: String,
    public val cursorPosition: Int,
)
