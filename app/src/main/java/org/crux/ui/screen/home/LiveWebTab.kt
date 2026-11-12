package org.crux.ui.screen.home

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Copy
import compose.icons.fontawesomeicons.solid.Download
import compose.icons.fontawesomeicons.solid.RotateRight
import compose.icons.fontawesomeicons.solid.ShareNodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.crux.BuildConfig
import org.crux.R
import org.crux.common.AndroidDownloadManager
import org.crux.data.model.tab.TimelineSpecIds
import org.crux.data.model.tab.UiTimelineTabItem
import org.crux.ui.component.FAIcon
import org.crux.ui.component.LocalBottomBarAccessory
import org.crux.ui.screen.crux.CruxPlatform
import org.crux.ui.screen.crux.PlatformBadge
import org.koin.compose.koinInject
import java.io.ByteArrayInputStream
import kotlin.math.abs

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
        pages[site]?.takeIf { it.config == config }
            ?: run {
                pages.remove(site)?.destroy()
                clearSharedWebDataOnce(context)
                val saved =
                    if (config.rememberTabPosition) {
                        LiveWebSessionState.restore(context, site)
                    } else {
                        LiveWebSessionState.clear(context, site)
                        null
                    }
                LiveWebPage(
                    site = site,
                    context = context,
                    startUrl = saved?.first ?: site.homeUrl,
                    initialScrollY = saved?.second ?: 0,
                    persistSession = config.rememberTabPosition,
                    config = config,
                ).also {
                    it.onSiteLoaded = { onPageFinished?.invoke() }
                    pages[site] = it
                }
            }

    /** Reloads a site's home tab, e.g. after signing in on a separate page. */
    fun reload(site: LiveWebSite) {
        pages[site]?.webView?.reload()
    }

    /** Clears an active site's in-memory cache, form data, and history, then loads its home URL. */
    fun clearAndReload(site: LiveWebSite) {
        pages[site]?.clearInMemoryAndReload()
    }

    fun destroy() {
        pages.values.forEach { it.destroy() }
        pages.clear()
    }
}

internal val LocalLiveWebPages = staticCompositionLocalOf<LiveWebPages?> { null }

