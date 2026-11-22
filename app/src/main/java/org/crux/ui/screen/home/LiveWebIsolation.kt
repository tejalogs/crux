package org.crux.ui.screen.home

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.webkit.ProfileStore
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.net.URI
import java.net.URLDecoder
import java.security.SecureRandom
import java.util.WeakHashMap
import org.crux.ui.screen.crux.hasSession

private const val ISOLATION_PREFS = "live_web_isolation"
private const val KEY_SHARED_DATA_CLEARED = "shared_data_cleared"

private const val FREEZE_MEDIA_SCRIPT =
    """
    (function() {
        try {
            document.querySelectorAll('video, audio').forEach(function(m) {
                m.pause();
                // Remember which elements we muted so resume only undoes our change,
                // not a mute the user or the site chose.
                if (!m.muted) { m.muted = true; m.setAttribute('data-crux-muted', '1'); }
            });
        } catch(e) {}
    })();
    """

private const val RESTORE_MEDIA_SCRIPT =
    """
    (function() {
        try {
            document.querySelectorAll('[data-crux-muted]').forEach(function(m) {
                m.muted = false;
                m.removeAttribute('data-crux-muted');
            });
        } catch(e) {}
    })();
    """

/**
 * Freezes background execution for a WebView session:
 * 1. Calls onPause() to halt internal WebView timer loops and animations.
 * 2. Pauses and mutes all running HTML5 video/audio elements to stop decoding pipelines and sound.
 */
internal fun WebView.suspendWebSession() {
    runCatching {
        // Pause & mute HTML5 media BEFORE onPause() so JS executes immediately rather than deferring until resume.
        evaluateJavascript(FREEZE_MEDIA_SCRIPT, null)
        onPause()
        // Each site keeps its cookies in its own profile, so flush that store, not the shared one.
        (if (isSupported) WebViewCompat.getProfile(this).cookieManager else CookieManager.getInstance()).flush()
    }
}

/**
 * Resumes execution for a WebView session when brought back to foreground.
 */
internal fun WebView.resumeWebSession() {
    runCatching {
        // A sign-out sent this page to the sign-in page while it was hidden. Once that page has
        // committed, dropping history again leaves nothing of the account to go Back to.
        clearHistoryIfStartPageCommitted()
        onResume()
        resumeTimers()
        postInvalidateOnAnimation()
        evaluateJavascript(RESTORE_MEDIA_SCRIPT, null)
    }
}

/**
 * Automatically freezes background WebView renderers and media when:
 * 1. The hosting composable tab is switched away (isVisible = false).
 * 2. The host Activity or app is paused or stopped (screen off, user switching apps).
 *
 * Resumes execution only when both the app is in the foreground and this tab is actively visible.
 */
@Composable
internal fun WebSessionLifecycleSuspender(
    webView: WebView?,
    isVisible: Boolean = true,
) {
    if (webView == null) return
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, webView, isVisible) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        if (isVisible) webView.resumeWebSession() else webView.suspendWebSession()
                    }

                    Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                        webView.suspendWebSession()
                    }

                    else -> {}
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)

        if (isVisible && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            webView.resumeWebSession()
        } else {
            webView.suspendWebSession()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webView.suspendWebSession()
        }
    }
}

private val isSupported: Boolean
    get() = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

/** False on WebViews without profiles, where every site shares one cookie jar. */
internal val multiProfileSupported: Boolean
    get() = isSupported

private val LiveWebSite.profile
    get() = ProfileStore.getInstance().getOrCreateProfile(name)

/** Every site WebView made so far (weakly held), so signing out of a site can wipe its open pages. */
private val siteWebViews = WeakHashMap<WebView, LiveWebSite>()

/** Pages wiped by a sign-out whose back history is dropped again once the start page has committed. */
private val pendingHistoryClears = WeakHashMap<WebView, Unit>()

private const val HISTORY_CLEAR_POLL_MS = 250L
private const val HISTORY_CLEAR_MAX_POLLS = 40

/**
 * Drops the back history of a page wiped by a sign-out once the start page it was sent to has
 * committed. [wipeOpenPages] clears history before loading, which leaves the signed-in page as the
 * one remaining entry; when the start page commits it becomes entry 1, so clearing then removes
 * the signed-in page. Returns true when the pending clear was done (or none was pending).
 */
