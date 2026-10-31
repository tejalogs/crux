package dev.dimension.flare.ui.screen.home

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
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
import androidx.compose.runtime.getValue
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
import dev.dimension.flare.data.model.tab.TimelineSpecIds
import dev.dimension.flare.data.model.tab.UiTimelineTabItem
import dev.dimension.flare.ui.component.LocalBottomBarAccessory
import java.io.ByteArrayInputStream

// Reddit's trackers and ad servers, read from what the page really loads. Reddit's own CDNs and
// www.google.com stay allowed: posts and reCAPTCHA break without them.
private val REDDIT_BLOCKED_HOSTS =
    setOf(
        "alb.reddit.com",
        "pi.reddit.com",
        "w3-reporting.reddit.com",
        "events.reddit.com",
        "events.redditmedia.com",
        "googletagmanager.com",
        "google-analytics.com",
        "doubleclick.net",
        "googlesyndication.com",
        "rlcdn.com",
        "google.co.in",
    )

// Promoted posts, "get the app" prompts and sign-in nags. display:none only, so the page's own
// scripts keep working.
private const val REDDIT_HIDE_CSS =
    "shreddit-ad-post,shreddit-comments-page-ad,comment-tree-ad," +
        "configured-xpromo-modal,[id^=configured-xpromo],xpromo-app-selector," +
        "a[href*=onelink],a[href*='reddit.app.link'],a[href*='apps.apple.com'],a[href*='play.google.com']," +
        "#login-button,#signup-button,a[href^='/login'],a[href^='/register'],auth-flow-link" +
        "{display:none!important}"

private const val REDDIT_DOCUMENT_START_JS =
    "(function(){var s=document.createElement('style');s.textContent=\"$REDDIT_HIDE_CSS\";" +
        "(document.head||document.documentElement).appendChild(s);})();"

/**
 * A real site shown as a home tab. Instagram and LinkedIn only get hide-only page scripts, chosen
 * by Teja: requests are never blocked there, because that helped get the account flagged. Reddit
 * is used signed out, so it also gets tracker blocking. Cookies never leave the WebView.
 */
internal enum class LiveWebSite(
    val homeUrl: String,
    val tabIdSuffix: String,
    val allowedHosts: Set<String>,
    val blockedHosts: Set<String> = emptySet(),
    val documentStartJs: String? = null,
    val pullToRefresh: Boolean = false,
    val smartReplies: Boolean = true,
) {
    Instagram(
        homeUrl = "https://www.instagram.com/",
        tabIdSuffix = "@instagram.com",
        allowedHosts = setOf("instagram.com", "facebook.com"),
        documentStartJs = INSTAGRAM_PAGE_SCRIPT,
        smartReplies = false,
        pullToRefresh = true,
    ),
    LinkedIn(
        homeUrl = "https://www.linkedin.com/feed/",
        tabIdSuffix = "@linkedin.com",
        allowedHosts = setOf("linkedin.com", "lnkd.in"),
        documentStartJs = LINKEDIN_PAGE_SCRIPT,
    ),
    X(
        homeUrl = "https://x.com/home",
        tabIdSuffix = "",
        allowedHosts = setOf("x.com", "twitter.com"),
    ),
    Reddit(
        // Newest first, not Reddit's ranked "Best".
        homeUrl = "https://www.reddit.com/new/",
        tabIdSuffix = "",
        allowedHosts = setOf("reddit.com", "redd.it"),
        blockedHosts = REDDIT_BLOCKED_HOSTS,
        documentStartJs = REDDIT_DOCUMENT_START_JS,
        pullToRefresh = true,
    ),
    ;

    fun allows(host: String?): Boolean = host.matchesAny(allowedHosts)

    fun blocks(host: String?): Boolean = host.matchesAny(blockedHosts)

    private fun String?.matchesAny(hosts: Set<String>): Boolean = this != null && hosts.any { this == it || endsWith(".$it") }
}

internal fun UiTimelineTabItem.liveWebSite(): LiveWebSite? =
    when {
        id.startsWith("${TimelineSpecIds.WEB_X}:") -> {
            LiveWebSite.X
        }

        id.startsWith("${TimelineSpecIds.WEB_REDDIT}:") -> {
            LiveWebSite.Reddit
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

    fun page(
        site: LiveWebSite,
        context: Context,
    ): LiveWebPage =
        pages.getOrPut(site) {
            clearSharedWebDataOnce(context)
            LiveWebPage(site, context)
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
) {
    var canGoBack by mutableStateOf(false)
        private set
    var onFileChooser: ((ValueCallback<Array<Uri>>) -> Unit)? = null
    var onPageLoaded: (() -> Unit)? = null

    val webView: WebView =
        WebView(context).apply {
            useProfileOf(site)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // Without these WebView ignores the page's mobile viewport, and wide layouts get cropped.
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            site.cookieManager().setAcceptThirdPartyCookies(this, false)
            site.documentStartJs?.let { script ->
                if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    WebViewCompat.addDocumentStartJavaScript(this, script, setOf(site.homeUrl.toUri().let { "${it.scheme}://${it.host}" }))
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
                        if (site.blocks(request.url.host)) {
                            WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                        } else {
                            super.shouldInterceptRequest(view, request)
                        }

                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean {
                        val url = request.url
                        val cleaned = cleanOutgoingLink(url.toString())
                        val isRedirect = cleaned.substringBefore("?") != url.toString().substringBefore("?")
                        if (url.scheme == "https" && site.allows(url.host) && !isRedirect) return false
                        if (url.scheme == "https" || url.scheme == "http") {
                            runCatching { view.context.startActivity(Intent(Intent.ACTION_VIEW, cleaned.toUri())) }
                        }
                        return true
                    }

                    override fun onPageFinished(
                        view: WebView,
                        url: String?,
                    ) {
                        onPageLoaded?.invoke()
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
            loadUrl(site.homeUrl)
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

@Composable
internal fun LiveWebTab(
    site: LiveWebSite,
    isCurrentlyVisible: Boolean,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    val pages = LocalLiveWebPages.current ?: return
    val page = remember(site, pages) { pages.page(site, context) }
    var pendingFiles by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            pendingFiles?.onReceiveValue(uris.toTypedArray())
            pendingFiles = null
        }
    DisposableEffect(page) {
        page.onFileChooser = {
            pendingFiles = it
            fileLauncher.launch("*/*")
        }
        onDispose { page.onFileChooser = null }
    }
    val accessory = LocalBottomBarAccessory.current
    DisposableEffect(accessory, isCurrentlyVisible) {
        if (isCurrentlyVisible) accessory?.keepVisible = true
        onDispose {
            if (isCurrentlyVisible) accessory?.keepVisible = false
        }
    }
    val scrollToTopRegistry = LocalScrollToTopRegistry.current
    DisposableEffect(scrollToTopRegistry, page, isCurrentlyVisible) {
        val callback = page::scrollToTopOrReload
        if (isCurrentlyVisible) scrollToTopRegistry?.registerCallback(callback)
        onDispose { scrollToTopRegistry?.unregisterCallback(callback) }
    }
    // Stops video and audio when another tab is showing.
    LaunchedEffect(page, isCurrentlyVisible) {
        if (isCurrentlyVisible) page.webView.onResume() else page.webView.onPause()
    }
    BackHandler(enabled = isCurrentlyVisible && page.canGoBack) {
        page.goBack()
    }
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
        if (site.smartReplies) {
            SmartReplyButton(
                site = site,
                page = page,
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(contentPadding)
                        .padding(end = 12.dp, bottom = 84.dp),
            )
        }
    }
}
