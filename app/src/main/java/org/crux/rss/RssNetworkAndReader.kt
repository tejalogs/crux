package org.crux.rss

import com.fleeksoft.ksoup.Ksoup
import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.charset
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import moe.tlaster.readability.Readability
import org.crux.data.network.PublicUrlValidator
import org.crux.data.network.getPublic
import org.crux.data.network.publicWebClient
import java.io.IOException
import java.nio.charset.Charset
import java.util.Locale

internal sealed interface FeedFetchResult {
    data object NotModified : FeedFetchResult

    data class Updated(
        val feed: ParsedFeed,
        val etag: String?,
        val lastModified: String?,
    ) : FeedFetchResult
}

internal class RssArticleExtractionException : Exception("Unable to extract article content")

internal class RssArticleTimeoutException : Exception("Article request timed out")

internal fun Throwable.rssArticleFailureReason(): RssArticleFailureReason =
    when (this) {
        is RssArticleTimeoutException,
        is HttpRequestTimeoutException,
        is ConnectTimeoutException,
        is SocketTimeoutException,
        -> RssArticleFailureReason.TimedOut

        is RssArticleExtractionException -> RssArticleFailureReason.NoArticle

        is ResponseException -> RssArticleFailureReason.HttpError

        is IOException,
        is kotlinx.io.IOException,
        -> RssArticleFailureReason.Unreachable

        else -> cause?.takeIf { it !== this }?.rssArticleFailureReason() ?: RssArticleFailureReason.Other
    }

internal fun Throwable.rssArticleHttpStatus(): Int? =
    when (this) {
        is ResponseException -> response.status.value
        else -> cause?.takeIf { it !== this }?.rssArticleHttpStatus()
    }

/**
 * Network fetcher for RSS feeds (with ETag / If-Modified-Since 304 support), HTML feed
 * auto-discovery, and full-article Readability extraction — protected on every hop by
 * [PublicUrlValidator] SSRF checks.
 */
