package org.crux.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiIcon

@Serializable
public sealed class IconType {
    @Immutable
    @Serializable
    public data class Avatar(
        @SerialName("userKey")
        val accountKey: MicroBlogKey,
    ) : IconType()

    @Immutable
    @Serializable
    public data class Url(
        val url: String,
    ) : IconType()

    @Immutable
    @Serializable
    public data class FavIcon(
        val host: String,
    ) : IconType()

    @Immutable
    @Serializable
    public data class Material(
        val icon: UiIcon,
    ) : IconType()

    @Immutable
    @Serializable
    public data class Mixed(
        val icon: UiIcon,
        @SerialName("userKey")
        val accountKey: MicroBlogKey,
    ) : IconType()
}
