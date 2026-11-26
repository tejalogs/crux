package org.crux.ui.screen.home

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareRequest
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.utils.io.readBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.readByteArray
import org.crux.data.network.PublicUrlValidator
import org.crux.data.network.publicWebClient
import java.net.URI

/**
 * Link shorteners whose redirect Crux looks up itself, so the browser or the reader gets the real
 * address instead of a tracked click. Matched exactly (or with a leading `www.`), never as a suffix:
 * `t.co` must not match `microsoft.co` or `at.co`.
 */
internal val SHORT_LINK_HOSTS: Set<String> = setOf("t.co", "lnkd.in", "bit.ly", "buff.ly", "ow.ly")

/** Redirects followed from one shortener to another before giving up. */
internal const val SHORT_LINK_MAX_HOPS = 3

/**
 * Whole-lookup budget; after this the original link opens as it would have without the lookup. A
 * healthy shortener answers in well under a second, and the tap shows "Opening link…" while it
 * waits, so three seconds is the most a slow network can hold a link back.
 */
internal const val SHORT_LINK_TIMEOUT_MS = 3_000L

/** A lookup this quick opens with no note at all, so a fast answer does not flash "Opening link…". */
internal const val SHORT_LINK_FEEDBACK_DELAY_MS = 250L

/**
 * Most of lnkd.in's "this link will take you to a page that's not on LinkedIn" notice that Crux
 * reads (the page is about 5 KB). No other shortener's body is read.
 */
internal const val SHORT_LINK_PAGE_MAX_BYTES = 64 * 1024

private const val LINKEDIN_SHORT_HOST = "lnkd.in"

private val REDIRECT_STATUSES = setOf(301, 302, 303, 307, 308)

/** True when [url] is an http(s) link on one of [SHORT_LINK_HOSTS]. */
internal fun isShortLinkUrl(url: String): Boolean = shortLinkHostOf(url) != null

internal fun isShortLinkHost(host: String?): Boolean = normalizedShortHost(host) != null

/** The [SHORT_LINK_HOSTS] entry [url] belongs to, or null when it is not an http(s) shortener link. */
private fun shortLinkHostOf(url: String): String? {
    val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return null
    return normalizedShortHost(uri.host)
}

private fun normalizedShortHost(host: String?): String? {
    val h = host?.lowercase()?.trimEnd('.')?.removePrefix("www.") ?: return null
    return h.takeIf { it in SHORT_LINK_HOSTS }
}

/**
 * One shortener answer: its status code, Location header and, only when it was asked for, the start
 * of the page ([SHORT_LINK_PAGE_MAX_BYTES] at most).
 */
internal data class ShortLinkHop(
    val status: Int,
    val location: String?,
    val page: String? = null,
)

/**
 * Fetches one hop. [head] selects HEAD; otherwise GET. [readPage] asks for the start of a 2xx page
 * as well (used for lnkd.in's notice page only). Throws or returns null on failure.
 */
internal interface ShortLinkFetcher {
    suspend fun fetch(
        url: String,
        head: Boolean,
        readPage: Boolean,
    ): ShortLinkHop?
}

/**
 * Plain public client: no cookie store (cookies a shortener sets are dropped), no Referer or Origin,
 * no WebView profile and engine-level redirects off, so the shortener sees one anonymous request from
 * the device and nothing that ties it to the user's signed-in session. Created on first use, off the
 * main thread.
 */
private val shortLinkClient: HttpClient by lazy { publicWebClient() }

internal object PublicShortLinkFetcher : ShortLinkFetcher {
    override suspend fun fetch(
        url: String,
        head: Boolean,
        readPage: Boolean,
    ): ShortLinkHop? =
        shortLinkClient
            .prepareRequest(url) { method = if (head) HttpMethod.Head else HttpMethod.Get }
            .execute { response ->
                val status = response.status.value
                // Crux reads a body only for lnkd.in's notice page, capped. When network logging is
                // turned on (debug builds or the debug log setting), the shared client's logger may
                // still record the response as it does for every other request.
                val page =
                    if (readPage && !head && status in 200..299) {
                        response
                            .bodyAsChannel()
                            .readBuffer(SHORT_LINK_PAGE_MAX_BYTES.toLong())
                            .readByteArray()
                            .decodeToString()
                    } else {
                        null
                    }
                ShortLinkHop(status, response.headers[HttpHeaders.Location], page)
            }
}