private fun WebView.clearHistoryIfStartPageCommitted(): Boolean {
    if (synchronized(siteWebViews) { !pendingHistoryClears.containsKey(this) }) return true
    if (copyBackForwardList().currentIndex < 1) return false
    synchronized(siteWebViews) { pendingHistoryClears.remove(this) }
    clearHistory()
    return true
}

/** Polls on the main thread until [clearHistoryIfStartPageCommitted] succeeds, for about ten seconds. */
private fun WebView.clearHistoryWhenStartPageCommits(pollsLeft: Int = HISTORY_CLEAR_MAX_POLLS) {
    postDelayed({
        val done = runCatching { clearHistoryIfStartPageCommitted() }.getOrDefault(true)
        if (!done && pollsLeft > 1) clearHistoryWhenStartPageCommits(pollsLeft - 1)
    }, HISTORY_CLEAR_POLL_MS)
}

/** Each site's cookies, storage, cache and service workers live in that site's own profile. */
internal fun WebView.useProfileOf(site: LiveWebSite) {
    synchronized(siteWebViews) { siteWebViews[this] = site }
    if (isSupported) {
        site.profile
        WebViewCompat.setProfile(this, site.name)
    }
}

internal fun LiveWebSite.cookieManager(): CookieManager = if (isSupported) profile.cookieManager else CookieManager.getInstance()

internal fun LiveWebSite.webStorage(): WebStorage = if (isSupported) profile.webStorage else WebStorage.getInstance()

/** Origins WebStorage can be asked to forget for a cookie domain. */
internal fun storageOriginsFor(domain: String): List<String> = listOf("https://$domain", "https://www.$domain")

/** The subdomains the sites use for their own pages (mobile.x.com, old.reddit.com, m.instagram.com…). */
private val SITE_SUBDOMAINS = listOf("m", "mobile", "old", "new", "sh")

/**
 * Every origin to sign out of for a cookie domain when profiles are unavailable: the domain, www,
 * and the sites' other page subdomains, whose storage and host-only cookies would survive otherwise.
 */
internal fun signOutOriginsFor(domain: String): List<String> = storageOriginsFor(domain) + SITE_SUBDOMAINS.map { "https://$it.$domain" }

/**
 * Set-Cookie strings that expire [name] on [domain], both as a host-only and a domain cookie.
 * Secure is set so `__Secure-` and `__Host-` cookies, which refuse any non-Secure overwrite, go too.
 */
internal fun expiryCookiesFor(
    domain: String,
    name: String,
): List<String> {
    val expired = "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Secure; Path=/"
    return listOf(expired, "$expired; Domain=.$domain")
}

/**
 * Identity providers the sites sign in through, with the parent domain their session cookies are
 * set on. Without profiles their cookies sit in the one shared jar.
 */
internal val IDENTITY_PROVIDER_HOSTS: Map<String, String> =
    mapOf(
        "accounts.google.com" to "google.com",
        "accounts.youtube.com" to "youtube.com",
        "appleid.apple.com" to "apple.com",
        "auth.meta.com" to "meta.com",
    )

/**
 * The identity-provider hosts whose cookies signing out of [site] should expire when every site
 * shares one jar: the providers [site] signs in through, except those another site that is still
 * signed in also uses. Expiring a Google session never signs another site out, but it would make
 * the user sign in to Google again in a site they are still using; once no site that uses the
 * provider is signed in, nothing needs it and leaving it would let the next person one-tap back
 * into the account they just signed out of.
 */
internal fun identityProviderHostsToClear(
    site: LiveWebSite,
    stillSignedIn: Collection<LiveWebSite>,
): List<String> =
    IDENTITY_PROVIDER_HOSTS.keys.filter { host ->
        site.allows(host) && stillSignedIn.none { it != site && it.allows(host) }
    }

/** Expires every cookie [origin] can see, as host-only and as domain cookies on each of [domains]. */
private fun expireVisibleCookies(
    cookies: CookieManager,
    origin: String,
    domains: List<String>,
) {
    cookieNames(cookies.getCookie("$origin/")).forEach { name ->
        domains
            .flatMap { expiryCookiesFor(it, name) }
            .distinct()
            .forEach { cookies.setCookie("$origin/", it) }
    }
}

private fun cookieNames(header: String?): List<String> =
    header
        .orEmpty()
        .split(";")
        .map { it.trim().substringBefore("=") }
        .filter { it.isNotEmpty() }

