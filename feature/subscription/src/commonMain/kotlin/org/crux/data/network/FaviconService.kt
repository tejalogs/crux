package org.crux.data.network

import com.fleeksoft.ksoup.Ksoup
import io.ktor.client.request.get
import io.ktor.http.Url
import org.crux.data.network.rss.decodedBodyText
import org.crux.data.repository.tryRun

private const val MAX_FAVICON_HTML_BYTES: Long = 2L * 1024 * 1024

internal object FaviconService {
    private val client = ktorClient { }

    suspend fun fetchIcon(url: String): String? {
        val actualUrl =
            if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
                url
            } else {
                "https://$url"
            }
        if (!PublicUrlValidator.isSafePublicHttpUrl(actualUrl)) return null
        val webContent =
            tryRun {
                client.get(actualUrl).decodedBodyText(maxBytes = MAX_FAVICON_HTML_BYTES)
            }.getOrNull() ?: return null
        findFaviconUrl(actualUrl, webContent)?.takeIf { PublicUrlValidator.isSafePublicHttpUrl(it) }?.let {
            return it
        }
        val parsedUrl = Url(actualUrl)
        val favIcon = "https://${parsedUrl.host}/favicon.ico"
        if (!PublicUrlValidator.isSafePublicHttpUrl(favIcon)) return null
        val hasFavIcon =
            tryRun {
                val response = client.get(favIcon)
                if (response.status.value !in 200..299) {
                    throw Exception("Failed to fetch favicon: ${response.status}")
                }
            }
        return if (hasFavIcon.isSuccess) {
            favIcon
        } else {
            null
        }
    }
}

internal fun findFaviconUrl(
    pageUrl: String,
    webContent: String,
): String? {
    val parsedUrl = Url(pageUrl)
    val document = Ksoup.parse(webContent)
    val iconLink =
        document
            .select(
                """
                link[rel~=(?i)(?:^|\s)icon(?:\s|$)],
                link[rel~=(?i)^(?=.*\bshortcut\b)(?=.*\bicon\b).*$]
                """.trimIndent(),
            ).largestIcon()
            ?: document
                .select(
                    """
                    link[rel~=(?i)(?:^|\s)(?:apple-touch-icon(?:-precomposed)?|mask-icon)(?:\s|$)]
                    """.trimIndent(),
                ).largestIcon()
            ?: return null
    val iconHref = iconLink.attr("href").ifBlank { return null }
    return when {
        iconHref.startsWith("http", ignoreCase = true) -> iconHref
        iconHref.startsWith("//") -> "https:$iconHref"
        iconHref.startsWith("/") -> "https://${parsedUrl.host}$iconHref"
        else -> "https://${parsedUrl.host}/$iconHref"
    }
}

private fun com.fleeksoft.ksoup.select.Elements.largestIcon() =
    maxByOrNull {
        it
            .attribute("sizes")
            ?.value
            ?.split('x')
            ?.firstOrNull()
            ?.toIntOrNull() ?: 0
    }
