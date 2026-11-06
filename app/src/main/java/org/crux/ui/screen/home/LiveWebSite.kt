package org.crux.ui.screen.home

// Hosts each site needs for sign-in and for its pages to work. Anything else opens outside the WebView.
private val X_ALLOWED_HOSTS =
    setOf(
        "x.com",
        "twitter.com",
        "twimg.com",
        "accounts.google.com",
        "appleid.apple.com",
        "google.com",
        "gstatic.com",
        "recaptcha.net",
        "arkoselabs.com",
        "funcaptcha.com",
    )

private val LINKEDIN_ALLOWED_HOSTS =
    setOf(
        "linkedin.com",
        "licdn.com",
        "accounts.google.com",
        "appleid.apple.com",
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
        "events.reddit.com",
        "events.redditmedia.com",
        "rlcdn.com",
        "google.co.in",
    )

private val X_TRACKER_HOSTS =
    setOf(
        "analytics.twitter.com",
        "analytics.x.com",
        "telemetry.twitter.com",
        "ads-twitter.com",
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
        tabIdSuffix = "@linkedin.com",
        allowedHosts = LINKEDIN_ALLOWED_HOSTS,
        trackerHosts = LINKEDIN_TRACKER_HOSTS,
        defaultProtection = ProtectionLevel.Standard,
        cookieDomains = setOf("linkedin.com"),
        sessionCookies = setOf("li_at"),
    ),
    X(
        homeUrl = "https://x.com/home",
        tabIdSuffix = "",
        allowedHosts = X_ALLOWED_HOSTS,
        trackerHosts = X_TRACKER_HOSTS,
        defaultProtection = ProtectionLevel.Standard,
        cookieDomains = setOf("x.com", "twitter.com"),
        sessionCookies = setOf("auth_token", "twid", "ct0"),
    ),
    Reddit(
        // Newest first, not Reddit's ranked "Best".
        homeUrl = "https://www.reddit.com/new/",
        tabIdSuffix = "",
        allowedHosts = setOf("reddit.com", "redd.it"),
        trackerHosts = REDDIT_TRACKER_HOSTS,
        // Used signed out, so refusing trackers cannot look like a modified client to an account.
        defaultProtection = ProtectionLevel.Strict,
        cookieDomains = setOf("reddit.com", "redd.it"),
        sessionCookies = setOf("reddit_session", "token_v2"),
        pullToRefresh = true,
    ),
    ;

    fun allows(host: String?): Boolean = hostMatchesAny(host, allowedHosts)

    /** True for the accounts whose Strict mode can look like a modified client. */
    val isSignedInAccount: Boolean
        get() = defaultProtection == ProtectionLevel.Standard

    companion object {
        fun fromName(name: String): LiveWebSite? = entries.firstOrNull { it.name == name }
    }
}