/**
 * Signs out of this site only. With profiles that empties the site's own profile. Without them
 * every site shares one jar, so wiping it would sign out the others: instead only the cookies of
 * this site's own domains (and subdomains) are expired, only its origins' storage is forgotten,
 * and the Google / Apple / Meta sign-in cookies go once no other signed-in site uses them.
 *
 * Either way every open page of the site loses its back history and HTTP cache and goes to the
 * start page, and with [context] the saved last page and scroll position are forgotten, so
 * nothing of the account stays on screen or comes back on the next launch.
 */
internal fun LiveWebSite.clearSession(context: Context? = null) {
    context?.let { LiveWebSessionState.clear(it, this) }
    runCatching {
        if (isSupported) {
            cookieManager().removeAllCookies(null)
            cookieManager().flush()
            webStorage().deleteAllData()
        } else {
            val cookies = CookieManager.getInstance()
            cookieDomains.forEach { domain ->
                signOutOriginsFor(domain).forEach { origin ->
                    expireVisibleCookies(cookies, origin, listOf(domain, URI(origin).host))
                    WebStorage.getInstance().deleteOrigin(origin)
                }
            }
            val stillSignedIn = LiveWebSite.entries.filter { it != this && it.hasSession() }
            identityProviderHostsToClear(this, stillSignedIn).forEach { host ->
                expireVisibleCookies(cookies, "https://$host", listOfNotNull(host, IDENTITY_PROVIDER_HOSTS[host]))
            }
            cookies.flush()
        }
    }
    onMainThread { wipeOpenPages(context) }
}

private fun onMainThread(block: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) block() else Handler(Looper.getMainLooper()).post(block)
}

/**
 * Sends every open page of this site to its start page (the sign-in page once signed out) with
 * its HTTP cache, form data and back history dropped, like [LiveWebPages.clearAndReload]. With
 * [context], a short-lived WebView in the site's profile also empties that profile's HTTP cache,
 * which covers the case where no page of the site is open. (Without profiles the HTTP cache is
 * shared, so that drops every site's cached files; only sign-ins live in cookies, not the cache.)
 */
private fun LiveWebSite.wipeOpenPages(context: Context?) {
    val open = synchronized(siteWebViews) { siteWebViews.filterValues { it == this }.keys.toList() }
    open.forEach { webView ->
        runCatching {
            webView.stopLoading()
            webView.clearCache(true)
            webView.clearFormData()
            webView.clearHistory()
            webView.loadUrl(startUrlForSession())
            synchronized(siteWebViews) { pendingHistoryClears[webView] = Unit }
            webView.clearHistoryWhenStartPageCommits()
        }
    }
    if (context != null) {
        runCatching {
            val scratch = WebView(context.applicationContext)
            try {
                if (isSupported) {
                    profile
                    WebViewCompat.setProfile(scratch, name)
                }
                scratch.clearCache(true)
            } finally {
                scratch.destroy()
            }
        }
    }
}

/** Once, removes the sessions these sites left in the shared browser store before isolation. */
internal fun clearSharedWebDataOnce(context: Context) {
    if (!isSupported) return
    val prefs = context.getSharedPreferences(ISOLATION_PREFS, Context.MODE_PRIVATE)
    if (prefs.getBoolean(KEY_SHARED_DATA_CLEARED, false)) return
    CookieManager.getInstance().removeAllCookies(null)
    WebStorage.getInstance().deleteAllData()
    prefs.edit { putBoolean(KEY_SHARED_DATA_CLEARED, true) }
}

private val TRACKING_PARAM =
    Regex(
        "^(utm_.*|fbclid|gclid|gbraid|wbraid|msclkid|dclid|yclid|ttclid|twclid|ref_src|ref_url|igsh|igshid|share_id|" +
            "li_fat_id|trk|trkInfo|trackingId|midSig|midToken|eid|otep|mibextid|rdt|rdt_cid|mc_eid|mc_cid|_hsenc|_hsmi)$",
        RegexOption.IGNORE_CASE,
    )

/**
 * Sharer identifiers that are only tracking on their own site. Elsewhere the same short names are
 * real parameters (search terms, timestamps, sizes), so they are dropped only on these hosts:
 * X's s and t, YouTube's and Spotify's si (who shared the link), LinkedIn's rcm (member token).
 */
private val HOST_SCOPED_TRACKING_PARAMS: List<Pair<Set<String>, Regex>> =
    listOf(
        setOf("x.com", "twitter.com") to Regex("^(s|t)$", RegexOption.IGNORE_CASE),
        setOf("youtube.com", "youtu.be", "spotify.com") to Regex("^si$", RegexOption.IGNORE_CASE),
        setOf("linkedin.com") to Regex("^rcm$", RegexOption.IGNORE_CASE),
    )

