package org.crux.ui.screen.home

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.crux.data.model.tab.TimelineSpecIds
import org.crux.data.model.tab.UiTimelineTabItem
import org.crux.ui.component.LocalBottomBarAccessory
import java.io.ByteArrayInputStream

// Modern Chrome Mobile User-Agent to avoid Google OAuth 403 disallowed_useragent and webview friction.
private const val CHROME_MOBILE_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

internal fun UiTimelineTabItem.liveWebSite(): LiveWebSite? = liveWebSiteForTabId(id)

/**
 * Which live site a home tab id shows. Instagram and LinkedIn pills are static (`web.instagram:...`)
 * but older installs saved them per signed-in account (`common.home:<key>@instagram.com`), so those
 * ids still resolve until they are migrated.
 */
internal fun liveWebSiteForTabId(id: String): LiveWebSite? =
    when {
        id.startsWith("${TimelineSpecIds.WEB_X}:") -> {
            LiveWebSite.X
        }

        id.startsWith("${TimelineSpecIds.WEB_REDDIT}:") -> {
            LiveWebSite.Reddit
        }

        id.startsWith("${TimelineSpecIds.WEB_INSTAGRAM}:") -> {
            LiveWebSite.Instagram
        }

        id.startsWith("${TimelineSpecIds.WEB_LINKEDIN}:") -> {
            LiveWebSite.LinkedIn
        }

        id.startsWith("${TimelineSpecIds.COMMON_HOME}:") -> {
            LiveWebSite.entries.firstOrNull { it.tabIdSuffix.isNotEmpty() && id.endsWith(it.tabIdSuffix) }
        }

        else -> {
            null
        }
    }

/** One WebView per site, kept for the whole home screen so tabs stay logged in and scrolled. */
internal class LiveWebPages {
    private val pages = mutableMapOf<LiveWebSite, LiveWebPage>()

    /** Called whenever any site page finishes loading, e.g. so a fresh sign-in is noticed. */
    var onPageFinished: (() -> Unit)? = null

    /**
     * The site's page, recreated when its settings changed: scripts and request blocking are fixed
     * when a WebView is built, so a new setting only applies to a new page.
     */
    fun page(
        site: LiveWebSite,
        context: Context,
        config: LiveWebConfig = LiveWebPrivacyStore.get(context).configFor(site),
    ): LiveWebPage =
        pages[site]?.takeIf { it.startUrl == site.homeUrl && it.config == config }
            ?: run {
                pages.remove(site)?.destroy()
                clearSharedWebDataOnce(context)
                LiveWebPage(site, context, config = config).also {
                    it.onSiteLoaded = { onPageFinished?.invoke() }
                    pages[site] = it
                }
            }

    /** Reloads a site's home tab, e.g. after signing in on a separate page. */
    fun reload(site: LiveWebSite) {
        pages[site]?.webView?.reload()
    }

    fun destroy() {
        pages.values.forEach { it.destroy() }
        pages.clear()
    }
}

internal val LocalLiveWebPages = staticCompositionLocalOf<LiveWebPages?> { null }

