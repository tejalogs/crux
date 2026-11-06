package org.crux.data.network

import de.jensklingenberg.ktorfit.Ktorfit
import de.jensklingenberg.ktorfit.converter.Converter
import de.jensklingenberg.ktorfit.converter.ResponseConverterFactory
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.HttpRequestBuilder
import kotlinx.serialization.json.Json
import org.crux.common.BuildConfig
import org.crux.common.JSON
import org.crux.data.privacy.installPrivacy
import org.crux.data.repository.DebugRepository
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public fun ktorfit(
    baseUrl: String,
    json: Json = JSON,
    extraConverterFactories: List<Converter.Factory> = emptyList(),
    config: HttpClientConfig<*>.() -> Unit = {},
): Ktorfit =
    de.jensklingenberg.ktorfit.ktorfit {
        baseUrl(baseUrl)
        httpClient(
            ktorClient {
                install(ContentNegotiation) {
                    nullableFallbackJson(json)
                }
                config.invoke(this)
            },
        )
        converterFactories(
            ResponseConverterFactory(),
            *extraConverterFactories.toTypedArray(),
        )
    }

@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
public fun ktorClient(
    config: HttpClientConfig<*>.() -> Unit = {
        install(ContentNegotiation) {
            nullableFallbackJson(JSON)
        }
    },
): HttpClient =
    HttpClient(httpClientEngine) {
        config.invoke(this)
        // Installed after the caller's config so privacy policy is the last word on the wire,
        // regardless of what a network-specific plugin appended earlier.
        installPrivacy()
        install(Logging) {
            logger = CruxLogger
            filter {
                (BuildConfig.debug || DebugRepository.isEnabled) && !it.isMediaUpload()
            }
            level =
                if (BuildConfig.debug) {
                    LogLevel.ALL
                } else {
                    LogLevel.BODY
                }
        }
    }

internal expect val httpClientEngine: HttpClientEngine

// Logging's BODY mode collects the entire outgoing stream. Keep upload payloads out of it,
// including X's base64 form chunks and authenticated upload retries.
internal fun HttpRequestBuilder.isMediaUpload(): Boolean {
    val path = url.pathSegments.joinToString("/")
    return path.endsWith("media/upload.json") ||
        path.endsWith("com.atproto.repo.uploadBlob") ||
        path.endsWith("app.bsky.video.uploadVideo") ||
        path.endsWith("api/v1/media") || path.endsWith("api/v2/media") ||
        path.endsWith("drive/files/create") || path.endsWith("upload") || path.endsWith("statuses/uploadPic")
}

internal data object CruxLogger : io.ktor.client.plugins.logging.Logger {
    override fun log(message: String) {
        if (BuildConfig.debug) {
            println(message)
        }
        DebugRepository.log(message)
    }
}
