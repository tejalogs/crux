package org.crux.ui.screen.home

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
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

/** Each site's cookies, storage, cache and service workers live in that site's own profile. */
internal fun WebView.useProfileOf(site: LiveWebSite) {
    if (isSupported) {
        site.profile
        WebViewCompat.setProfile(this, site.name)
    }
}

internal fun LiveWebSite.cookieManager(): CookieManager = if (isSupported) profile.cookieManager else CookieManager.getInstance()

internal fun LiveWebSite.webStorage(): WebStorage = if (isSupported) profile.webStorage else WebStorage.getInstance()

/** Origins WebStorage can be asked to forget for a cookie domain. */
internal fun storageOriginsFor(domain: String): List<String> = listOf("https://$domain", "https://www.$domain")

/** Set-Cookie strings that expire [name] on [domain], both as a host-only and a domain cookie. */
internal fun expiryCookiesFor(
    domain: String,
    name: String,
): List<String> {
    val expired = "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/"
    return listOf(expired, "$expired; Domain=.$domain")
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
 * this site's own domains are expired and only its origins' storage is forgotten.
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
                storageOriginsFor(domain).forEach { origin ->
                    cookieNames(cookies.getCookie("$origin/")).forEach { name ->
                        expiryCookiesFor(domain, name).forEach { cookies.setCookie("$origin/", it) }
                    }
                    WebStorage.getInstance().deleteOrigin(origin)
                }
            }
            cookies.flush()
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
        "^(utm_.*|fbclid|gclid|twclid|ref_src|ref_url|igsh|igshid|share_id|li_fat_id|trk|trkInfo|trackingId|midSig|midToken|eid|otep|mibextid|rdt|rdt_cid)$",
        RegexOption.IGNORE_CASE,
    )

// X's share links add s and t. On other sites they are real parameters (search, timestamps).
private val X_SHARE_PARAM = Regex("^(s|t)$", RegexOption.IGNORE_CASE)
private val X_HOSTS = setOf("x.com", "twitter.com")

private fun isXHost(host: String?): Boolean {
    val h = host?.lowercase() ?: return false
    return X_HOSTS.any { h == it || h.endsWith(".$it") }
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
    val xLink = isXHost(uri.host)
    val kept =
        query.split("&").filterNot {
            val name = it.substringBefore("=")
            TRACKING_PARAM.matches(name) || (xLink && X_SHARE_PARAM.matches(name))
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