private fun hostScopedTrackingParams(host: String?): List<Regex> {
    val h = host?.lowercase()?.trimEnd('.') ?: return emptyList()
    return HOST_SCOPED_TRACKING_PARAMS
        .filter { (hosts, _) -> hosts.any { h == it || h.endsWith(".$it") } }
        .map { (_, params) -> params }
}

private fun queryParam(
    query: String?,
    name: String,
): String? =
    query
        ?.split("&")
        ?.firstOrNull { it.startsWith("$name=") }
        ?.substringAfter("=")
        // A malformed escape such as %zz throws; fall back to leaving the link as it was.
        ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }

private const val MAX_REDIRECT_UNWRAP_DEPTH = 3

/** Follows the site's click-tracking redirect to the real link, then drops tracking parameters. */
internal fun cleanOutgoingLink(
    url: String,
    depth: Int = 0,
): String {
    val uri = runCatching { URI(url) }.getOrNull() ?: return url
    val host = uri.host?.lowercase()
    val target =
        if (depth < MAX_REDIRECT_UNWRAP_DEPTH) {
            when {
                host == "l.instagram.com" -> queryParam(uri.rawQuery, "u")
                host == "out.reddit.com" -> queryParam(uri.rawQuery, "url")
                host == "l.facebook.com" || host == "lm.facebook.com" -> queryParam(uri.rawQuery, "u")
                (host == "linkedin.com" || host?.endsWith(".linkedin.com") == true) &&
                    (uri.path?.startsWith("/safety/go") == true || uri.path?.startsWith("/redir/") == true) -> {
                    queryParam(uri.rawQuery, "url")
                }

                else -> null
            }
        } else {
            null
        }
    if (target != null) {
        val targetUri = runCatching { URI(target.trim()) }.getOrNull()
        val targetScheme = targetUri?.scheme?.lowercase()
        if ((targetScheme == "http" || targetScheme == "https") && !targetUri.host.isNullOrBlank()) {
            return cleanOutgoingLink(target.trim(), depth + 1)
        }
        // Refuse to unwrap into non-web schemes (crux://, file://, intent://, javascript:, etc.).
        return url
    }
    val query = uri.rawQuery ?: return url
    val siteParams = hostScopedTrackingParams(uri.host)
    val kept =
        query.split("&").filterNot {
            val name = it.substringBefore("=")
            TRACKING_PARAM.matches(name) || siteParams.any { params -> params.matches(name) }
        }
    val base = url.substringBefore("?")
    val fragment = uri.rawFragment?.let { "#$it" }.orEmpty()
    return if (kept.isEmpty()) "$base$fragment" else "$base?${kept.joinToString("&")}$fragment"
}

/**
 * Validates and upgrades an initial URL intended for a standalone authenticated [site] WebView.
 * Rejects non-HTTP(S) schemes and hosts outside [site.allows], falling back to [site.startUrlForSession].
 */
internal fun sanitizeSiteInitialUrl(
    site: LiveWebSite,
    initialUrl: String?,
): String {
    val trimmed = initialUrl?.trim().orEmpty()
    if (trimmed.isEmpty()) return site.startUrlForSession()
    val uri = runCatching { URI(trimmed) }.getOrNull() ?: return site.startUrlForSession()
    val scheme = uri.scheme?.lowercase() ?: return site.startUrlForSession()
    if ((scheme != "https" && scheme != "http") || !site.allows(uri.host)) {
        return site.startUrlForSession()
    }
    return if (scheme == "http") "https://" + trimmed.substringAfter("://") else trimmed
}

private val bridgeTokenRandom = SecureRandom()

internal fun generateWebBridgeToken(): String {
    val bytes = ByteArray(16)
    bridgeTokenRandom.nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}

/**
 * Remembers each home live-web tab's last visited URL and vertical scroll offset across process
 * death, scoped strictly to URLs hosted on that site.
 */
internal object LiveWebSessionState {
    private const val PREFS_NAME = "crux_web_state"
    private val AUTH_PATH_REGEX =
        Regex(
            "(^|/)(i/flow|login|accounts/login|accounts/onetap|uas/login|account/access|account/login_verification|checkpoint|challenge)(/|$)",
            RegexOption.IGNORE_CASE,
        )

