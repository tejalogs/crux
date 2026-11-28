package org.crux.data.datastore.model

import kotlinx.serialization.Serializable

private const val DEFAULT_SERVER_URL = ""

@Serializable
internal data class CruxConfig(
    val serverUrl: String = DEFAULT_SERVER_URL,
)
