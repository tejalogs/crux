package org.crux.ui.screen.home

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.provider.Browser
import android.webkit.WebView
import android.widget.Toast
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.crux.R
import org.crux.data.network.PublicUrlValidator
import org.crux.ui.common.InternalDeeplinkToken
import org.crux.ui.route.DeeplinkRoute
import org.crux.ui.route.Route
import org.crux.ui.route.toUri
import java.net.URI

private val NON_READER_DOMAINS =
    setOf(
        // Link-in-bio aggregators & creator hubs (e.g. Instagram/TikTok bio links)
        "linktr.ee",
        "beacons.ai",
        "bento.me",
        "bio.link",
        "campsite.bio",
        "lnk.bio",
        "stan.store",
        "carrd.co",
        "taplink.cc",
        "solo.to",
        "snipfeed.co",
        "allmylinks.com",
        "contactinbio.com",
        "linkin.bio",
        "shor.by",
        "pillar.io",
        "link.me",
        "hoo.be",
        "komi.io",
        "withkoji.com",
        "direct.me",
        "link.space",

        // Social networks, microblogs & messaging
        "instagram.com",
        "facebook.com",
        "fb.com",
        "fb.me",
        "twitter.com",
        "x.com",
        "threads.net",
        "tiktok.com",
        "pinterest.com",
        "snapchat.com",
        "reddit.com",
        "redd.it",
        "linkedin.com",
        "twitch.tv",
        "discord.com",
        "discord.gg",
        "t.me",
        "telegram.org",
        "telegram.me",
        "whatsapp.com",
        "wa.me",
        "messenger.com",

        // Media streaming & podcasts
        "youtube.com",
        "youtu.be",
        "spotify.com",
        "music.apple.com",
        "podcasts.apple.com",
        "soundcloud.com",
        "bandcamp.com",
        "netflix.com",
        "vimeo.com",
        "deezer.com",
        "tidal.com",
        "mixcloud.com",

        // App stores & packages
        "play.google.com",
        "apps.apple.com",
        "testflight.apple.com",
        "apkpure.com",
        "apkmirror.com",
        "f-droid.org",

        // E-commerce, shopping & creator storefronts
        "amazon.com",
        "amzn.to",
        "ebay.com",
        "etsy.com",
        "shopify.com",
        "myshopify.com",
        "walmart.com",
        "aliexpress.com",
        "target.com",
        "shein.com",
        "temu.com",
        "bestbuy.com",
        "apple.com",
        "store.steampowered.com",
        "patreon.com",
        "buymeacoffee.com",
        "ko-fi.com",
        "gofundme.com",
        "kickstarter.com",
        "indiegogo.com",
        "gumroad.com",

        // Developer tools, productivity & cloud
        "github.com",
        "gitlab.com",
        "bitbucket.org",
        "stackoverflow.com",
        "notion.so",
        "notion.site",
        "figma.com",
        "canva.com",
        "zoom.us",
        "calendly.com",
        "eventbrite.com",
        "docs.google.com",
        "drive.google.com",
        "forms.gle",
        "dropbox.com",

        // Search engines & portals
        "google.com",
        "bing.com",
        "duckduckgo.com",
        "yahoo.com",
        "baidu.com",
        "yandex.com",
        "maps.google.com",

        // Auth & Identity
        "accounts.google.com",
        "appleid.apple.com",

        // Shorteners & redirectors
        "bit.ly",
        "tinyurl.com",
        "ow.ly",
        "is.gd",
        "buff.ly",
        "goo.gl",
        "qr.ae",
        "rb.gy",
        "shorturl.at",
        "l.instagram.com",
        // t.co, lnkd.in and the other SHORT_LINK_HOSTS are refused in isReaderEligibleUrl itself:
        // openLinkOutsideWebView expands them first when link cleaning is on, so the real article
        // can still open in the reader, but an unexpanded one (cleaning off, lookup failed) never does.
    )