    private fun urlKey(site: LiveWebSite) = "url.${site.name}"

    private fun scrollKey(site: LiveWebSite) = "scroll.${site.name}"

    fun isAuthOrLoginUrl(url: String?): Boolean {
        val candidate = url?.trim() ?: return false
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return false
        val host = uri.host?.lowercase()?.trimEnd('.').orEmpty()
        if (host == "accounts.google.com" || host == "appleid.apple.com") return true
        val path = uri.path.orEmpty()
        return AUTH_PATH_REGEX.containsMatchIn(path)
    }

    fun isRestorableUrl(
        site: LiveWebSite,
        url: String?,
    ): Boolean {
        val candidate = url?.trim() ?: return false
        if (isExternalOAuthUrl(candidate)) return false
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true) || !site.allows(uri.host)) return false
        val path = uri.path.orEmpty()
        if (site == LiveWebSite.X && (path.isEmpty() || path == "/")) return false
        if (AUTH_PATH_REGEX.containsMatchIn(path)) return false
        return true
    }

    fun save(
        context: Context,
        site: LiveWebSite,
        url: String?,
        scrollY: Int,
    ) {
        if (site.isSignedInAccount && !site.hasSession()) return
        if (!isRestorableUrl(site, url)) return
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(urlKey(site), url)
            putInt(scrollKey(site), scrollY.coerceAtLeast(0))
        }
    }

    fun restore(
        context: Context,
        site: LiveWebSite,
    ): Pair<String, Int>? {
        if (site.isSignedInAccount && !site.hasSession()) {
            clear(context, site)
            return null
        }
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedUrl = prefs.getString(urlKey(site), null) ?: return null
        if (!isRestorableUrl(site, savedUrl)) {
            clear(context, site)
            return null
        }
        return savedUrl to prefs.getInt(scrollKey(site), 0).coerceAtLeast(0)
    }

    fun clear(
        context: Context,
        site: LiveWebSite,
    ) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            remove(urlKey(site))
            remove(scrollKey(site))
        }
    }
}

/**
 * Hides Crux's package name from the `X-Requested-With` header and disables WebView algorithmic
 * darkening (which otherwise color-inverts avatars and photos on light-themed pages like LinkedIn).
 */
@SuppressLint("RestrictedApi")
@Suppress("DEPRECATION")
internal fun configureWebViewHeadersAndDarkening(settings: WebSettings) {
    if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
        WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
    }
    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
        WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)
    }
}

private val EXTERNAL_OAUTH_HOSTS =
    setOf(
        "accounts.google.com",
        "accounts.youtube.com",
        "appleid.apple.com",
        "myaccount.google.com",
        "gds.google.com",
    )

private val OAUTH_BOUNCE_PATH_REGEX =
    Regex("(^|/)(oauth2?|uas/connect|checkpoint/lg/login-with-|accounts/onetap|i/flow/sso|auth/google)(/|$)", RegexOption.IGNORE_CASE)

/** True when [url] is hosted on a third-party identity provider (Google, Apple). */
internal fun isExternalOAuthUrl(url: String?): Boolean {
    val uri = url?.trim()?.let { runCatching { URI(it) }.getOrNull() } ?: return false
    val host = uri.host?.lowercase()?.trimEnd('.').orEmpty()
    return EXTERNAL_OAUTH_HOSTS.any { host == it || host.endsWith(".$it") }
}

private fun isOAuthRedirectHop(url: String?): Boolean {
    if (url.isNullOrBlank() || url == "about:blank") return true
    if (isExternalOAuthUrl(url)) return true
    val path = runCatching { URI(url.trim()).path }.getOrNull().orEmpty()
    return OAUTH_BOUNCE_PATH_REGEX.containsMatchIn(path)
}

internal fun computeWebViewCanGoBack(
    webViewCanGoBack: Boolean,
    currentUrl: String?,
): Boolean = webViewCanGoBack || isExternalOAuthUrl(currentUrl)

internal sealed interface BackNavigationAction {
    data object NativeGoBack : BackNavigationAction

    data class GoSteps(
        val steps: Int,
    ) : BackNavigationAction

    data class LoadUrl(
        val url: String,
    ) : BackNavigationAction
}

/**
 * When navigating back from an external OAuth provider (such as Google Sign-In on LinkedIn),
 * skips over all Google and OAuth relay redirect hops back to the previous site page, or falls back
 * to [site.loginUrl] if no non-OAuth entry exists in history (e.g. after `location.replace`).
 */
