package org.crux.ui.screen.home

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.ServiceWorkerClient
import android.webkit.ServiceWorkerController
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.webkit.ProfileStore
import androidx.webkit.ServiceWorkerClientCompat
import androidx.webkit.ServiceWorkerControllerCompat
import androidx.webkit.WebViewFeature
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import org.crux.BuildConfig

/**
 * The one request decision for a site's traffic, used by the page, its popups and its service
 * workers: note the host in [LiveWebContactLog], and refuse it only when the site's protection
 * level blocks it (Strict). Standard sites are observed and never changed. Returns the empty
 * response to send instead, or null to let the request go.
 */
internal fun siteRequestResponse(
    site: LiveWebSite,
    blocked: Boolean,
    host: String?,
    counters: LiveWebPrivacyCounters,
): WebResourceResponse? {
    LiveWebContactLog.record(site, host, blocked)
    if (!blocked) return null
    // Pages, popups and service workers call this from different network threads, and the
    // counters' update is a read-modify-write; blocks are rare, so a lock costs nothing here.
    synchronized(counters) { counters.recordBlockedRequest(site) }
    return blockedTrackerResponse()
}

/** [siteRequestResponse] with the block decision taken from [config]. */
internal fun siteRequestResponse(
    site: LiveWebSite,
    config: LiveWebConfig,
    host: String?,
    counters: LiveWebPrivacyCounters,
): WebResourceResponse? = siteRequestResponse(site, config.blocks(site, host), host, counters)

/**
 * Reads the current protection level for service-worker requests, which outlive any one page.
 * The config is rebuilt only when the stored settings object changes, so a request costs one
 * reference compare.
 */
private class LiveConfigReader(
    context: Context,
) {
    private val store = LiveWebPrivacyStore.get(context)

    @Volatile
    private var cached: Pair<WebPrivacySettings, Map<LiveWebSite, LiveWebConfig>>? = null

    fun configFor(site: LiveWebSite): LiveWebConfig {
        val settings = store.settings.value
        val current = cached
        val configs =
            if (current != null && current.first === settings) {
                current.second
            } else {
                LiveWebSite.entries.associateWith { settings.configFor(it) }.also { cached = settings to it }
            }
        return configs.getValue(site)
    }
}

/**
 * Which site a request in the shared (profile-less) service-worker store belongs to. The page's
 * Origin (or Referer) decides when there is one: a site page's worker is that site's, and any
 * other page's (the Hardened Browser, the reader, a shared sign-in page) is left alone. Without
 * either header the request host decides, but only when exactly one site claims it, first by its
 * own hosts and then by its tracker list; a host several sites share (Google or Apple sign-in,
 * captchas, the common ad and analytics hosts) cannot be credited fairly, so it is neither
 * logged nor blocked.
 * Best effort by design: profiles (Android WebView 123+) remove the guesswork.
 */
internal fun siteForSharedServiceWorkerRequest(
    url: String?,
    origin: String?,
): LiveWebSite? {
    fun hostOf(value: String?): String? = value?.let { runCatching { URI(it.trim()).host }.getOrNull() }?.lowercase()?.trimEnd('.')
    val pageHost = hostOf(origin)
    if (pageHost != null) return LiveWebSite.entries.singleOrNull { it.allows(pageHost) }
    val requestHost = hostOf(url) ?: return null
    LiveWebSite.entries.singleOrNull { it.allows(requestHost) }?.let { return it }
    if (LiveWebSite.entries.any { it.allows(requestHost) }) return null
    return LiveWebSite.entries.singleOrNull { hostMatchesAny(requestHost, it.trackerHosts) }
}

private const val SHARED_STORE_KEY = "\u0000shared"

/** Profiles (by name) whose service workers are already covered, so each is set up once. */
private val coveredServiceWorkerStores: MutableSet<String> = ConcurrentHashMap.newKeySet()

