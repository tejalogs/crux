package org.crux.ui.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import org.crux.ui.render.UiRichText

@Serializable
@Immutable
public data class UiTranslatableText(
    public val original: UiRichText,
    public val translation: UiRichText? = null,
)
