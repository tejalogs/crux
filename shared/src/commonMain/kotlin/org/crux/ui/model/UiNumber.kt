package org.crux.ui.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import org.crux.ui.humanizer.Formatter.humanize

@Serializable
@Immutable
public data class UiNumber public constructor(
    public val value: Long,
) {
    public val humanized: String by lazy {
        value.takeIf { it > 0 }?.humanize().orEmpty()
    }
}
