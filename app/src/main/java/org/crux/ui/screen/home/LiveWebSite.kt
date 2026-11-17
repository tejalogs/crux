package org.crux.ui.screen.home

import org.crux.ui.screen.crux.hasSession

// Hosts each site needs for sign-in and for its pages to work. Anything else opens outside the WebView.
private val X_ALLOWED_HOSTS =
    setOf(
        "x.com",
        "twitter.com",
        "twimg.com",
        "accounts.google.com",
        "appleid.apple.com",
        "gstatic.com",
        "recaptcha.net",
        "arkoselabs.com",
        "funcaptcha.com",
    )

private val X_EXCLUDED_SUBDOMAINS =
    setOf(
        "developer.x.com",
        "developer.twitter.com",
        "business.x.com",
        "business.twitter.com",
        "ads.x.com",
        "ads.twitter.com",
        "help.x.com",
        "help.twitter.com",
        "about.x.com",
        "about.twitter.com",
        "careers.x.com",
        "careers.twitter.com",
        "legal.x.com",
        "legal.twitter.com",
        "blog.x.com",
        "blog.twitter.com",
        "status.x.com",
        "status.twitter.com",
    )

private val LINKEDIN_ALLOWED_HOSTS =
    setOf(
        "linkedin.com",
        "licdn.com",
        "accounts.google.com",
        "appleid.apple.com",
        "gstatic.com",
        "recaptcha.net",
        "arkoselabs.com",
        "funcaptcha.com",
        "arkose.com",
    )

private val INSTAGRAM_ALLOWED_HOSTS =
    setOf(
        "instagram.com",
        "facebook.com",
        "fbcdn.net",
        "cdninstagram.com",
    )

// Each site's own tracker hosts, read from what the pages really load. They are only refused when
// the site is set to Strict. Reddit's own CDNs and www.google.com stay allowed: posts and
// reCAPTCHA break without them.
private val REDDIT_TRACKER_HOSTS =
    setOf(
        "alb.reddit.com",
        "pi.reddit.com",
        "w3-reporting.reddit.com",
        "w3-reporting-nel.reddit.com",
        "events.reddit.com",
        "events.redditmedia.com",
        "error-tracking.reddit.com",
        "rlcdn.com",
        "google.co.in",
    )

private val X_TRACKER_HOSTS =
    setOf(
        "analytics.twitter.com",
        "analytics.x.com",
        "telemetry.twitter.com",
        "ads-twitter.com",
        "ads-api.twitter.com",
        "ads-api.x.com",
        "scribe.twitter.com",
        "p.twitter.com",
        "tracking.x.com",
        "caps.twitter.com",
        "monorail-edge.twitter.com",
    )

private val LINKEDIN_TRACKER_HOSTS =
    setOf(
        "px.ads.linkedin.com",
        "analytics.pointdrive.linkedin.com",
        "snap.licdn.com",
        "tracking.linkedin.com",
        "sstats.linkedin.com",
    )

private val INSTAGRAM_TRACKER_HOSTS =
    setOf(
        "connect.facebook.net",
        "logging.facebook.com",
        "pixel.facebook.com",
    )

/**
 * A real site shown as a home tab, in its own isolated WebView profile. This holds only the web
 * facts about a site. Names, brand colours and what can be posted live in `CruxPlatform`, which is
 * the one list the share and compose screens are built from.
 */