/**
 * Lookups run here, apart from the caller, so the caller's timeout never waits on work that cannot be
 * cancelled. [PublicUrlValidator] does a blocking DNS lookup; when one hangs, the caller still gets
 * null on time and the stuck thread finishes on its own, its answer discarded.
 */
private val shortLinkLookupScope = CoroutineScope(SupervisorJob())

/**
 * Looks up where a shortener link ([isShortLinkUrl]) really goes, without cookies or site identity.
 *
 * Every address is checked with [isSafe] (by default [PublicUrlValidator]) before it is contacted or
 * returned, so a redirect into localhost or the home network is refused. Each hop tries HEAD and
 * falls back to GET when HEAD fails or does not redirect. lnkd.in often answers external links with
 * a notice page instead of a redirect; for lnkd.in only, the target is read from that page's single
 * outbound link. Only shortener hosts are contacted: the first target that leaves [SHORT_LINK_HOSTS]
 * is returned without being fetched. Gives up (null) after [maxHops] hops, on a non-http(s) target,
 * on any error, or after [timeoutMs], even when a DNS lookup on [dispatcher] is still stuck.
 */
internal suspend fun resolveShortLink(
    url: String,
    fetcher: ShortLinkFetcher = PublicShortLinkFetcher,
    isSafe: (String) -> Boolean = PublicUrlValidator::isSafePublicHttpUrl,
    maxHops: Int = SHORT_LINK_MAX_HOPS,
    timeoutMs: Long = SHORT_LINK_TIMEOUT_MS,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
): String? {
    val start = url.trim()
    if (!isShortLinkUrl(start)) return null
    val lookup = shortLinkLookupScope.async(dispatcher) { followShortLink(start, fetcher, isSafe, maxHops) }
    return try {
        withTimeoutOrNull(timeoutMs) { lookup.await() }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    } finally {
        lookup.cancel()
    }
}

private suspend fun followShortLink(
    start: String,
    fetcher: ShortLinkFetcher,
    isSafe: (String) -> Boolean,
    maxHops: Int,
): String? {
    var current = start
    var hops = 0
    while (true) {
        if (!safeCheck(current, isSafe)) return null
        val location = fetchTargetLocation(current, fetcher) ?: return null
        val next = resolveRedirectTarget(current, location) ?: return null
        hops++
        if (!isShortLinkUrl(next)) return next.takeIf { safeCheck(it, isSafe) }
        if (hops >= maxHops) return null
        current = next
    }
}

/**
 * Where [url] points: the Location of a redirect (HEAD first, then GET), or for lnkd.in the link on
 * its notice page. Null when neither gives one.
 */
private suspend fun fetchTargetLocation(
    url: String,
    fetcher: ShortLinkFetcher,
): String? {
    val readPage = shortLinkHostOf(url) == LINKEDIN_SHORT_HOST
    for (head in listOf(true, false)) {
        val hop = fetchOrNull(fetcher, url, head, readPage && !head) ?: continue
        val location = hop.location?.trim()
        if (hop.status in REDIRECT_STATUSES && !location.isNullOrEmpty()) return location
        if (!head && readPage && hop.status in 200..299) {
            return hop.page?.let(::linkedInNoticeTarget)
        }
    }
    return null
}

