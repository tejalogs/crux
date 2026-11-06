package org.crux.ui.screen.home

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.net.URI
import java.net.URLDecoder

private const val ISOLATION_PREFS = "live_web_isolation"
private const val KEY_SHARED_DATA_CLEARED = "shared_data_cleared"

private const val FREEZE_MEDIA_SCRIPT =
    """
    (function() {
        try {
            document.querySelectorAll('video, audio').forEach(function(m) {
                m.pause();
                m.muted = true;
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
        onPause()
        evaluateJavascript(FREEZE_MEDIA_SCRIPT, null)
    }
}

/**
 * Resumes execution for a WebView session when brought back to foreground.
 */
internal fun WebView.resumeWebSession() {
    runCatching {
        onResume()
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
internal fun LiveWebSite.clearSession() {
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
        ?.let { URLDecoder.decode(it, "UTF-8") }

/** Follows the site's click-tracking redirect to the real link, then drops tracking parameters. */
internal fun cleanOutgoingLink(url: String): String {
    val uri = runCatching { URI(url) }.getOrNull() ?: return url
    val target =
        when {
            uri.host == "l.instagram.com" -> queryParam(uri.rawQuery, "u")
            uri.host?.endsWith("linkedin.com") == true && uri.path.startsWith("/safety/go") -> queryParam(uri.rawQuery, "url")
            else -> null
        }
    if (target != null) return cleanOutgoingLink(target)
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
