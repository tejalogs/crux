package org.crux.data.network.rss

import com.fleeksoft.ksoup.Ksoup
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.takeFrom
import kotlinx.serialization.decodeFromString
import nl.adaptivity.xmlutil.serialization.XML
import org.crux.data.network.FaviconService
import org.crux.data.network.ktorClient
import org.crux.data.network.rss.model.Feed
import org.crux.data.repository.tryRun
import org.crux.ui.model.mapper.link

internal object RssService {
    private val client = ktorClient { }

    suspend fun fetch(url: String): Feed {
        val response = client.get(url).decodedText()
        return parseFeedText(response)
    }

    suspend fun detectLinkSources(url: String): List<String> =
        findFeedLinks(client.get(url).decodedText(), url)
            .takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("No RSS or Atom feeds found at the provided URL: $url")

    /**
     * The feed's icon, found from the feed that was already downloaded: its own icon, else the
     * favicon of the site it links to. Never downloads the feed a second time.
     */
    suspend fun iconFor(
        feed: Feed,
        url: String,
    ): String? {
        val feedIcon = (feed as? Feed.Atom)?.icon
        if (feedIcon != null) {
            return feedIcon
        }
        return FaviconService.fetchIcon(feed.link ?: url)
    }

    suspend fun fetchIcon(url: String): String? {
        val feed =
            tryRun {
                fetch(url)
            }.getOrNull()
        return if (feed != null) iconFor(feed, url) else FaviconService.fetchIcon(url)
    }

    private suspend fun HttpResponse.decodedText(): String = decodedBodyText()
}

/** The body as text in the charset the server, the XML declaration or the page's meta tag names. */
internal suspend fun HttpResponse.decodedBodyText(): String {
    val bytes = body<ByteArray>()
    return decodeResponseBody(
        bytes = bytes,
        headerCharset =
            headers[HttpHeaders.ContentType]
                ?.let(::detectHeaderCharset),
    )
}

private val FEED_LINK_TYPES = listOf("application/rss+xml", "application/atom+xml")
private val GENERIC_XML_TYPES = listOf("application/xml", "text/xml")

/**
 * Feed addresses a page advertises in its head, as absolute URLs. Plain `application/xml` and
 * `text/xml` links count too when they are marked `rel="alternate"`, since many sites use them.
 */
internal fun findFeedLinks(
    html: String,
    pageUrl: String,
): List<String> {
    val document = Ksoup.parse(html, pageUrl)
    val base =
        document
            .select("base[href]")
            .firstOrNull()
            ?.attr("href")
            ?.let { resolveAgainst(pageUrl, it) } ?: pageUrl
    return document
        .select("link[href][type]")
        .filter { link ->
            val type = link.attr("type").lowercase()
            FEED_LINK_TYPES.any { type.contains(it) } ||
                (GENERIC_XML_TYPES.any { type.contains(it) } && link.attr("rel").contains("alternate", ignoreCase = true))
        }.map { it.attr("href").trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { resolveAgainst(base, it) }
        .distinct()
}

/** Resolves [reference] against [base] with the full rules for relative paths, protocol-relative links and ports. */
internal fun resolveAgainst(
    base: String,
    reference: String,
): String? =
    runCatching { URLBuilder(Url(base)).takeFrom(reference).buildString() }
        .getOrNull()
        ?.takeIf { it.startsWith("http", ignoreCase = true) }

internal fun parseFeedText(text: String): Feed = RssServiceXml.decodeFromString(text)

internal fun decodeResponseBody(
    bytes: ByteArray,
    headerCharset: String?,
): String {
    val candidates =
        buildList {
            addCharset(headerCharset)
            addCharset(detectXmlCharset(bytes))
            addCharset(detectHtmlCharset(bytes))
            addCharset("utf-8")
            addCharset("iso-8859-1")
        }
    candidates.forEach { charset ->
        decodeBytes(bytes, charset)?.let {
            return it
        }
    }
    return bytes.decodeToString()
}

private fun MutableList<String>.addCharset(value: String?) {
    if (!value.isNullOrBlank() && none { it.equals(value, ignoreCase = true) }) {
        add(value)
    }
}

private fun detectXmlCharset(bytes: ByteArray): String? =
    XML_DECLARATION_ENCODING_REGEX.find(asciiPrefix(bytes, limit = 256))?.groupValues?.getOrNull(1)

private fun detectHtmlCharset(bytes: ByteArray): String? =
    HTML_META_CHARSET_REGEX.find(asciiPrefix(bytes, limit = 4096))?.groupValues?.getOrNull(1)

private fun detectHeaderCharset(contentType: String): String? = HEADER_CHARSET_REGEX.find(contentType)?.groupValues?.getOrNull(1)

private fun asciiPrefix(
    bytes: ByteArray,
    limit: Int,
): String =
    buildString(minOf(bytes.size, limit)) {
        bytes
            .take(limit)
            .forEach { byte ->
                val value = byte.toInt() and 0xff
                append(
                    when (value) {
                        0x09, 0x0a, 0x0d -> value.toChar()
                        in 0x20..0x7e -> value.toChar()
                        else -> ' '
                    },
                )
            }
    }

private val XML_DECLARATION_ENCODING_REGEX =
    Regex("""<\?xml[^>]*encoding\s*=\s*["']\s*([^"'\s>]+)\s*["']""", RegexOption.IGNORE_CASE)

private val HTML_META_CHARSET_REGEX =
    Regex("""<meta[^>]*charset\s*=\s*["']?\s*([^"'\s/>]+)""", RegexOption.IGNORE_CASE)

private val HEADER_CHARSET_REGEX =
    Regex("""(?:^|;)\s*charset\s*=\s*"?([^";\s]+)""", RegexOption.IGNORE_CASE)

private val RssServiceXml by lazy {
    XML.v1 {
        policy {
            autoPolymorphic = true
            ignoreUnknownChildren()
        }
        defaultToGenericParser = true
    }
}