private val NON_ARTICLE_SEGMENTS =
    setOf(
        "",
        "about",
        "contact",
        "pricing",
        "terms",
        "privacy",
        "legal",
        "login",
        "register",
        "signup",
        "signin",
        "checkout",
        "cart",
        "shop",
        "store",
        "products",
        "product",
        "item",
        "categories",
        "category",
        "faq",
        "help",
        "support",
        "search",
        "explore",
        "feed",
        "home",
        "index.html",
        "u",
        "user",
        "channel",
        "profile",
        "settings",
        "download",
        "app",
    )

private val BINARY_PATH_SUFFIX =
    Regex("\\.(?:pdf|apk|zip|tar|gz|mp4|webm|mp3|wav|jpg|jpeg|png|gif|webp|svg|ico)$", RegexOption.IGNORE_CASE)

private val ARTICLE_PATH_PREFIX =
    Regex("^/(?:article|articles|story|stories|post|posts|blog|blogs|news|entry|entries|p)/", RegexOption.IGNORE_CASE)

private val DATE_PATH_PATTERN =
    Regex("/\\d{4}/\\d{1,2}/")

private val ARTICLE_EXTENSION =
    Regex("\\.(?:html?|php)$", RegexOption.IGNORE_CASE)

private fun isNonArticleHost(host: String): Boolean {
    val h = host.lowercase()
    if (NON_READER_DOMAINS.any { h == it || h.endsWith(".$it") }) return true
    if (h.startsWith("login.") || h.startsWith("auth.") || h.startsWith("sso.")) return true
    if (Regex("^amazon\\.[a-z.]{2,6}$").matches(h)) return true
    if (Regex("^(?:www\\.)?google\\.[a-z.]{2,6}$").matches(h)) return true
    return false
}

internal fun isReaderEligibleUrl(url: String): Boolean {
    val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase() ?: return false
    if (scheme != "http" && scheme != "https") return false
    val host = uri.host?.lowercase() ?: return false
    // A shortener's own page is a redirect or lnkd.in's "not on LinkedIn" notice, never an article,
    // even when its code looks like a slug (https://lnkd.in/g-Ab12Cd).
    if (isShortLinkHost(host)) return false
    if (isNonArticleHost(host)) return false

    val path = uri.path.orEmpty()
    if (path.isEmpty() || path == "/") return false
    if (BINARY_PATH_SUFFIX.containsMatchIn(path)) return false

    val segments = path.split("/").filter { it.isNotEmpty() }
    if (segments.isEmpty()) return false
    if (segments.size == 1 && segments[0].lowercase() in NON_ARTICLE_SEGMENTS) return false

    // Single-segment path that looks like a username, handle, or standalone page
    if (segments.size == 1) {
        val single = segments[0]
        val hasHyphen = single.contains("-") || single.contains("_")
        val isArticleExt = ARTICLE_EXTENSION.containsMatchIn(single)
        if (!hasHyphen && !isArticleExt) return false
    }

    val hasPrefix = ARTICLE_PATH_PREFIX.containsMatchIn(path)
    val hasDate = DATE_PATH_PATTERN.containsMatchIn(path)
    val hasExt = ARTICLE_EXTENSION.containsMatchIn(path)
    val leafSlug = segments.lastOrNull()?.contains("-") == true

    return hasPrefix || hasDate || hasExt || (segments.size >= 2 && leafSlug)
}

/**
 * Opens a link that a live web page does not keep for itself. A link to another social site goes to
 * Crux's own screen for it, through the app's crux:// link so the home navigation shows it. When
 * [openInReader] is true, eligible HTTP(S) article links open in Crux's distraction-free reader;
 * otherwise links go to the phone's default browser with tracking removed when [clean] is set.
 * With [clean] set, a shortener link ([isShortLinkUrl]) is expanded before it opens: from
 * [anchorExpansion] when the page already printed the full address for it (X's t.co links, see
 * [expandedShortLinkFromAnchor]), so nothing is fetched; otherwise off the main thread with a
 * cookie-less lookup ([shortLinkLookups]), then cleaned and opened; if that fails, the original
 * link opens as before. Opening any link drops a shortener lookup still waiting from an earlier
 * tap, so the latest tap wins.
 */
