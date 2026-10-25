package dev.dimension.flare.data.platform

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Immutable
@Serializable
@SerialName("LinkedInCredential")
public data class LinkedInCredential(
    val cookies: String,
    val userId: String? = null,
    val username: String? = null,
)