@SuppressLint("SetJavaScriptEnabled")
internal class LiveWebPage(
    private val site: LiveWebSite,
    context: Context,
    val startUrl: String = site.homeUrl,
    initialScrollY: Int = 0,
    private val persistSession: Boolean = false,
    val config: LiveWebConfig = LiveWebPrivacyStore.get(context).configFor(site),
) {
    private val appContext = context.applicationContext
    private var pendingRestoreScrollY = initialScrollY.coerceAtLeast(0)

    private fun outgoing(url: String): String = if (config.cleanLinks) cleanOutgoingLink(url) else url

    /** Off-site links go to the Crux Reader or default browser, or to Crux's own screen for another social site. */
    private fun openOutside(
        context: Context,
        url: String,
    ) = openLinkOutsideWebView(
        context = context,
        url = url,
        clean = config.cleanLinks,
        openInReader = config.openInReader,
    )

    var canGoBack by mutableStateOf(false)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var isLoading by mutableStateOf(true)
        private set
    var loadProgress by mutableIntStateOf(10)
        private set
    var onFileChooser: ((ValueCallback<Array<Uri>>) -> Unit)? = null
    var onImageLongPress: ((String) -> Unit)? = null
    var onPageLoaded: (() -> Unit)? = null
    var onSiteLoaded: (() -> Unit)? = null

    val webView: WebView =
        WebView(context).apply {
            useProfileOf(site)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            isNestedScrollingEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            // The WebView's real identity: a spoofed one disagrees with its other signals, and that
            // mismatch is a common automation tell (it was part of the Instagram flag in Oct 2026).
            // Without these WebView ignores the page's mobile viewport, and wide layouts get cropped.
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.setSupportMultipleWindows(true)
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.mediaPlaybackRequiresUserGesture = config.blockAutoplay
            hardenWebSettings(site, config.blockThirdPartyCookies)
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
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, true)
            }
            webViewClient =
                object : WebViewClient() {
                    override fun onPageStarted(
                        view: WebView,
                        url: String?,
                        favicon: Bitmap?,
                    ) {
                        super.onPageStarted(view, url, favicon)
                        if (url != null && url != "about:blank") {
                            loadError = null
                            isLoading = true
                            loadProgress = 10
                        }
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError,
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request.isForMainFrame) {
                            loadError = error.description?.toString() ?: "Failed to connect"
                            isLoading = false
                            loadProgress = 100
                            onPageLoaded?.invoke()
                        }
                    }

                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse? =
                        if (config.blocks(site, request.url.host)) {
                            LiveWebPrivacyCounters.get(view.context).recordBlockedRequest(site)
                            WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                        } else {
                            super.shouldInterceptRequest(view, request)
                        }

                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean {
                        if (!request.isForMainFrame) return false
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
                        val scrollToRestore = pendingRestoreScrollY
                        if (scrollToRestore > 0) {
                            pendingRestoreScrollY = 0
                            view.postDelayed(
                                {
                                    view.scrollTo(0, scrollToRestore)
                                    view.evaluateJavascript("window.scrollTo(0,$scrollToRestore)", null)
                                },
                                350L,
                            )
                        }
                        if (persistSession) {
                            LiveWebSessionState.save(appContext, site, url, view.scrollY)
                        }
                        onPageLoaded?.invoke()
                        onSiteLoaded?.invoke()
                        isLoading = false
                        loadProgress = 100
                    }

                    override fun doUpdateVisitedHistory(
                        view: WebView,
                        url: String?,
                        isReload: Boolean,
                    ) {
                        canGoBack = view.canGoBack()
                        if (persistSession) {
                            LiveWebSessionState.save(appContext, site, url, view.scrollY)
                        }
                    }
                }
            webChromeClient =
                object : WebChromeClient() {
                    override fun onProgressChanged(
                        view: WebView?,
                        newProgress: Int,
                    ) {
                        loadProgress = newProgress
                        if (newProgress >= 100) {
                            isLoading = false
                        } else if (newProgress in 1..99) {
                            isLoading = true
                        }
                    }

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
                                hardenWebSettings(site, config.blockThirdPartyCookies)
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

                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        // Page messages can carry URLs and data from a signed-in session: debug builds only.
                        if (BuildConfig.DEBUG) {
                            consoleMessage?.let {
                                Log.d("CruxWeb", "[${site.name}] ${it.message()} (line ${it.lineNumber()} of ${it.sourceId()})")
                            }
                        }
                        return super.onConsoleMessage(consoleMessage)
                    }

                    override fun onGeolocationPermissionsShowPrompt(
                        origin: String?,
                        callback: GeolocationPermissions.Callback?,
                    ) {
                        callback?.invoke(origin, false, false)
                    }

                    override fun onPermissionRequest(request: PermissionRequest?) {
                        request?.deny()
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
            setOnLongClickListener { view ->
                val callback = onImageLongPress ?: return@setOnLongClickListener false
                val targetWebView = view as? WebView ?: return@setOnLongClickListener false
                val hit = targetWebView.hitTestResult
                when (hit.type) {
                    WebView.HitTestResult.IMAGE_TYPE -> {
                        val src = hit.extra?.takeIf { it.startsWith("http", ignoreCase = true) }
                        if (src != null) {
                            callback(src)
                            true
                        } else {
                            false
                        }
                    }

                    WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                        val src = hit.extra?.takeIf { it.startsWith("http", ignoreCase = true) }
                        if (src != null) {
                            callback(src)
                            true
                        } else {
                            val msg =
                                Message.obtain(
                                    Handler(Looper.getMainLooper()) { result ->
                                        val imgSrc =
                                            result.data
                                                .getString("src")
                                                ?.takeIf { it.startsWith("http", ignoreCase = true) }
                                        if (imgSrc != null) callback(imgSrc)
                                        true
                                    },
                                )
                            targetWebView.requestFocusNodeHref(msg)
                            true
                        }
                    }

                    else -> {
                        false
                    }
                }
            }
            loadUrl(startUrl)
        }

    fun goBack() {
        webView.goBack()
    }

    fun clearInMemoryAndReload() {
        pendingRestoreScrollY = 0
        runCatching {
            webView.clearCache(true)
            webView.clearFormData()
            webView.clearHistory()
        }
        webView.loadUrl(site.homeUrl)
    }

    fun saveSessionState() {
        if (!persistSession) return
        val currentUrl = webView.url
        val nativeY = webView.scrollY.coerceAtLeast(0)
        webView.evaluateJavascript("Math.round(window.scrollY||document.documentElement.scrollTop||0)") { raw ->
            val jsY = raw?.trim()?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            LiveWebSessionState.save(appContext, site, currentUrl, maxOf(nativeY, jsY))
        }
    }

    /** Same as tapping a feed pill again: back to the top, or reload if already there. */
    fun readPostInView(onResult: (LiveWebPost?) -> Unit) {
        webView.evaluateJavascript(READ_POST_SCRIPT) { result -> onResult(parseLiveWebPost(result)) }
    }

    fun scrollToTopOrReload() {
        if (webView.canScrollVertically(-1)) {
            webView.scrollTo(0, 0)
            webView.evaluateJavascript("window.scrollTo({top:0,behavior:'smooth'})", null)
        } else {
            webView.reload()
        }
    }

    fun destroy() {
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.hardenWebSettings(
    site: LiveWebSite,
    blockThirdPartyCookies: Boolean,
) {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.databaseEnabled = true
    settings.allowFileAccess = false
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        settings.safeBrowsingEnabled = true
    }
    val acceptThirdParty = !blockThirdPartyCookies
    runCatching {
        val globalCookieManager = CookieManager.getInstance()
        globalCookieManager.setAcceptCookie(true)
        globalCookieManager.setAcceptThirdPartyCookies(this, acceptThirdParty)
    }
    val cookieManager = site.cookieManager()
    cookieManager.setAcceptCookie(true)
    cookieManager.setAcceptThirdPartyCookies(this, acceptThirdParty)
}