internal fun computeBackNavigationAction(
    currentUrl: String?,
    historyUrls: List<String>,
    currentIndex: Int,
    site: LiveWebSite,
): BackNavigationAction {
    if (!isExternalOAuthUrl(currentUrl)) return BackNavigationAction.NativeGoBack
    val startIdx = (currentIndex - 1).coerceAtMost(historyUrls.lastIndex)
    for (idx in startIdx downTo 0) {
        val candidate = historyUrls[idx]
        if (!isOAuthRedirectHop(candidate)) {
            return BackNavigationAction.GoSteps(idx - currentIndex)
        }
    }
    return BackNavigationAction.LoadUrl(site.loginUrl)
}

internal fun navigateWebViewBackOrEscapeOAuth(
    webView: WebView,
    site: LiveWebSite,
) {
    val history = webView.copyBackForwardList()
    val historyUrls = (0 until history.size).map { idx -> history.getItemAtIndex(idx)?.url.orEmpty() }
    when (val action = computeBackNavigationAction(webView.url, historyUrls, history.currentIndex, site)) {
        is BackNavigationAction.GoSteps -> {
            if (action.steps < 0 && webView.canGoBackOrForward(action.steps)) {
                webView.goBackOrForward(action.steps)
            } else if (webView.canGoBack()) {
                webView.goBack()
            } else {
                webView.loadUrl(site.loginUrl)
            }
        }

        is BackNavigationAction.LoadUrl -> {
            webView.loadUrl(action.url)
        }

        BackNavigationAction.NativeGoBack -> {
            if (webView.canGoBack()) {
                webView.goBack()
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

/**
 * Computes an updated [currentUiMode] with [expectedNightMask] restored when Chromium `WebView`
 * initialization clears `UI_MODE_NIGHT_MASK` on a plain `ComponentActivity`. Returns `null` if no
 * change is needed.
 */
internal fun computeRestoredNightUiMode(
    currentUiMode: Int,
    expectedNightMask: Int,
): Int? {
    if (expectedNightMask == Configuration.UI_MODE_NIGHT_UNDEFINED) return null
    if ((currentUiMode and Configuration.UI_MODE_NIGHT_MASK) == expectedNightMask) return null
    return (currentUiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or expectedNightMask
}

internal fun Context.currentUiNightMask(): Int {
    val hostResources = (findActivity()?.resources ?: resources)
    return hostResources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
}

@Suppress("DEPRECATION")
private fun restoreResourcesNightMask(
    resources: Resources,
    expectedNightMask: Int,
) {
    val restoredUiMode = computeRestoredNightUiMode(resources.configuration.uiMode, expectedNightMask) ?: return
    val fixed = Configuration(resources.configuration).apply { uiMode = restoredUiMode }
    resources.updateConfiguration(fixed, resources.displayMetrics)
}

internal fun restoreUiModeNightMaskIfNeeded(
    context: Context,
    expectedNightMask: Int,
) {
    if (expectedNightMask == Configuration.UI_MODE_NIGHT_UNDEFINED) return
    context.findActivity()?.resources?.let { restoreResourcesNightMask(it, expectedNightMask) }
    restoreResourcesNightMask(context.resources, expectedNightMask)
    restoreResourcesNightMask(context.applicationContext.resources, expectedNightMask)
}

internal fun createThemeSafeWebView(context: Context): WebView {
    val expectedNightMask = context.currentUiNightMask()
    val webView = WebView(context)
    restoreUiModeNightMaskIfNeeded(context, expectedNightMask)
    webView.post {
        restoreUiModeNightMaskIfNeeded(context, expectedNightMask)
    }
    return webView
}

/** Compact host + first path segment for standalone WebView top-bar subtitles (e.g. `instagram.com/natgeo`). */
internal fun formatCompactHostPath(url: String?): String? {
    val raw = url?.trim()?.takeIf { it.isNotEmpty() && it != "about:blank" } ?: return null
    val uri = runCatching { URI(raw) }.getOrNull() ?: return null
    val host = uri.host?.lowercase()?.removePrefix("www.")?.takeIf { it.isNotBlank() } ?: return null
    val cleanPath = uri.path?.trimEnd('/').orEmpty()
    return if (cleanPath.isEmpty() || cleanPath == "/") {
        host
    } else {
        "$host$cleanPath"
    }
}