@SuppressLint("SetJavaScriptEnabled")
internal class LiveWebPage(
    site: LiveWebSite,
    context: Context,
    val startUrl: String = site.homeUrl,
    val config: LiveWebConfig = LiveWebPrivacyStore.get(context).configFor(site),
) {
    private fun outgoing(url: String): String = if (config.cleanLinks) cleanOutgoingLink(url) else url

    /** Off-site links go to the default browser, or to Crux's own screen when they are another social site. */
    private fun openOutside(
        context: Context,
        url: String,
    ) = openLinkOutsideWebView(context, url, config.cleanLinks)

    var canGoBack by mutableStateOf(false)
        private set
    var onFileChooser: ((ValueCallback<Array<Uri>>) -> Unit)? = null
    var onPageLoaded: (() -> Unit)? = null
    var onSiteLoaded: (() -> Unit)? = null

    val webView: WebView =
        WebView(context).apply {
            useProfileOf(site)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = CHROME_MOBILE_USER_AGENT
            // Without these WebView ignores the page's mobile viewport, and wide layouts get cropped.
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.setSupportMultipleWindows(true)
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.mediaPlaybackRequiresUserGesture = config.blockAutoplay
            site.cookieManager().setAcceptThirdPartyCookies(this, true)
            val documentStartScripts = config.documentStartScripts(site)
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                documentStartScripts.forEach { script ->
                    WebViewCompat.addDocumentStartJavaScript(this, script, setOf("*"))
                }
            }
            // Sites otherwise get the app's ID in a header on every request.
            if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
            }
            webViewClient =
                object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse? =
                        if (config.blocks(site, request.url.host)) {
                            WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                        } else {
                            super.shouldInterceptRequest(view, request)
                        }

                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean {
                        val url = request.url
                        val cleaned = outgoing(url.toString())
                        val isRedirect = cleaned.substringBefore("?") != url.toString().substringBefore("?")
                        if (url.scheme == "https" && site.allows(url.host) && !isRedirect) return false
                        if (url.scheme == "https" || url.scheme == "http") {
                            openOutside(view.context, url.toString())
                        }
                        return true
                    }

                    override fun onPageFinished(
                        view: WebView,
                        url: String?,
                    ) {
                        site.cookieManager().flush()
                        // Fallback for WebViews that cannot inject at document start. Each script guards itself.
                        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                            config.documentStartScripts(site).forEach { view.evaluateJavascript(it, null) }
                        }
                        onPageLoaded?.invoke()
                        onSiteLoaded?.invoke()
                    }

                    override fun doUpdateVisitedHistory(
                        view: WebView,
                        url: String?,
                        isReload: Boolean,
                    ) {
                        canGoBack = view.canGoBack()
                    }
                }
            webChromeClient =
                object : WebChromeClient() {
                    override fun onCreateWindow(
                        view: WebView,
                        isDialog: Boolean,
                        isUserGesture: Boolean,
                        resultMsg: android.os.Message?,
                    ): Boolean {
                        // Fast path: if the user tapped a link with an explicit target URL, handle it directly.
                        val hitUrl = view.hitTestResult.extra
                        if (!hitUrl.isNullOrEmpty() && (hitUrl.startsWith("http://") || hitUrl.startsWith("https://"))) {
                            val hitHost = runCatching { hitUrl.toUri().host }.getOrNull()
                            val cleaned = outgoing(hitUrl)
                            val isRedirect = cleaned.substringBefore("?") != hitUrl.substringBefore("?")
                            if (!site.allows(hitHost) || isRedirect) {
                                openOutside(view.context, hitUrl)
                                return false
                            }
                        }

                        val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                        val popupWebView =
                            WebView(view.context).apply {
                                useProfileOf(site)
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.userAgentString = CHROME_MOBILE_USER_AGENT
                                webViewClient =
                                    object : WebViewClient() {
                                        private var handled = false

                                        private fun handleTarget(targetUrl: String): Boolean {
                                            if (handled) return true
                                            val uri = runCatching { targetUrl.toUri() }.getOrNull() ?: return false
                                            val host = uri.host
                                            val cleaned = outgoing(targetUrl)
                                            val isRedirect = cleaned.substringBefore("?") != targetUrl.substringBefore("?")
                                            handled = true
                                            if (targetUrl.startsWith("https://") && site.allows(host) && !isRedirect) {
                                                this@LiveWebPage.webView.loadUrl(targetUrl)
                                            } else {
                                                openOutside(this@LiveWebPage.webView.context, targetUrl)
                                            }
                                            post { destroy() }
                                            return true
                                        }

                                        override fun shouldOverrideUrlLoading(
                                            popup: WebView,
                                            request: WebResourceRequest,
                                        ): Boolean = handleTarget(request.url.toString())

                                        override fun onPageStarted(
                                            popup: WebView,
                                            url: String?,
                                            favicon: Bitmap?,
                                        ) {
                                            if (url != null && url != "about:blank") {
                                                handleTarget(url)
                                            }
                                        }

                                        override fun onPageFinished(
                                            popup: WebView,
                                            url: String?,
                                        ) {
                                            site.cookieManager().flush()
                                        }
                                    }
                            }
                        transport.webView = popupWebView
                        resultMsg.sendToTarget()
                        return true
                    }

                    override fun onShowFileChooser(
                        webView: WebView,
                        filePathCallback: ValueCallback<Array<Uri>>,
                        fileChooserParams: FileChooserParams,
                    ): Boolean {
                        val chooser = onFileChooser ?: return false
                        chooser(filePathCallback)
                        return true
                    }
                }
            loadUrl(startUrl)
        }

    fun goBack() {
        webView.goBack()
    }

    /** Same as tapping a feed pill again: back to the top, or reload if already there. */
    fun readPostInView(onResult: (LiveWebPost?) -> Unit) {
        webView.evaluateJavascript(READ_POST_SCRIPT) { result -> onResult(parseLiveWebPost(result)) }
    }

    fun scrollToTopOrReload() {
        if (webView.canScrollVertically(-1)) {
            webView.pageUp(true)
        } else {
            webView.reload()
        }
    }

    fun destroy() {
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    }
}

