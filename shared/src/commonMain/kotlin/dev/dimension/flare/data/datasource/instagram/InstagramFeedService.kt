package dev.dimension.flare.data.datasource.instagram

import dev.dimension.flare.common.JSON
import dev.dimension.flare.common.decodeFromStringWithNullableFallback
import dev.dimension.flare.data.network.ktorClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess

public const val INSTAGRAM_FEED_TIMELINE_URL: String = "https://www.instagram.com/api/v1/feed/timeline/"
public const val INSTAGRAM_APP_ID: String = "936619743392459"
public const val INSTAGRAM_ASBD_ID: String = "129477"
public const val INSTAGRAM_USER_AGENT: String =
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"

public class InstagramFeedService(
    private val client: HttpClient = ktorClient(),
) {

    public suspend fun getHomeTimeline(
        cookies: String,
        cursor: String? = null,
    ): InstagramTimelineResponse? {
        if (cookies.isBlank()) return null
        val csrfToken = extractCsrfToken(cookies)

        return try {
            val response = client.get(INSTAGRAM_FEED_TIMELINE_URL) {
                header(HttpHeaders.UserAgent, INSTAGRAM_USER_AGENT)
                header(HttpHeaders.Cookie, cookies)
                header(HttpHeaders.Accept, "*/*")
                header(HttpHeaders.Referrer, "https://www.instagram.com/")
                header("X-IG-App-ID", INSTAGRAM_APP_ID)
                header("X-ASBD-ID", INSTAGRAM_ASBD_ID)
                header("X-Requested-With", "XMLHttpRequest")
                csrfToken?.let { header("X-CSRFToken", it) }

                parameter("reason", "warm_start_fetch")
                cursor?.let { parameter("max_id", it) }
            }

            if (!response.status.isSuccess()) {
                return null
            }

            val bodyText = response.bodyAsText()
            JSON.decodeFromStringWithNullableFallback(
                InstagramTimelineResponse.serializer(),
                bodyText,
            )
        } catch (e: Exception) {
            null
        }
    }

    public suspend fun likePost(
        mediaId: String,
        liked: Boolean,
        cookies: String,
    ): Boolean {
        if (cookies.isBlank()) return false
        val csrfToken = extractCsrfToken(cookies) ?: return false
        val action = if (liked) "like" else "unlike"
        val url = "https://www.instagram.com/api/v1/web/likes/$mediaId/$action/"

        return try {
            val response = client.post(url) {
                header(HttpHeaders.UserAgent, INSTAGRAM_USER_AGENT)
                header(HttpHeaders.Cookie, cookies)
                header(HttpHeaders.Accept, "*/*")
                header(HttpHeaders.Referrer, "https://www.instagram.com/")
                header("X-IG-App-ID", INSTAGRAM_APP_ID)
                header("X-ASBD-ID", INSTAGRAM_ASBD_ID)
                header("X-Requested-With", "XMLHttpRequest")
                header("X-CSRFToken", csrfToken)
            }
            response.status.isSuccess()
        } catch (_: Exception) {
            false
        }
    }

    public companion object {
        public fun extractCsrfToken(cookies: String): String? =
            cookies.split(";")
                .map { it.trim() }
                .firstOrNull { it.startsWith("csrftoken=") }
                ?.substringAfter("csrftoken=")

        public fun extractUserId(cookies: String): String? =
            cookies.split(";")
                .map { it.trim() }
                .firstOrNull { it.startsWith("ds_user_id=") }
                ?.substringAfter("ds_user_id=")

        public fun hasActiveSession(cookies: String): Boolean =
            cookies.contains("sessionid=") && cookies.contains("ds_user_id=")
    }
}
