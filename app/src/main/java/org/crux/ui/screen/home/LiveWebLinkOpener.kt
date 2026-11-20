package org.crux.ui.screen.home

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Browser
import android.widget.Toast
import androidx.core.net.toUri
import org.crux.R
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
        "t.co",
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
        "lnkd.in",
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
 */
internal fun openLinkOutsideWebView(
    context: Context,
    url: String,
    clean: Boolean,
    openInReader: Boolean = LiveWebPrivacyStore.get(context).settings.value.openLinksInReader,
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
        if (clean) LiveWebPrivacyCounters.get(context).recordLinkCleaned(url.trim(), cleanOutgoingLink(url.trim()))
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