internal fun openLinkOutsideWebView(
    context: Context,
    url: String,
    clean: Boolean,
    openInReader: Boolean = LiveWebPrivacyStore.get(context).settings.value.openLinksInReader,
    anchorExpansion: String? = null,
    shortLinkLookups: PendingShortLinks = appShortLinkLookups,
) {
    when (val plan = planOutboundOpen(url, clean, anchorExpansion)) {
        is OutboundOpen.Now -> {
            shortLinkLookups.cancel()
            openLinkNow(context, plan.url, clean, openInReader, countedFrom = plan.countedFrom)
        }

        is OutboundOpen.AfterLookup -> {
            // The application context, so a lookup never keeps the tapped page's activity alive.
            val appContext = context.applicationContext ?: context
            shortLinkLookups.open(
                shortUrl = plan.shortUrl,
                feedback = ToastShortLinkFeedback(appContext),
                isForeground = { isAppInForeground(appContext) },
            ) { target ->
                openLinkNow(appContext, target, clean = true, openInReader = openInReader, countedFrom = plan.shortUrl)
            }
        }
    }
}

/** What a tapped outbound link needs before it can open. */
internal sealed interface OutboundOpen {
    /** Open [url] now; [countedFrom] is the link actually tapped, for the privacy counters. */
    data class Now(
        val url: String,
        val countedFrom: String,
    ) : OutboundOpen

    /** Ask the shortener where [shortUrl] goes first. */
    data class AfterLookup(
        val shortUrl: String,
    ) : OutboundOpen
}

/**
 * The plan for a tapped [url]: a shortener link with [clean] on needs a lookup unless
 * [anchorExpansion] already gives its full address ([expandedShortLinkFromAnchor]), which then
 * opens cleaned, at once; every other link opens as it is.
 */
internal fun planOutboundOpen(
    url: String,
    clean: Boolean,
    anchorExpansion: String?,
): OutboundOpen {
    if (!clean || !isShortLinkUrl(url)) return OutboundOpen.Now(url, url)
    val expanded = expandedShortLinkFromAnchor(url, anchorExpansion) ?: return OutboundOpen.AfterLookup(url.trim())
    return OutboundOpen.Now(cleanOutgoingLink(expanded), url)
}

/** Hosts whose links carry their full target in the anchor itself. Only X's t.co does this. */
private val ANCHOR_EXPANDING_SHORT_HOSTS = setOf("t.co")

/** The ellipsis X appends to a long link's text; its hidden spans still carry the whole address. */
private val TRAILING_ELLIPSIS = Regex("(?:\u2026|\\.\\.\\.)+$")

/**
 * The full address the page already shows for a tapped t.co link, so it can open with no lookup.
 * Each candidate is a string X puts in or on the anchor: its text content (the hidden `https://`
 * span, the visible part and the hidden rest, then an ellipsis), its title, or an old-style
 * `data-expanded-url`. A candidate is used only when, minus a trailing ellipsis, it is one whole
 * absolute http(s) URL with a host and no spaces, its host is not a private, loopback or local
 * address as written ([PublicUrlValidator.isNonPublicHostLiteral], no DNS lookup), and it is not
 * itself a shortener link. A truncated or domain-only text ("example.com/news/sto…",
 * "example.com") has no scheme and is refused, so card links and anything uncertain fall back to
 * the lookup. Null for any link not on t.co, since other shorteners' anchors show the short
 * address itself. Callers pass candidates only from X's own pages ([anchorExpansionTrustedOn]);
 * the reader still runs the full DNS check before it fetches anything.
 */
internal fun expandedShortLinkFromAnchor(
    shortUrl: String,
    vararg candidates: String?,
): String? {
    val host =
        runCatching { URI(shortUrl.trim()).host }
            .getOrNull()
            ?.lowercase()
            ?.trimEnd('.')
            ?.removePrefix("www.")
    if (host !in ANCHOR_EXPANDING_SHORT_HOSTS || !isShortLinkUrl(shortUrl)) return null
    return candidates.firstNotNullOfOrNull(::fullUrlFromAnchorText)
}