private class HorizontalFriendlySwipeRefreshLayout(
    context: Context,
) : SwipeRefreshLayout(context) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var startX = 0f
    private var isDisallowed = false

    override fun canChildScrollUp(): Boolean {
        val child = getChildAt(0) as? WebView
        return child?.canScrollVertically(-1) ?: super.canChildScrollUp()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.x
                isDisallowed = false
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = abs(ev.x - startX)
                if (dx > touchSlop) {
                    isDisallowed = true
                }
            }
        }
        if (isDisallowed) {
            return false
        }
        return super.onInterceptTouchEvent(ev)
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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
    val isImeVisible = WindowInsets.isImeVisible
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val downloadManager = koinInject<AndroidDownloadManager>()
    var contextImageUrl by remember { mutableStateOf<String?>(null) }
    BindFileChooser(page)
    DisposableEffect(page) {
        page.onImageLongPress = { imageUrl -> contextImageUrl = imageUrl }
        onDispose { page.onImageLongPress = null }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, page) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                    page.saveSessionState()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            page.saveSessionState()
        }
    }
    LaunchedEffect(isCurrentlyVisible, page) {
        if (!isCurrentlyVisible) {
            page.saveSessionState()
        }
    }
    val accessory = LocalBottomBarAccessory.current
    DisposableEffect(accessory, isCurrentlyVisible, site, isImeVisible) {
        if (isCurrentlyVisible && !isImeVisible) {
            accessory?.keepVisibleOwner = site
        } else if (accessory?.keepVisibleOwner == site) {
            accessory.keepVisibleOwner = null
        }
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
                        .padding(
                            if (isImeVisible) {
                                PaddingValues(top = contentPadding.calculateTopPadding())
                            } else {
                                contentPadding
                            },
                        ).imePadding(),
                factory = { viewContext ->
                    val container =
                        if (site.pullToRefresh) {
                            HorizontalFriendlySwipeRefreshLayout(viewContext).apply {
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
            AnimatedVisibility(
                visible = page.isLoading && page.loadError == null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(top = contentPadding.calculateTopPadding()),
            ) {
                LinearProgressIndicator(
                    progress = { (page.loadProgress / 100f).coerceIn(0.05f, 1f) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                )
            }
            val error = page.loadError
            if (error != null) {
                Surface(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(contentPadding),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PlatformBadge(platform = CruxPlatform.forSite(site), size = 56.dp)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.crux_web_error_title, site.label),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.crux_web_error_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(20.dp))
                        FilledTonalButton(onClick = { page.webView.reload() }) {
                            FAIcon(
                                FontAwesomeIcons.Solid.RotateRight,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.crux_web_error_retry))
                        }
                    }
                }
            }
        }
    }
    val imageUrl = contextImageUrl
    if (imageUrl != null) {
        val copyLabel = stringResource(R.string.media_menu_copy_link)
        val shareTitle = stringResource(R.string.media_menu_share_image)
        ModalBottomSheet(
            onDismissRequest = { contextImageUrl = null },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp),
            ) {
                Text(
                    text = imageUrl.toUri().host ?: imageUrl,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.media_menu_save)) },
                    leadingContent = {
                        FAIcon(
                            FontAwesomeIcons.Solid.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    modifier =
                        Modifier.clickable {
                            contextImageUrl = null
                            val fileName = inferWebImageFileName(imageUrl)
                            scope.launch {
                                Toast
                                    .makeText(context, context.getString(R.string.media_download_started), Toast.LENGTH_SHORT)
                                    .show()
                                downloadManager.downloadMedia(
                                    uri = imageUrl,
                                    fileName = fileName,
                                    callback =
                                        object : AndroidDownloadManager.DownloadCallback {
                                            override fun onDownloadSuccess(downloadId: Long) {
                                                scope.launch(Dispatchers.Main) {
                                                    Toast
                                                        .makeText(context, context.getString(R.string.media_save_success), Toast.LENGTH_SHORT)
                                                        .show()
                                                }
                                            }

                                            override fun onDownloadFailed(downloadId: Long) {
                                                scope.launch(Dispatchers.Main) {
                                                    Toast
                                                        .makeText(context, context.getString(R.string.media_save_fail), Toast.LENGTH_SHORT)
                                                        .show()
                                                }
                                            }
                                        },
                                )
                            }
                        },
                )
                ListItem(
                    headlineContent = { Text(shareTitle) },
                    leadingContent = {
                        FAIcon(
                            FontAwesomeIcons.Solid.ShareNodes,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    modifier =
                        Modifier.clickable {
                            contextImageUrl = null
                            val shareIntent =
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, imageUrl)
                                }
                            runCatching {
                                context.startActivity(Intent.createChooser(shareIntent, shareTitle))
                            }
                        },
                )
                ListItem(
                    headlineContent = { Text(copyLabel) },
                    leadingContent = {
                        FAIcon(
                            FontAwesomeIcons.Solid.Copy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    modifier =
                        Modifier.clickable {
                            contextImageUrl = null
                            scope.launch {
                                clipboard.setClipEntry(
                                    ClipEntry(ClipData.newPlainText(copyLabel, imageUrl)),
                                )
                            }
                        },
                )
            }
        }
    }
}

private fun inferWebImageFileName(imageUrl: String): String {
    val uri = runCatching { imageUrl.toUri() }.getOrNull()
    val rawSegment = uri?.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
    val sanitized = rawSegment?.replace(Regex("[^A-Za-z0-9._-]"), "_")?.take(48)
    val formatExt = uri?.getQueryParameter("format")?.lowercase()?.takeIf { it in setOf("jpg", "jpeg", "png", "webp", "gif") }
    val hasImageExt = sanitized?.substringAfterLast('.', "")?.lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif")
    val ext = formatExt ?: if (hasImageExt) null else "jpg"
    val base = sanitized?.takeIf { it.isNotBlank() } ?: "crux_web_${System.currentTimeMillis()}"
    return if (ext != null && !base.endsWith(".$ext", ignoreCase = true)) "$base.$ext" else base
}
