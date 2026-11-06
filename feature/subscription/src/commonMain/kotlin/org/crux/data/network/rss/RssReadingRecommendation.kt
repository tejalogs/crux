package org.crux.data.network.rss

import io.ktor.client.plugins.ResponseException
import io.ktor.http.URLBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import org.crux.data.database.app.model.RssDisplayMode
import org.crux.data.network.rss.model.Feed
import org.crux.ui.model.mapper.resolvedLink

internal suspend fun recommendRssDisplayMode(
    feed: Feed,
    extract: suspend (String) -> DocumentData,
): RssDisplayMode? =
    withTimeoutOrNull(6_000) {
        val samples =
            feed
                .readingSamples()
                .distinctBy { it.first }
                .filter {
                    absoluteArticleUrl.containsMatchIn(it.first) &&
                        runCatching {
                            // Url fills a missing host from the platform origin, so inspect the builder instead.
                            URLBuilder(it.first).host.isNotBlank()
                        }.getOrDefault(false)
                }.take(2)
                .toList()
        if (samples.isEmpty()) return@withTimeoutOrNull null
        var uncertain = false
        for ((url, _) in samples) {
            try {
                extract(url)
                return@withTimeoutOrNull RssDisplayMode.FULL_CONTENT
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                if (!exception.isDefinitiveArticleFailure()) uncertain = true
            }
        }
        when {
            uncertain -> null
            samples.all { (_, contents) -> contents.any { it.hasRssContent() } } -> RssDisplayMode.DESCRIPTION_ONLY
            else -> RssDisplayMode.OPEN_IN_BROWSER
        }
    }

private val absoluteArticleUrl = Regex("""^https?://[^/?#\s\\]+""", RegexOption.IGNORE_CASE)

private fun Feed.readingSamples(): Sequence<Pair<String, List<String?>>> =
    when (this) {
        is Feed.Rss20 -> {
            channel.items.asSequence().mapNotNull { item ->
                item.resolvedLink()?.let { it to listOf(item.contentEncoded, item.description) }
            }
        }

        is Feed.Atom -> {
            entries.asSequence().mapNotNull { entry ->
                entry.resolvedLink()?.let { it to listOf(entry.content?.value, entry.summary?.value) }
            }
        }

        is Feed.RDF -> {
            items.asSequence().mapNotNull { item ->
                item.resolvedLink()?.let { it to listOf(item.contentEncoded, item.description) }
            }
        }
    }

private fun Throwable.isDefinitiveArticleFailure(): Boolean =
    this is ArticleExtractionException ||
        (this is ResponseException && response.status.value in 300..499 && response.status.value !in setOf(408, 429)) ||
        isRssTransportSecurityFailure() ||
        (cause?.takeIf { it !== this }?.isDefinitiveArticleFailure() == true)

internal expect fun Throwable.isRssTransportSecurityFailure(): Boolean

internal fun String.toRssWebUrl(): String =
    trim().let {
        if (it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)) it else "https://$it"
    }
