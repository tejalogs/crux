package org.crux.ui.screen.home

import org.crux.ui.route.DeeplinkRoute
import org.crux.ui.route.Route
import java.net.URI

private val INSTAGRAM_LINK_HOSTS = setOf("instagram.com", "www.instagram.com", "m.instagram.com")
private val LINKEDIN_LINK_HOSTS = setOf("linkedin.com", "www.linkedin.com", "m.linkedin.com")
private val X_LINK_HOSTS = setOf("x.com", "www.x.com", "mobile.x.com", "twitter.com", "www.twitter.com", "mobile.twitter.com")
private val REDDIT_LINK_HOSTS =
    setOf("reddit.com", "www.reddit.com", "old.reddit.com", "new.reddit.com", "m.reddit.com", "np.reddit.com", "redd.it")

/**
 * Where a tapped link goes. The social sites open in Crux's own live web screen so the signed-in
 * session is used; everything else goes to the phone's default browser app.
 */
internal sealed interface LinkTarget {
    /** One of Instagram, X, LinkedIn or Reddit: opened in Crux's live web screen for that site. */
    data class LiveWebScreen(
        val route: Route,
    ) : LinkTarget

    /** A crux:// app route, handled by the app itself. */
    data class InternalDeepLink(
        val uri: String,
    ) : LinkTarget

    /** Handed to Intent.ACTION_VIEW, tracking parameters already removed. */
    data class SystemBrowser(
        val url: String,
    ) : LinkTarget

    /** Empty, relative or host-less: nothing sensible can open it. */
    data object Invalid : LinkTarget
}

private val WEB_SCHEMES = setOf("http", "https")

// Feeds often carry links with unescaped spaces or pipes that java.net.URI rejects but browsers accept.
private val LENIENT_WEB_LINK = Regex("^https?://[^/?#\\s]+.*", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

/**
 * Instagram, LinkedIn, X and Reddit have no native screens in Crux, so their links open on the real
 * site in the live web screen. Anything else returns null and is opened the normal way.
 */
internal fun liveWebLinkRoute(url: String): Route? {
    val link = url.trim()
    val uri = runCatching { URI(link) }.getOrNull() ?: return null
    if (uri.scheme?.lowercase() !in WEB_SCHEMES) return null
    return when (uri.host?.lowercase()) {
        in INSTAGRAM_LINK_HOSTS -> Route.InstagramWeb(initialUrl = link)
        in LINKEDIN_LINK_HOSTS -> Route.LinkedInWeb(initialUrl = link)
        in X_LINK_HOSTS -> Route.LiveWeb(site = LiveWebSite.X.name, initialUrl = link)
        in REDDIT_LINK_HOSTS -> Route.LiveWeb(site = LiveWebSite.Reddit.name, initialUrl = link)
        else -> null
    }
}

/**
 * The single place that decides how a link opens. Click-tracking redirects and tracking
 * parameters are removed first when [clean] is set, so a redirect that lands on a social site is
 * still recognised and nothing tracked reaches the browser.
 */
internal fun resolveLinkTarget(
    raw: String,
    clean: Boolean = true,
): LinkTarget {
    val link = raw.trim()
    if (link.isEmpty()) return LinkTarget.Invalid
    if (DeeplinkRoute.isDeeplink(link)) return LinkTarget.InternalDeepLink(link)
    val uri =
        runCatching { URI(link) }.getOrNull()
            ?: return if (LENIENT_WEB_LINK.matches(link)) LinkTarget.SystemBrowser(link) else LinkTarget.Invalid
    val scheme = uri.scheme?.lowercase()
    if (scheme.isNullOrEmpty()) return LinkTarget.Invalid
    if (scheme !in WEB_SCHEMES) return LinkTarget.SystemBrowser(link)
    if (uri.host.isNullOrEmpty()) return LinkTarget.Invalid
    val target = if (clean) cleanOutgoingLink(link) else link
    liveWebLinkRoute(target)?.let { return LinkTarget.LiveWebScreen(it) }
    return LinkTarget.SystemBrowser(target)
}
