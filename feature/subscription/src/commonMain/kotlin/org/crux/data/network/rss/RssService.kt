package org.crux.data.network.rss

import com.fleeksoft.ksoup.Ksoup
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import io.ktor.utils.io.readBuffer
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.io.readByteArray
import kotlinx.serialization.decodeFromString
import nl.adaptivity.xmlutil.XmlException
import nl.adaptivity.xmlutil.serialization.XML
import org.crux.data.network.FaviconService
import org.crux.data.network.PublicUrlValidator
import org.crux.data.network.getPublic
import org.crux.data.network.publicWebClient
import org.crux.data.network.rss.model.Feed
import org.crux.data.repository.tryRun
import org.crux.ui.model.mapper.link

private const val MAX_RSS_RESPONSE_BYTES: Long = 10L * 1024 * 1024

/**
 * How many of the feeds a page advertises are tried. A page can list thousands of feed links,
 * and each one is downloaded (up to [MAX_RSS_RESPONSE_BYTES]) to read its title.
 */
internal const val MAX_DISCOVERED_FEED_LINKS: Int = 5

/** How many advertised feeds are downloaded at the same time during discovery. */
internal const val MAX_CONCURRENT_DISCOVERY_FETCHES: Int = 3

internal object RssService {
    private val client = publicWebClient()

    suspend fun fetch(url: String): Feed = fetchFeed(client, url)

    /** The feeds the page at [url] advertises, at most [MAX_DISCOVERED_FEED_LINKS] of them. */
    suspend fun detectLinkSources(url: String): List<String> {
        val safeUrl = PublicUrlValidator.requireSafePublicHttpUrl(url)
        return findFeedLinks(client.getPublic(safeUrl).decodedText(), safeUrl)
            .take(MAX_DISCOVERED_FEED_LINKS)
            .takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("No RSS or Atom feeds found at the provided URL: $safeUrl")
    }

    /**
     * The feed's icon, found from the feed that was already downloaded: its own icon, else the
     * favicon of the site it links to. Never downloads the feed a second time.
     */
    suspend fun iconFor(
        feed: Feed,
        url: String,
    ): String? {
        // The feed names its own icon, so it gets the same public-address check as a favicon
        // found on the site: otherwise every render of the source list would GET a LAN address.
        val feedIcon =
            (feed as? Feed.Atom)
                ?.icon
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { resolveAgainst(url, it) }
        if (feedIcon != null && PublicUrlValidator.isSafePublicHttpUrl(feedIcon)) {
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

/**
 * Downloads and parses the feed at [url]. Redirects are followed and re-checked by [getPublic];
 * a final status outside 2xx is reported as [FeedHttpStatusException] instead of handing an
 * error page to the XML parser, where it would surface as a confusing parse error.
 */
internal suspend fun fetchFeed(
    client: HttpClient,
    url: String,
): Feed {
    val response = client.getPublic(url)
    if (!response.status.isSuccess()) throw FeedHttpStatusException(response)
    return parseFeedText(response.decodedBodyText())
}

/**
 * The feed address answered with a non-2xx status. It is a [ResponseException], like the
 * reader's, so callers can still tell permanent (4xx) from temporary (5xx, 408, 429) failures.
 */
internal class FeedHttpStatusException(
    response: HttpResponse,
) : ResponseException(response, "") {
    override val message: String =
        "The feed address answered HTTP ${response.status.value} ${response.status.description}".trim()
}

/** The body as text in the charset the server, the XML declaration or the page's meta tag names. */
internal suspend fun HttpResponse.decodedBodyText(maxBytes: Long = MAX_RSS_RESPONSE_BYTES): String {
    val packet = bodyAsChannel().readBuffer(maxBytes + 1L)
    val bytes = packet.readByteArray()
    if (bytes.size.toLong() > maxBytes) {
        throw IllegalStateException("Response exceeds maximum allowed size ($maxBytes bytes).")
    }
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

internal fun parseFeedText(text: String): Feed {
    rejectEntityDeclarations(text)
    return RssServiceXml.decodeFromString(text)
}

/**
 * Runs [transform] on each of [items] with at most [maxConcurrent] running at once, keeping the
 * input order. Used for feed discovery so a page cannot start dozens of large downloads at once.
 */
internal suspend fun <T, R> boundedParallelMap(
    items: List<T>,
    maxConcurrent: Int = MAX_CONCURRENT_DISCOVERY_FETCHES,
    transform: suspend (T) -> R,
): List<R> {
    val limiter = Semaphore(maxConcurrent)
    return coroutineScope {
        items.map { item -> async { limiter.withPermit { transform(item) } } }.awaitAll()
    }
}

/**
 * Refuses XML that declares entities in a DOCTYPE. Feeds and OPML never need a DTD, and the
 * generic xmlutil parser expands internal entities with no depth or size limit, so a 1 KB
 * "billion laughs" document would build gigabytes of text and freeze or crash the app.
 */
internal fun rejectEntityDeclarations(text: String) {
    val doctype = text.indexOf("<!DOCTYPE", ignoreCase = true)
    if (doctype < 0) return
    if (text.indexOf("<!ENTITY", startIndex = doctype, ignoreCase = true) >= 0) {
        throw XmlException("Refused XML document that declares entities in a DOCTYPE.")
    }
}

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