/**
 * Service-worker coverage for [site]: requests a registered service worker makes (push, sync,
 * offline caches, re-issued fetches) go through [siteRequestResponse] like the page's own, and
 * the X-Requested-With header that would carry Crux's package name is turned off for them too.
 *
 * With profiles, each site's profile has its own controller (androidx.webkit 1.17.1 exposes it
 * as the framework [ServiceWorkerController] from `Profile.getServiceWorkerController()`), so the
 * site is known. Without profiles every site shares the one global controller, and the site is
 * worked out from the request's Origin or host. Safe to call for every new WebView.
 */
internal fun installServiceWorkerCoverage(
    context: Context,
    site: LiveWebSite,
) {
    val appContext = context.applicationContext
    clearSharedServiceWorkerRequestedWith()
    if (multiProfileSupported) {
        if (!coveredServiceWorkerStores.add(site.name)) return
        val installed =
            runCatching {
                val controller = ProfileStore.getInstance().getOrCreateProfile(site.name).serviceWorkerController
                clearRequestedWith(controller)
                controller.setServiceWorkerClient(SiteServiceWorkerClient(appContext, site))
            }
        installed.onFailure { error ->
            coveredServiceWorkerStores.remove(site.name)
            if (BuildConfig.DEBUG) Log.w("LiveWebSw", "service-worker coverage failed for ${site.name}", error)
        }
        return
    }
    if (!coveredServiceWorkerStores.add(SHARED_STORE_KEY)) return
    if (
        !WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE) ||
        !WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_SHOULD_INTERCEPT_REQUEST)
    ) {
        return
    }
    runCatching {
        ServiceWorkerControllerCompat.getInstance().setServiceWorkerClient(SharedServiceWorkerClient(appContext))
    }.onFailure { coveredServiceWorkerStores.remove(SHARED_STORE_KEY) }
}

private var sharedRequestedWithCleared = false

/** The default profile's service workers (the Hardened Browser fallback, the RSS reader's pages). */
@SuppressLint("RestrictedApi")
private fun clearSharedServiceWorkerRequestedWith() {
    if (sharedRequestedWithCleared) return
    sharedRequestedWithCleared = true
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE)) return
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) return
    runCatching {
        ServiceWorkerControllerCompat.getInstance().serviceWorkerWebSettings.setRequestedWithHeaderOriginAllowList(emptySet())
    }
}

/**
 * A profile's service-worker settings come back as the framework class, which has no
 * X-Requested-With setter. androidx.webkit wraps the very same object this way for the default
 * profile (ServiceWorkerControllerImpl), so the wrapper is used directly; any failure (a WebView
 * too old for the call) leaves the header as WebView's default.
 */
@SuppressLint("RestrictedApi")
private fun clearRequestedWith(controller: ServiceWorkerController) {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) return
    runCatching {
        androidx.webkit.internal
            .ServiceWorkerWebSettingsImpl(controller.serviceWorkerWebSettings)
            .setRequestedWithHeaderOriginAllowList(emptySet())
    }
}

private class SiteServiceWorkerClient(
    context: Context,
    private val site: LiveWebSite,
) : ServiceWorkerClient() {
    private val configs = LiveConfigReader(context)
    private val counters = LiveWebPrivacyCounters.get(context)

    override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? =
        siteRequestResponse(site, configs.configFor(site), request.url?.host, counters)
}

private class SharedServiceWorkerClient(
    context: Context,
) : ServiceWorkerClientCompat() {
    private val configs = LiveConfigReader(context)
    private val counters = LiveWebPrivacyCounters.get(context)

    override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
        val headers = request.requestHeaders.orEmpty()
        fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
        val origin = header("Origin") ?: header("Referer")
        val site = siteForSharedServiceWorkerRequest(request.url?.toString(), origin) ?: return null
        return siteRequestResponse(site, configs.configFor(site), request.url?.host, counters)
    }
}
