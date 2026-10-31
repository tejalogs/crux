package dev.dimension.flare.ui.screen.home

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.core.content.edit
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.net.URI
import java.net.URLDecoder

private const val ISOLATION_PREFS = "live_web_isolation"
private const val KEY_SHARED_DATA_CLEARED = "shared_data_cleared"

private val isSupported: Boolean
    get() = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

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

/** Once, removes the sessions these sites left in the shared browser store before isolation. */
internal fun clearSharedWebDataOnce(context: Context) {
    if (!isSupported) return
    val prefs = context.getSharedPreferences(ISOLATION_PREFS, Context.MODE_PRIVATE)
    if (prefs.getBoolean(KEY_SHARED_DATA_CLEARED, false)) return
    CookieManager.getInstance().removeAllCookies(null)
    WebStorage.getInstance().deleteAllData()
    prefs.edit { putBoolean(KEY_SHARED_DATA_CLEARED, true) }
}

private val TRACKING_PARAM = Regex("^(utm_.*|fbclid|gclid|igsh|igshid|li_fat_id|trk|trkInfo|mibextid)$", RegexOption.IGNORE_CASE)

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
    val kept = query.split("&").filterNot { TRACKING_PARAM.matches(it.substringBefore("=")) }
    val base = url.substringBefore("?")
    val fragment = uri.rawFragment?.let { "#$it" }.orEmpty()
    return if (kept.isEmpty()) "$base$fragment" else "$base?${kept.joinToString("&")}$fragment"
}