private suspend fun fetchOrNull(
    fetcher: ShortLinkFetcher,
    url: String,
    head: Boolean,
    readPage: Boolean,
): ShortLinkHop? =
    try {
        fetcher.fetch(url, head, readPage)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

private fun safeCheck(
    url: String,
    isSafe: (String) -> Boolean,
): Boolean = runCatching { isSafe(url) }.getOrDefault(false)

/** The outbound button on lnkd.in's "not on LinkedIn" notice page. */
private val LINKEDIN_NOTICE_LINK =
    Regex("""<a\b[^>]*\bdata-tracking-control-name\s*=\s*"external_url_click"[^>]*>""", RegexOption.IGNORE_CASE)

private val HREF_ATTRIBUTE = Regex("""\bhref\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE)

/**
 * The target of lnkd.in's notice page: the href of its one outbound link, HTML entities decoded.
 * Null unless exactly one such link is on the page, so a changed or unexpected page falls back to
 * the original link. The caller still resolves, scheme-checks and validates the result.
 */
internal fun linkedInNoticeTarget(page: String): String? {
    val links = LINKEDIN_NOTICE_LINK.findAll(page).take(2).toList()
    if (links.size != 1) return null
    val href = HREF_ATTRIBUTE.find(links.single().value)?.groupValues?.get(1) ?: return null
    return decodeHtmlAttribute(href).trim().takeIf { it.isNotEmpty() }
}

private val HTML_ENTITY = Regex("&(#[xX][0-9a-fA-F]{1,6}|#[0-9]{1,7}|amp|quot|apos|lt|gt);")

private fun decodeHtmlAttribute(value: String): String =
    HTML_ENTITY.replace(value) { match ->
        when (val name = match.groupValues[1]) {
            "amp" -> "&"
            "quot" -> "\""
            "apos" -> "'"
            "lt" -> "<"
            "gt" -> ">"
            else -> {
                val code =
                    if (name.startsWith("#x") || name.startsWith("#X")) {
                        name.substring(2).toIntOrNull(16)
                    } else {
                        name.substring(1).toIntOrNull()
                    }
                code
                    ?.takeIf { Character.isValidCodePoint(it) }
                    ?.let { String(Character.toChars(it)) }
                    ?: match.value
            }
        }
    }

/** [location] resolved against [base]; null unless the result is an absolute http(s) URL with a host. */
internal fun resolveRedirectTarget(
    base: String,
    location: String,
): String? {
    val resolved = runCatching { URI(base).resolve(location.trim()) }.getOrNull() ?: return null
    val scheme = resolved.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return null
    if (resolved.host.isNullOrBlank()) return null
    return resolved.toString()
}

/**
 * The link to open for a tapped [url]: for a shortener, its real target (tracking removed when
 * [clean] is set); for anything else, or when the lookup fails, [url] unchanged so the normal
 * opener cleans it as before.
 */
internal suspend fun expandOutboundLink(
    url: String,
    clean: Boolean,
    resolve: suspend (String) -> String? = { resolveShortLink(it) },
): String {
    val trimmed = url.trim()
    if (!isShortLinkUrl(trimmed)) return url
    val expanded =
        try {
            resolve(trimmed)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } ?: return url
    return if (clean) cleanOutgoingLink(expanded) else expanded
}

/** The "Opening link…" note for one waiting lookup. Main thread only. */
internal interface ShortLinkFeedback {
    fun show()

    fun dismiss()
}

/**
 * The one shortener lookup waiting to open, so a tap is never silent and never opens late:
 * - a lookup still running after [feedbackDelayMs] shows its [ShortLinkFeedback]; the note is
 *   dismissed the moment the lookup ends, whatever its answer;
 * - a second tap on the link being looked up shows the note again instead of starting over;
 * - a tap on another link, or [cancel] (any other link opening), drops the waiting one, so the
 *   latest tap wins and an earlier answer is never opened;
 * - when the answer comes back while Crux is not on screen ([open]'s isForeground), nothing opens.
 * [lookup] gives the address to open; when it fails, the original link opens. Every method runs on
 * the main thread, and so must [scope].
 */
internal class PendingShortLinks(
    private val scope: CoroutineScope,
    private val feedbackDelayMs: Long = SHORT_LINK_FEEDBACK_DELAY_MS,
    private val lookup: suspend (String) -> String = { expandOutboundLink(it, clean = true) },
) {
    private var pending: Pair<String, Job>? = null
    private var shownFeedback: ShortLinkFeedback? = null

    /** The short link being looked up now, or null. */
    val pendingLink: String? get() = pending?.takeIf { it.second.isActive }?.first

    fun cancel() {
        pending?.second?.cancel()
        pending = null
        dismissFeedback()
    }

    fun open(
        shortUrl: String,
        feedback: ShortLinkFeedback,
        isForeground: () -> Boolean,
        open: (target: String) -> Unit,
    ) {
        val key = shortUrl.trim()
        pending?.let { (pendingKey, job) ->
            if (pendingKey == key && job.isActive) {
                showFeedback(feedback)
                return
            }
        }
        cancel()
        // Started lazily so the lookup is registered as pending before it can finish.
        val job =
            scope.launch(start = CoroutineStart.LAZY) {
                val note =
                    launch {
                        delay(feedbackDelayMs)
                        showFeedback(feedback)
                    }
                val target =
                    try {
                        lookup(key)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        key
                    }
                note.cancel()
                if (pending?.second !== coroutineContext[Job]) return@launch
                pending = null
                dismissFeedback()
                if (runCatching { isForeground() }.getOrDefault(false)) open(target)
            }
        pending = key to job
        job.start()
    }

    private fun showFeedback(next: ShortLinkFeedback) {
        dismissFeedback()
        runCatching { next.show() }
        shownFeedback = next
    }

    private fun dismissFeedback() {
        shownFeedback?.let { runCatching { it.dismiss() } }
        shownFeedback = null
    }
}
