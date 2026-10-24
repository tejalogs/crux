package dev.dimension.flare.data.network.bluesky

import kotlin.concurrent.Volatile

public object BlueskyOAuthConfig {
    public const val DEFAULT_CLIENT_METADATA_URL: String = "https://flareapp.moe/client-metadata.json"
    public const val DEFAULT_REDIRECT_URI: String = "https://flareapp.moe/callback"

    @Volatile
    public var clientMetadataUrl: String = DEFAULT_CLIENT_METADATA_URL

    @Volatile
    public var defaultRedirectUri: String = DEFAULT_REDIRECT_URI
}