private fun fullUrlFromAnchorText(raw: String?): String? {
    val text = raw?.trim()?.replace(TRAILING_ELLIPSIS, "")?.trim() ?: return null
    if (text.isEmpty() || text.any { it.isWhitespace() }) return null
    val uri = runCatching { URI(text) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return null
    val host = uri.host
    if (host.isNullOrBlank() || !uri.isAbsolute || uri.isOpaque) return null
    if (uri.rawUserInfo != null) return null
    if (runCatching { PublicUrlValidator.isNonPublicHostLiteral(host) }.getOrDefault(true)) return null
    if (isShortLinkUrl(text)) return null
    return text
}

/**
 * True when the page at [pageUrl] is X's own, the only site that prints a t.co link's full address
 * into its anchor. On any other page (Reddit, LinkedIn, the Hardened Browser) the page author
 * writes the anchor text, so `<a href="https://t.co/abc">https://other.example/</a>` must still be
 * looked up rather than trusted.
 */
internal fun anchorExpansionTrustedOn(pageUrl: String?): Boolean {
    val uri = runCatching { URI(pageUrl.orEmpty().trim()) }.getOrNull() ?: return false
    if (uri.scheme?.lowercase() != "https") return false
    val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
    // X's tab also allows shared sign-in pages (Google, Apple); only X's own domains count here.
    val onXDomain = ANCHOR_TRUSTED_PAGE_DOMAINS.any { host == it || host.endsWith(".$it") }
    return onXDomain && LiveWebSite.X.allows(host)
}

/** The only pages whose t.co anchors carry the link's real address: X's own. */
private val ANCHOR_TRUSTED_PAGE_DOMAINS = setOf("x.com", "twitter.com")

/**
 * [openLinkOutsideWebView] for a link tapped in this WebView. On X's own pages
 * ([anchorExpansionTrustedOn]) it first asks WebView for the tapped anchor's text (no page script
 * runs), and passes it as the anchor expansion when it belongs to this very link, so a t.co link
 * whose full address X already printed opens at once. WebView's answer can describe an earlier
 * tap, hence the URL check; when no answer comes within [ANCHOR_TEXT_WAIT_MS], the link opens
 * through the usual lookup. On every other page the link opens through the usual path directly.
 */
internal fun WebView.openTappedLinkOutsideWebView(
    url: String,
    clean: Boolean,
    openInReader: Boolean,
) {
    val pageContext = context
    if (!clean || !isShortLinkUrl(url) || !anchorExpansionTrustedOn(this.url)) {
        openLinkOutsideWebView(pageContext, url, clean, openInReader)
        return
    }
    var opened = false
    fun open(anchorText: String?) {
        if (opened) return
        opened = true
        openLinkOutsideWebView(pageContext, url, clean, openInReader, anchorExpansion = anchorText)
    }
    val mainLooper = Looper.getMainLooper()
    val replies =
        Handler(mainLooper) { result ->
            // WebView fills in the tapped anchor's href ("url") and text content ("title").
            val data = result.data
            open(anchorTextForTappedLink(url, data?.getString("url"), data?.getString("title")))
            true
        }
    val asked = runCatching { requestFocusNodeHref(Message.obtain(replies)) }.isSuccess
    if (!asked) {
        open(null)
        return
    }
    Handler(mainLooper).postDelayed({ open(null) }, ANCHOR_TEXT_WAIT_MS)
}

/**
 * The anchor text WebView reported, but only when its href is the link being opened: WebView keeps
 * the last hit-test result, which may belong to an earlier tap on another link.
 */
internal fun anchorTextForTappedLink(
    openingUrl: String,
    reportedHref: String?,
    reportedText: String?,
): String? = reportedText?.takeIf { reportedHref != null && reportedHref.trim() == openingUrl.trim() }

/** How long a tap waits for WebView's anchor text before falling back to the lookup. */
private const val ANCHOR_TEXT_WAIT_MS = 150L

/**
 * The app's one queue of shortener lookups, on the main thread so a tap never waits on the network
 * there. [resolveShortLink] returns within [SHORT_LINK_TIMEOUT_MS] even when a DNS lookup hangs,
 * so a link never opens long after the tap.
 */
private val appShortLinkLookups by lazy {
    PendingShortLinks(CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
}

/** "Opening link…" as a short toast, cancelled the moment the link opens. Main thread only. */
private class ToastShortLinkFeedback(
    private val context: Context,
) : ShortLinkFeedback {
    private var toast: Toast? = null

    override fun show() {
        dismiss()
        toast = Toast.makeText(context, R.string.web_opening_link, Toast.LENGTH_SHORT).also { it.show() }
    }

    override fun dismiss() {
        toast?.cancel()
        toast = null
    }
}

/** True while one of Crux's screens is visible; a link that resolves after the user left is dropped. */
private fun isAppInForeground(context: Context): Boolean {
    val state = ActivityManager.RunningAppProcessInfo()
    return runCatching { ActivityManager.getMyMemoryState(state) }.isSuccess &&
        isForegroundImportance(state.importance)
}

/** Foreground or visible: Crux is on screen, so a link opening now still answers the user's tap. */
internal fun isForegroundImportance(importance: Int): Boolean =
    importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE

/**
 * Opens [url] now. [countedFrom] is the link the user actually tapped, so an expanded shortener
 * counts as one removed tracking redirect in the privacy counters.
 */
private fun openLinkNow(
    context: Context,
    url: String,
    clean: Boolean,
    openInReader: Boolean,
    countedFrom: String = url,
) {
    val intent =
        when (val target = resolveLinkTarget(url, clean)) {
            is LinkTarget.LiveWebScreen -> {
                val link = target.route.initialLink() ?: url
                InternalDeeplinkToken.stamp(
                    Intent(Intent.ACTION_VIEW, DeeplinkRoute.OpenLinkDirectly(link).toUri().toUri())
                        .setPackage(context.packageName),
                )
            }

            is LinkTarget.SystemBrowser -> {
                if (openInReader && isReaderEligibleUrl(target.url)) {
                    InternalDeeplinkToken.stamp(
                        Intent(
                            Intent.ACTION_VIEW,
                            DeeplinkRoute.Rss.Detail(url = target.url).toUri().toUri(),
                        ).setPackage(context.packageName),
                    )
                } else {
                    Intent(Intent.ACTION_VIEW, target.url.toUri())
                        .putExtra(Browser.EXTRA_APPLICATION_ID, context.packageName)
                }
            }

            is LinkTarget.InternalDeepLink, LinkTarget.Invalid -> {
                return
            }
        }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
        if (clean) LiveWebPrivacyCounters.get(context).recordLinkCleaned(countedFrom.trim(), cleanOutgoingLink(url.trim()))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.unable_to_open_link, Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, R.string.unable_to_open_link, Toast.LENGTH_SHORT).show()
    }
}

private fun Route.initialLink(): String? =
    when (this) {
        is Route.LiveWeb -> initialUrl
        is Route.InstagramWeb -> initialUrl
        is Route.LinkedInWeb -> initialUrl
        else -> null
    }

/** The explicit "open in browser" buttons: the page's own URL goes to the default browser, never back into Crux. */
internal fun openInSystemBrowser(
    context: Context,
    url: String,
) {
    val link = cleanOutgoingLink(url.trim())
    val parsed = runCatching { URI(link) }.getOrNull()
    val scheme = parsed?.scheme?.lowercase()
    if ((scheme != "https" && scheme != "http") || parsed.host.isNullOrBlank()) {
        Toast.makeText(context, R.string.unable_to_open_link, Toast.LENGTH_SHORT).show()
        return
    }
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, link.toUri())
                .putExtra(Browser.EXTRA_APPLICATION_ID, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        LiveWebPrivacyCounters.get(context).recordLinkCleaned(url.trim(), link)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.unable_to_open_link, Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, R.string.unable_to_open_link, Toast.LENGTH_SHORT).show()
    }
}