internal enum class LiveWebSite(
    val homeUrl: String,
    /** Direct sign-in flow URL so signed-out users skip splash pages and redirect chains. */
    val loginUrl: String,
    /** Direct link inside this site's own settings for AI model training / privacy opt-outs. */
    val aiPrivacyUrl: String,
    val tabIdSuffix: String,
    val allowedHosts: Set<String>,
    val trackerHosts: Set<String>,
    /** Instagram, X and LinkedIn are signed-in accounts, so they never block requests unless asked. */
    val defaultProtection: ProtectionLevel,
    /** Domains whose cookies belong to this site, used to sign out when profiles are unavailable. */
    val cookieDomains: Set<String>,
    /** Cookies that only exist once someone has signed in. */
    val sessionCookies: Set<String>,
    val pullToRefresh: Boolean = false,
    val smartReplies: Boolean = true,
) {
    Instagram(
        homeUrl = "https://www.instagram.com/",
        loginUrl = "https://www.instagram.com/accounts/login/",
        aiPrivacyUrl = "https://www.instagram.com/accounts/privacy_and_security/",
        tabIdSuffix = "@instagram.com",
        allowedHosts = INSTAGRAM_ALLOWED_HOSTS,
        trackerHosts = INSTAGRAM_TRACKER_HOSTS,
        defaultProtection = ProtectionLevel.Standard,
        cookieDomains = setOf("instagram.com", "facebook.com"),
        sessionCookies = setOf("sessionid", "ds_user_id"),
        smartReplies = false,
        pullToRefresh = true,
    ),
    LinkedIn(
        homeUrl = "https://www.linkedin.com/feed/",
        loginUrl = "https://www.linkedin.com/login",
        aiPrivacyUrl = "https://www.linkedin.com/mypreferences/d/settings/data-for-ai-improvement",
        tabIdSuffix = "@linkedin.com",
        allowedHosts = LINKEDIN_ALLOWED_HOSTS,
        trackerHosts = LINKEDIN_TRACKER_HOSTS,
        defaultProtection = ProtectionLevel.Standard,
        cookieDomains = setOf("linkedin.com"),
        sessionCookies = setOf("li_at"),
        pullToRefresh = true,
    ),
    X(
        homeUrl = "https://x.com/home",
        loginUrl = "https://x.com/i/flow/login",
        aiPrivacyUrl = "https://x.com/settings/grok_settings",
        tabIdSuffix = "",
        allowedHosts = X_ALLOWED_HOSTS,
        trackerHosts = X_TRACKER_HOSTS,
        defaultProtection = ProtectionLevel.Standard,
        cookieDomains = setOf("x.com", "twitter.com"),
        // Never include ct0 here: X sets ct0 as a guest CSRF cookie on every signed-out visit.
        sessionCookies = setOf("auth_token", "twid"),
        pullToRefresh = true,
    ),
    Reddit(
        // Newest first, not Reddit's ranked "Best".
        homeUrl = "https://www.reddit.com/new/",
        loginUrl = "https://www.reddit.com/login/",
        aiPrivacyUrl = "https://www.reddit.com/settings/privacy",
        tabIdSuffix = "",
        allowedHosts = setOf("reddit.com", "redd.it", "redditmedia.com", "redditstatic.com"),
        trackerHosts = REDDIT_TRACKER_HOSTS,
        // Used signed out, so refusing trackers cannot look like a modified client to an account.
        defaultProtection = ProtectionLevel.Strict,
        cookieDomains = setOf("reddit.com", "redd.it"),
        // Never include token_v2 here: Reddit issues an anonymous token_v2 cookie to signed-out visitors.
        sessionCookies = setOf("reddit_session"),
        pullToRefresh = true,
    ),
    ;

    fun allows(host: String?): Boolean {
        val normalized = host?.lowercase()?.trimEnd('.') ?: return false
        if (this == X && normalized in X_EXCLUDED_SUBDOMAINS) return false
        return hostMatchesAny(normalized, allowedHosts)
    }

    /**
     * Initial URL to load when opening a tab or standalone screen for this site.
     * Signed-out accounts (X, Instagram, LinkedIn) open their direct login flow so they never stall
     * on splash screens or broken signed-out root pages; Reddit opens `/new/` because it works signed out.
     */
    fun startUrlForSession(): String =
        if (isSignedInAccount && !hasSession()) {
            loginUrl
        } else {
            homeUrl
        }

    /**
     * True when [url] is the bare signed-out X/Twitter splash (`https://x.com/` or `https://x.com/?...`)
     * that fails to render login controls in Android WebView.
     */
    fun isSignedOutXRoot(url: String?): Boolean {
        if (this != X || hasSession() || url.isNullOrBlank()) return false
        val uri = runCatching { java.net.URI(url.trim()) }.getOrNull() ?: return false
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
        if (
            host != "x.com" &&
            host != "www.x.com" &&
            host != "mobile.x.com" &&
            host != "twitter.com" &&
            host != "www.twitter.com" &&
            host != "mobile.twitter.com"
        ) {
            return false
        }
        val path = uri.path?.trimEnd('/').orEmpty()
        return path.isEmpty()
    }

    /** True for the accounts whose Strict mode can look like a modified client. */
    val isSignedInAccount: Boolean
        get() = defaultProtection == ProtectionLevel.Standard

    companion object {
        fun fromName(name: String): LiveWebSite? = entries.firstOrNull { it.name == name }
    }
}