internal class RssNetworkClient(
    private val httpClient: HttpClient = sharedClient,
) {
    suspend fun fetchFeed(
        url: String,
        etag: String? = null,
        lastModified: String? = null,
    ): FeedFetchResult =
        withTimeoutOrNull(FEED_TIMEOUT_MS) {
            val safeUrl = PublicUrlValidator.requireSafePublicHttpUrl(url)
            val response =
                httpClient.getPublic(safeUrl) {
                    header(HttpHeaders.Accept, FEED_ACCEPT_HEADER)
                    if (!etag.isNullOrBlank()) header(HttpHeaders.IfNoneMatch, etag)
                    if (!lastModified.isNullOrBlank()) header(HttpHeaders.IfModifiedSince, lastModified)
                }
            if (response.status == HttpStatusCode.NotModified) {
                return@withTimeoutOrNull FeedFetchResult.NotModified
            }
            if (!response.status.isSuccess()) {
                throw ResponseException(response, "HTTP ${response.status.value}")
            }
            val body = decodeBody(response, maxBytes = MAX_FEED_BYTES)
            val finalUrl = response.call.request.url.toString()
            val parsed = withContext(Dispatchers.Default) { RssFeedParser.parse(body, finalUrl) }
            FeedFetchResult.Updated(
                feed = parsed,
                etag = response.headers[HttpHeaders.ETag],
                lastModified = response.headers[HttpHeaders.LastModified],
            )
        } ?: throw RssArticleTimeoutException()

    /**
     * Accepts a direct feed URL or any website URL (e.g. `arstechnica.com`), returning one or more
     * discovered feeds with resolved titles and favicons.
     */
    suspend fun discoverFeeds(rawInput: String): List<DiscoveredFeed> =
        withTimeoutOrNull(FEED_TIMEOUT_MS) {
            val candidateUrl = normalizeWebUrl(rawInput)
            val safeUrl = PublicUrlValidator.requireSafePublicHttpUrl(candidateUrl)
            val response =
                httpClient.getPublic(safeUrl) {
                    header(HttpHeaders.Accept, "$FEED_ACCEPT_HEADER, text/html;q=0.8")
                }
            if (!response.status.isSuccess()) {
                throw ResponseException(response, "HTTP ${response.status.value}")
            }
            val finalUrl = response.call.request.url.toString()
            val body = decodeBody(response, maxBytes = MAX_FEED_BYTES)

            // 1. Direct RSS / Atom / JSON Feed?
            val direct = runCatching { withContext(Dispatchers.Default) { RssFeedParser.parse(body, finalUrl) } }.getOrNull()
            if (direct != null && (direct.articles.isNotEmpty() || direct.title.isNotBlank())) {
                return@withTimeoutOrNull listOf(
                    DiscoveredFeed(
                        url = finalUrl,
                        title = direct.title,
                        siteUrl = direct.siteUrl,
                        iconUrl = direct.iconUrl ?: defaultFaviconFor(direct.siteUrl ?: finalUrl),
                    ),
                )
            }

            // 2. HTML page -> extract <link rel="alternate" type="application/rss+xml|atom+xml|feed+json">
            withContext(Dispatchers.Default) {
                val doc = Ksoup.parse(body, finalUrl)
                val pageTitle = doc.title().trim()
                val iconUrl =
                    doc.selectFirst("link[rel~=(?i)^(shortcut )?icon$][href], link[rel=apple-touch-icon][href]")
                        ?.absUrl("href")
                        ?.takeIf { it.isNotBlank() }
                        ?: defaultFaviconFor(finalUrl)
                val found = LinkedHashMap<String, DiscoveredFeed>()
                doc.select("link[rel~=alternate][href]").forEach { link ->
                    val type = link.attr("type").lowercase(Locale.ROOT)
                    if (
                        type.contains("rss") ||
                        type.contains("atom") ||
                        type.contains("feed+json") ||
                        type == "application/xml" ||
                        type == "text/xml"
                    ) {
                        val href = link.absUrl("href").ifBlank { RssFeedParser.resolveUrl(finalUrl, link.attr("href")) }
                        if (href.isNotBlank() && PublicUrlValidator.isSafePublicHttpUrl(href)) {
                            val feedTitle = link.attr("title").trim().ifEmpty { pageTitle }.ifEmpty { href }
                            found.putIfAbsent(
                                href.lowercase(Locale.ROOT),
                                DiscoveredFeed(
                                    url = href,
                                    title = feedTitle,
                                    siteUrl = finalUrl,
                                    iconUrl = iconUrl,
                                ),
                            )
                        }
                    }
                }
                found.values.toList()
            }
        } ?: throw RssArticleTimeoutException()

    suspend fun extractArticle(
        url: String,
        descriptionHtml: String? = null,
        title: String? = null,
        fallbackHtml: String? = null,
    ): RssDocument {
        if (descriptionHtml != null) {
            return withContext(Dispatchers.Default) {
                embeddedDocument(descriptionHtml, title, RssDocumentSource.FeedText)
            } ?: throw RssArticleExtractionException()
        }
        return try {
            val extracted = extractFullPage(url)
            val resolvedTitle = title?.takeIf { it.isNotBlank() } ?: extracted.title
            extracted.copy(title = resolvedTitle)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            withContext(Dispatchers.Default) {
                embeddedDocument(fallbackHtml, title, RssDocumentSource.FeedSummary)
            } ?: throw e
        }
    }

    private suspend fun extractFullPage(url: String): RssDocument =
        withTimeoutOrNull(ARTICLE_TIMEOUT_MS) {
            val safeUrl = PublicUrlValidator.requireSafePublicHttpUrl(url)
            val response = httpClient.getPublic(safeUrl)
            if (!response.status.isSuccess()) throw ResponseException(response, "HTTP ${response.status.value}")
            val html = decodeBody(response, maxBytes = MAX_ARTICLE_BYTES)
            val finalUrl = response.call.request.url.toString()
            withContext(Dispatchers.Default) {
                val parsed = Readability(html, finalUrl).parse() ?: throw RssArticleExtractionException()
                if (!parsed.content.hasReadableHtml()) throw RssArticleExtractionException()
                RssDocument(
                    title = parsed.title.orEmpty(),
                    contentHtml = parsed.content,
                    textContent = parsed.textContent.orEmpty(),
                    byline = parsed.byline?.takeIf { it.isNotBlank() },
                    siteName = parsed.siteName?.takeIf { it.isNotBlank() },
                    publishedAt = RssFeedParser.parseFeedDate(parsed.publishedTime),
                    source = RssDocumentSource.Full,
                )
            }
        } ?: throw RssArticleTimeoutException()

    companion object {
        private const val FEED_TIMEOUT_MS = 15_000L
        private const val ARTICLE_TIMEOUT_MS = 15_000L
        private const val MAX_FEED_BYTES = 5 * 1024 * 1024
        private const val MAX_ARTICLE_BYTES = 8 * 1024 * 1024
        private const val FEED_ACCEPT_HEADER =
            "application/rss+xml, application/atom+xml, application/feed+json, application/xml;q=0.9, text/xml;q=0.8"

        private val sharedClient: HttpClient by lazy { publicWebClient() }

        internal fun normalizeWebUrl(raw: String): String {
            val trimmed = raw.trim()
            return if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
                trimmed
            } else {
                "https://$trimmed"
            }
        }

        internal fun defaultFaviconFor(url: String): String? =
            runCatching {
                val uri = java.net.URI(url)
                val scheme = uri.scheme ?: "https"
                val host = uri.host ?: return@runCatching null
                "$scheme://$host/favicon.ico"
            }.getOrNull()

        private suspend fun decodeBody(
            response: HttpResponse,
            maxBytes: Int,
        ): String {
            val bytes = response.readRawBytes()
            val bounded = if (bytes.size > maxBytes) bytes.copyOf(maxBytes) else bytes
            val headerCharset = response.charset()?.name()
            val detected =
                headerCharset
                    ?: detectXmlEncoding(bounded)
                    ?: Charsets.UTF_8.name()
            val charset = runCatching { Charset.forName(detected) }.getOrDefault(Charsets.UTF_8)
            return String(bounded, charset)
        }

        private fun detectXmlEncoding(bytes: ByteArray): String? {
            val head = String(bytes, 0, minOf(bytes.size, 256), Charsets.ISO_8859_1)
            return XML_ENCODING_REGEX.find(head)?.groupValues?.getOrNull(1)?.trim()
        }

        internal fun String?.hasReadableHtml(): Boolean {
            if (isNullOrBlank()) return false
            val body = Ksoup.parse(this).body()
            body.select("script, style, template").remove()
            return body.text().isNotBlank() || body.select("img[src], video[src], audio[src], source[src]").isNotEmpty()
        }

        private fun embeddedDocument(
            html: String?,
            title: String?,
            source: RssDocumentSource,
        ): RssDocument? =
            html?.takeIf { it.hasReadableHtml() }?.let {
                val text = Ksoup.parse(it).apply { select("script, style, template").remove() }.text()
                RssDocument(
                    title = title.orEmpty(),
                    contentHtml = it,
                    textContent = text,
                    source = source,
                )
            }

        private val XML_ENCODING_REGEX = Regex("""encoding\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    }
}
