package org.crux.data.network

import de.jensklingenberg.ktorfit.Ktorfit
import de.jensklingenberg.ktorfit.converter.Converter
import de.jensklingenberg.ktorfit.converter.ResponseConverterFactory
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.encodedPath
import kotlinx.serialization.json.Json
import org.crux.common.BuildConfig
import org.crux.common.JSON
import org.crux.data.privacy.installPrivacy
import org.crux.data.repository.DebugRepository
import org.crux.data.repository.LogSanitizer
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
        install(AiEndpointHeadersLogging)
        install(Logging) {
            logger = CruxLogger
            sanitizeHeader { header -> isSensitiveLogHeader(header) }
            filter {
                // AI requests carry whole conversations and tool results; they are logged
                // headers-only by AiEndpointHeadersLogging instead.
                isNetworkLoggingEnabled() && !it.isMediaUpload() && !isAiEndpointPath(it.url.encodedPath)
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

private fun isNetworkLoggingEnabled(): Boolean = BuildConfig.debug || DebugRepository.isEnabled

private fun isSensitiveLogHeader(header: String): Boolean =
    header.equals(HttpHeaders.Authorization, ignoreCase = true) ||
        header.equals(HttpHeaders.Cookie, ignoreCase = true) ||
        header.equals(HttpHeaders.SetCookie, ignoreCase = true) ||
        header.equals("X-Api-Key", ignoreCase = true) ||
        header.equals("X-Goog-Api-Key", ignoreCase = true) ||
        header.equals("X-Csrf-Token", ignoreCase = true)

/**
 * True for OpenAI-compatible, Ollama and Gemini model endpoints. Their bodies hold prompts, chat
 * history and tool results, so they must never reach the debug log.
 */
internal fun isAiEndpointPath(encodedPath: String): Boolean {
    val path = encodedPath.trimEnd('/').lowercase()
    return AI_ENDPOINT_PATH_SUFFIXES.any { path.endsWith(it) }
}

private val AI_ENDPOINT_PATH_SUFFIXES =
    listOf(
        "/completions",
        "/responses",
        "/embeddings",
        "/moderations",
        "/api/chat",
        "/api/generate",
        "/api/embed",
        ":generatecontent",
        ":streamgeneratecontent",
    )

/** Logs AI endpoint traffic as request line, status and headers only, never bodies or query strings. */
private val AiEndpointHeadersLogging =
    createClientPlugin("CruxAiEndpointHeadersLogging") {
        onRequest { request, _ ->
            val path = request.url.encodedPath
            if (isNetworkLoggingEnabled() && isAiEndpointPath(path)) {
                CruxLogger.log(
                    buildString {
                        appendLine("REQUEST: ${request.method.value} ${request.url.protocol.name}://${request.url.host}$path")
                        appendLogHeaders(request.headers.entries())
                        append("BODY: omitted for AI endpoint")
                    },
                )
            }
        }
        onResponse { response ->
            val url: Url = response.call.request.url
            if (isNetworkLoggingEnabled() && isAiEndpointPath(url.encodedPath)) {
                CruxLogger.log(
                    buildString {
                        appendLine("RESPONSE: ${response.status} from ${url.protocol.name}://${url.host}${url.encodedPath}")
                        appendLogHeaders(response.headers.entries())
                        append("BODY: omitted for AI endpoint")
                    },
                )
            }
        }
    }

private fun StringBuilder.appendLogHeaders(headers: Set<Map.Entry<String, List<String>>>) {
    appendLine("HEADERS")
    headers.forEach { (name, values) ->
        val value = if (isSensitiveLogHeader(name)) "***" else values.joinToString("; ")
        appendLine("-> $name: $value")
    }
}

internal data object CruxLogger : io.ktor.client.plugins.logging.Logger {
    override fun log(message: String) {
        val sanitized = LogSanitizer.sanitize(message)
        if (BuildConfig.debug) {
            println(sanitized)
        }
        DebugRepository.log(sanitized)
    }
}
