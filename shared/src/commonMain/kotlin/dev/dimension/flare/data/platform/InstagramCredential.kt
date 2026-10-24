package dev.dimension.flare.data.platform

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Immutable
@Serializable
@SerialName("InstagramCredential")
public data class InstagramCredential(
    val cookies: String,
    val userId: String? = null,
    val username: String? = null,
)
