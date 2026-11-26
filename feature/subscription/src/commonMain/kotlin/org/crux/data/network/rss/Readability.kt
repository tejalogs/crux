package org.crux.data.network.rss

import androidx.compose.runtime.Immutable
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import moe.tlaster.readability.Readability
import org.crux.data.network.PublicUrlValidator
import org.crux.data.network.getPublic
import org.crux.data.network.publicWebClient
import org.crux.data.repository.tryRun
import org.crux.ui.render.UiDateTime
import org.crux.ui.render.parseHtml
import org.crux.ui.render.toUi
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.time.Instant

/**
 * One HTTP client for every reader. Each Readability used to build its own, which was never
 * closed, so opening articles and pressing Retry kept adding clients.
 */
private val sharedReaderClient: HttpClient by lazy { publicWebClient() }

internal class Readability(
    private val client: HttpClient = sharedReaderClient,
    private val parserDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    suspend fun extract(url: String): DocumentData =
        withTimeoutOrNull(15_000) {
            val safeUrl = PublicUrlValidator.requireSafePublicHttpUrl(url)
            // Redirects are followed (and re-checked) by getPublic, so only the final status matters.
            val response = client.getPublic(safeUrl)
            // Same exception type expectSuccess raised, so callers can still tell permanent
            // (4xx) from temporary (5xx, 408, 429) failures by status.
            if (!response.status.isSuccess()) throw ResponseException(response, "")
            val html = response.decodedBodyText()
            withContext(parserDispatcher) {
                val article =
                    Readability(
                        html,
                        response.call.request.url
                            .toString(),
                    ).parse()
                        ?: throw ArticleExtractionException()
                if (!article.content.hasRssContent()) throw ArticleExtractionException()
                DocumentData(
                    title = article.title,
                    content = article.content,
                    textContent = article.textContent,
                    length = article.length,
                    excerpt = article.excerpt,
                    byline = article.byline,
                    dir = article.dir,
                    siteName = article.siteName,
                    lang = article.lang,
                    publishedTime = article.publishedTime,
                )
            }
        } ?: throw ArticleTimeoutException()

    suspend fun load(
        url: String,
        descriptionHtml: String? = null,
        title: String? = null,
        fallbackHtml: String? = null,
    ): DocumentData {
        if (descriptionHtml != null) {
            return withContext(parserDispatcher) { embeddedDocument(descriptionHtml, title, DocumentSource.FeedText) }
                ?: throw ArticleExtractionException()
        }
        return try {
            val doc = extract(url)
            val resolvedTitle = title?.takeIf { it.isNotBlank() } ?: doc.title
            doc.copy(title = resolvedTitle)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            withContext(parserDispatcher) { embeddedDocument(fallbackHtml, title, DocumentSource.FeedSummary) } ?: throw exception
        }
    }
}

/** Why an article could not be shown, so the reader can say so in plain words. */
public enum class ArticleFailureReason {
    /**
     * The site could not be reached: no connection, an unknown host, or a transport failure
     * such as a TLS handshake or a reset connection. Worded neutrally, since it is not always
     * the reader's connection.
     */
    Unreachable,

    /** The site took longer than the reader waits. */
    TimedOut,

    /** The page loaded but held no readable article. */
    NoArticle,

    /** The site answered with an error status. */
    HttpError,

    /** Anything else, such as an address the reader refuses to open. */
    Other,
}

/** The [ArticleFailureReason] for a failure thrown by [Readability.load]. */
public fun Throwable.articleFailureReason(): ArticleFailureReason =
    when (this) {
        is ArticleTimeoutException,
        is HttpRequestTimeoutException,
        is ConnectTimeoutException,
        is SocketTimeoutException,
        -> ArticleFailureReason.TimedOut

        is ArticleExtractionException -> ArticleFailureReason.NoArticle

        is ResponseException -> ArticleFailureReason.HttpError

        is IOException -> ArticleFailureReason.Unreachable

        else -> cause?.takeIf { it !== this }?.articleFailureReason() ?: ArticleFailureReason.Other
    }

/** The HTTP status of an [ArticleFailureReason.HttpError], or null. */
public fun Throwable.articleHttpStatus(): Int? =
    when (this) {
        is ResponseException -> response.status.value
        else -> cause?.takeIf { it !== this }?.articleHttpStatus()
    }

internal class ArticleExtractionException : Exception("Unable to extract article content")

internal class ArticleTimeoutException : Exception("Article request timed out")

internal fun String?.hasRssContent(): Boolean {
    if (isNullOrBlank()) return false
    val body = Ksoup.parse(this).body()
    body.select("script, style, template").remove()
    return body.text().isNotBlank() || body.select("img[src], video[src], audio[src], source[src]").isNotEmpty()
}

private fun embeddedDocument(
    html: String?,
    title: String?,
    source: DocumentSource,
): DocumentData? =
    html?.takeIf { it.hasRssContent() }?.let {
        DocumentData(
            title = title.orEmpty(),
            content = it,
            textContent = Ksoup.parse(it).apply { select("script, style, template").remove() }.text(),
            length = null,
            excerpt = null,
            byline = null,
            dir = null,
            siteName = null,
            lang = null,
            publishedTime = null,
            source = source,
        )
    }

/** Where the text the reader shows came from. */
public enum class DocumentSource {
    /** The article, extracted from the page. */
    Full,

    /** Extracting the article failed, so the feed's own summary of it stands in. */
    FeedSummary,

    /** The feed's own text, because the source is set to show feed text only. */
    FeedText,
}

@Immutable
@Serializable
public data class DocumentData(
    val title: String,
    val content: String,
    val textContent: String,
    val length: Int?,
    val excerpt: String?,
    val byline: String?,
    val dir: String?,
    val siteName: String?,
    val lang: String?,
    val publishedTime: String?,
    // Not part of the article: kept out of the JSON handed to summaries.
    @Transient val source: DocumentSource = DocumentSource.Full,
) {
    val publishDateTime: UiDateTime? by lazy {
        publishedTime?.let {
            tryRun {
                Instant.parse(it)
            }.getOrNull()?.toUi()
        }
    }

    @OptIn(ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    val element: Element by lazy {
        parseHtml(content)
    }
}
