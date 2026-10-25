package dev.dimension.flare.data.datasource.linkedin

import dev.dimension.flare.common.JSON
import dev.dimension.flare.common.decodeFromStringWithNullableFallback
import dev.dimension.flare.data.network.ktorClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess

public const val LINKEDIN_FEED_TIMELINE_URL: String = "https://www.linkedin.com/voyager/api/feed/updatesV2"
public const val LINKEDIN_FEED_DASH_URL: String = "https://www.linkedin.com/voyager/api/feed/dash/updates"
public const val LINKEDIN_USER_AGENT: String =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

public class LinkedInFeedService(
    private val client: HttpClient = ktorClient(),
) {

    public suspend fun getHomeTimeline(
        cookies: String,
        cursor: String? = null,
    ): LinkedInFeedResponse? {
        if (cookies.isBlank()) return null
        val csrfToken = extractCsrfToken(cookies)

        val primaryResult = fetchFromEndpoint(LINKEDIN_FEED_TIMELINE_URL, cookies, csrfToken, cursor)
        if (primaryResult != null && primaryResult.elements.isNotEmpty()) {
            return primaryResult
        }

        // Fallback to Dash updates endpoint if primary returned empty
        val fallbackResult = fetchFromEndpoint(LINKEDIN_FEED_DASH_URL, cookies, csrfToken, cursor)
        return fallbackResult ?: primaryResult
    }

    private suspend fun fetchFromEndpoint(
        url: String,
        cookies: String,
        csrfToken: String?,
        cursor: String?,
    ): LinkedInFeedResponse? {
        return try {
            val response = client.get(url) {
                header(HttpHeaders.UserAgent, LINKEDIN_USER_AGENT)
                header(HttpHeaders.Cookie, cookies)
                header(HttpHeaders.Accept, "application/json, application/vnd.linkedin.normalized+json+2.1")
                header(HttpHeaders.Referrer, "https://www.linkedin.com/feed/")
                header("x-restli-protocol-version", "2.0.0")
                header("sec-ch-ua", "\"Chromium\";v=\"130\", \"Google Chrome\";v=\"130\", \"Not?A_Brand\";v=\"99\"")
                header("sec-ch-ua-mobile", "?0")
                header("sec-ch-ua-platform", "\"macOS\"")
                header("sec-fetch-dest", "empty")
                header("sec-fetch-mode", "cors")
                header("sec-fetch-site", "same-origin")
                header("accept-language", "en-US,en;q=0.9")
                csrfToken?.let { header("csrf-token", it) }

                parameter("count", "20")
                parameter("q", "feedUpdates")
                cursor?.let { parameter("start", it) }
            }

            if (!response.status.isSuccess()) {
                return null
            }

            val bodyText = response.bodyAsText()
            LinkedInFeedParser.parse(bodyText)
        } catch (_: Exception) {
            null
        }
    }

    public companion object {
        public fun extractCsrfToken(cookies: String): String? =
            cookies.split(";")
                .map { it.trim() }
                .firstOrNull { it.startsWith("JSESSIONID=") }
                ?.substringAfter("JSESSIONID=")
                ?.removeSurrounding("\"")

        public fun extractUserId(cookies: String): String? {
            // 1. Try to find explicit member id / li_at token fingerprint
            val liAt = cookies.split(";")
                .map { it.trim() }
                .firstOrNull { it.startsWith("li_at=") }
                ?.substringAfter("li_at=")

            if (!liAt.isNullOrBlank()) {
                // Return consistent pseudo-id derived from session token
                val prefix = liAt.take(16)
                return "li_user_${prefix.hashCode()}"
            }
            return null
        }

        public fun hasActiveSession(cookies: String): Boolean =
            cookies.contains("li_at=")
    }
}