/** Lets the page's own upload buttons open the phone's file picker. */
@Composable
internal fun BindFileChooser(page: LiveWebPage) {
    var pendingFiles by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            pendingFiles?.onReceiveValue(uris.toTypedArray())
            pendingFiles = null
        }
    DisposableEffect(page) {
        page.onFileChooser = { callback ->
            pendingFiles?.onReceiveValue(null)
            pendingFiles = callback
            fileLauncher.launch("*/*")
        }
        onDispose {
            page.onFileChooser = null
            pendingFiles?.onReceiveValue(null)
            pendingFiles = null
        }
    }
}

@Composable
internal fun LiveWebTab(
    site: LiveWebSite,
    isCurrentlyVisible: Boolean,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    val pages = LocalLiveWebPages.current ?: return
    val homeUrl = site.homeUrl
    val store = remember(context) { LiveWebPrivacyStore.get(context) }
    val allSettings by store.settings.collectAsState()
    // A changed setting gives a new config, which makes the pages holder build a fresh page.
    val config = remember(allSettings, site) { allSettings.configFor(site) }
    val page = remember(site, pages, homeUrl, config) { pages.page(site, context, config) }
    BindFileChooser(page)
    val accessory = LocalBottomBarAccessory.current
    DisposableEffect(accessory, isCurrentlyVisible, site) {
        if (isCurrentlyVisible) accessory?.keepVisibleOwner = site
        onDispose {
            if (accessory?.keepVisibleOwner == site) accessory.keepVisibleOwner = null
        }
    }
    val smartReply = rememberSmartReplyAction(site, page)
    DisposableEffect(accessory, isCurrentlyVisible, smartReply) {
        if (isCurrentlyVisible && site.smartReplies) accessory?.smartReply = smartReply
        onDispose {
            if (accessory?.smartReply === smartReply) accessory.smartReply = null
        }
    }
    val scrollToTopRegistry = LocalScrollToTopRegistry.current
    DisposableEffect(scrollToTopRegistry, page, isCurrentlyVisible) {
        val callback = page::scrollToTopOrReload
        if (isCurrentlyVisible) scrollToTopRegistry?.registerCallback(callback)
        onDispose { scrollToTopRegistry?.unregisterCallback(callback) }
    }
    // Automatically freezes background WebView renderers and media when hidden or minimized.
    WebSessionLifecycleSuspender(webView = page.webView, isVisible = isCurrentlyVisible)
    BackHandler(enabled = isCurrentlyVisible && page.canGoBack) {
        page.goBack()
    }
    // The page can be replaced when a setting changes, so the view is rebuilt with it.
    key(page) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .imePadding(),
                factory = { viewContext ->
                    val container =
                        if (site.pullToRefresh) {
                            SwipeRefreshLayout(viewContext).apply {
                                setOnRefreshListener { page.webView.reload() }
                                page.onPageLoaded = { isRefreshing = false }
                            }
                        } else {
                            FrameLayout(viewContext)
                        }
                    (page.webView.parent as? ViewGroup)?.removeView(page.webView)
                    container.addView(
                        page.webView,
                        ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                    )
                    container
                },
                onRelease = {
                    page.onPageLoaded = null
                    it.removeAllViews()
                },
            )
        }
    }
}
