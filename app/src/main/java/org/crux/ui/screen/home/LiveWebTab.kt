package org.crux.ui.screen.home

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.res.Configuration
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
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
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
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import compose.icons.fontawesomeicons.solid.ArrowUpRightFromSquare
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Bookmark
import compose.icons.fontawesomeicons.solid.Copy
import compose.icons.fontawesomeicons.solid.Download
import compose.icons.fontawesomeicons.solid.RotateRight
import compose.icons.fontawesomeicons.solid.ShareNodes
import compose.icons.fontawesomeicons.solid.Xmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.crux.BuildConfig
import org.crux.R
import org.crux.common.AndroidDownloadManager
import org.crux.data.model.tab.TimelineSpecIds
import org.crux.data.model.tab.UiTimelineTabItem
import org.crux.ui.component.FAIcon
import org.crux.ui.component.LocalBottomBarAccessory
import org.crux.ui.lock.AppLock
import org.crux.ui.screen.crux.CruxPlatform
import org.crux.ui.screen.crux.PlatformBadge
import org.crux.ui.screen.crux.hasSession
import org.crux.ui.screen.media.MediaLandscapeEffect
import org.koin.compose.koinInject
import java.io.ByteArrayInputStream
import java.lang.ref.WeakReference
import java.util.WeakHashMap
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

        id.startsWith("${TimelineSpecIds.WEB_MASTODON}:") -> {
            LiveWebSite.Mastodon
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

    /**
     * Called when a site page's signed-in state changes (and once after its first load), e.g. so
     * a fresh sign-in shows in the side panel. Not on every navigation.
     */
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
        pages[site]
            ?.takeIf { it.config == config }
            ?.also { existing ->
                val currentUrl = existing.webView.url
                if (site.isSignedOutXRoot(currentUrl)) {
                    existing.webView.loadUrl(site.startUrlForSession())
                } else if (site.hasSession() && LiveWebSessionState.isAuthOrLoginUrl(currentUrl)) {
                    existing.webView.loadUrl(site.homeUrl)
                } else if (!site.hasSession() && isExternalOAuthUrl(currentUrl)) {
                    existing.webView.loadUrl(site.startUrlForSession())
                }
            }
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
                    startUrl = saved?.first ?: site.startUrlForSession(),
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
        val wv = pages[site]?.webView ?: return
        if (site.hasSession() && LiveWebSessionState.isAuthOrLoginUrl(wv.url)) {
            wv.loadUrl(site.homeUrl)
        } else {
            wv.reload()
        }
    }

    /** Clears an active site's in-memory cache, form data, and history, then loads its home URL. */
    fun clearAndReload(site: LiveWebSite) {
        pages[site]?.clearInMemoryAndReload()
    }

    /** Evicts a site's page after its WebView renderer process crashed so the tab recreates cleanly. */
    fun evictCrashed(site: LiveWebSite) {
        pages.remove(site)?.destroy()
    }

    /**
     * Pauses JavaScript timers in every WebView of the app while Crux is in the background.
     * WebView's timer pause is process-wide, so one call through any live page covers them all.
     */
    fun pauseAllTimers() {
        pages.values.firstOrNull()?.webView?.let { runCatching { it.pauseTimers() } }
    }

    /** Undoes [pauseAllTimers]; also process-wide. */
    fun resumeAllTimers() {
        pages.values.firstOrNull()?.webView?.let { runCatching { it.resumeTimers() } }
    }

    fun destroy() {
        pages.values.forEach { it.destroy() }
        pages.clear()
    }
}

internal val LocalLiveWebPages = staticCompositionLocalOf<LiveWebPages?> { null }

internal fun blockedTrackerResponse(): WebResourceResponse =
    WebResourceResponse(
        "text/plain",
        "UTF-8",
        204,
        "No Content",
        mapOf(
            "Access-Control-Allow-Origin" to "*",
            "Access-Control-Allow-Methods" to "GET, POST, OPTIONS",
            "Access-Control-Allow-Headers" to "*",
            "Cache-Control" to "no-store",
        ),
        ByteArrayInputStream(ByteArray(0)),
    )

/** Each [LiveWebPage] by its view, held weakly both ways, so a view's host can find its page. */
private val pagesByView = WeakHashMap<WebView, WeakReference<LiveWebPage>>()

private fun registerLiveWebPage(
    webView: WebView,
    page: LiveWebPage,
) {
    synchronized(pagesByView) { pagesByView[webView] = WeakReference(page) }
}

/** The page that owns [webView], or null for a WebView not built by [LiveWebPage]. */
internal fun liveWebPageOf(webView: WebView): LiveWebPage? = synchronized(pagesByView) { pagesByView[webView]?.get() }

@SuppressLint("SetJavaScriptEnabled")
internal class LiveWebPage(
    private val site: LiveWebSite,
    context: Context,
    val startUrl: String = site.startUrlForSession(),
    initialScrollY: Int = 0,
    private val persistSession: Boolean = false,
    val config: LiveWebConfig = LiveWebPrivacyStore.get(context).configFor(site),
) {
    private val appContext = context.applicationContext
    private val privacyCounters = LiveWebPrivacyCounters.get(appContext)
    private val bridgeToken = generateWebBridgeToken()
    private val bookmarkBridgeMsg = bridgeBookmarkMessage(bridgeToken)
    private val ptrDisableBridgeMsg = bridgePtrMessage(allow = false, bridgeToken = bridgeToken)
    private val ptrEnableBridgeMsg = bridgePtrMessage(allow = true, bridgeToken = bridgeToken)
    private val nsfwNoteBridgeMsg = bridgeRedditNsfwNoteMessage(bridgeToken)
    private var pendingRestoreScrollY = initialScrollY.coerceAtLeast(0)

    var canGoBack by mutableStateOf(false)
        private set
    var currentUrl by mutableStateOf(startUrl)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var isLoading by mutableStateOf(true)
        private set
    var hasLoadedOnce by mutableStateOf(false)
        private set
    var hasCommittedVisible by mutableStateOf(false)
        private set
    var loadProgress by mutableIntStateOf(10)
        private set
    var canPullToRefresh by mutableStateOf(true)
        private set

    /** True once the page's touch bridge has reported pull-to-refresh state for this page load. */
    private var ptrBridgeHeard = false

    /** What the pull-to-refresh container asks: the bridge's answer, or "not at top" until it has one. */
    fun mayPullToRefresh(): Boolean =
        liveWebPageAllowsPull(
            bridgeAllows = canPullToRefresh,
            bridgeHeard = ptrBridgeHeard,
            scrollsInnerContainer = site.feedScrollsInnerContainer,
        )

    /** The Reddit page whose 18+ media Reddit withheld, while Crux's note about it is up. */
    var redditNsfwNoteUrl by mutableStateOf<String?>(null)
        private set
    private val redditNsfwNoteShownKeys = mutableSetOf<String>()

    /** A note asked for while the App Lock was up; shown on unlock if the page is still that post. */
    private var pendingRedditNsfwNoteUrl: String? = null

    fun dismissRedditNsfwNote() {
        redditNsfwNoteUrl = null
    }

    private fun onRedditNsfwNoteRequested() {
        if (site != LiveWebSite.Reddit) return
        val url = webView.url ?: currentUrl
        val key = redditNsfwNoteKey(url)
        when (redditNsfwNoteStep(key, alreadyShown = key in redditNsfwNoteShownKeys, unlocked = AppLock.isUnlocked)) {
            RedditNsfwNoteStep.Show -> showRedditNsfwNote(url, key)
            // Page script can post this too: never open native UI under the App Lock, keep it for later.
            RedditNsfwNoteStep.Defer -> pendingRedditNsfwNoteUrl = url
            RedditNsfwNoteStep.Ignore -> Unit
        }
    }

    /** Called once the App Lock opens: shows a note that was asked for behind it, if still relevant. */
    fun promotePendingRedditNsfwNote() {
        val pending = pendingRedditNsfwNoteUrl ?: return
        pendingRedditNsfwNoteUrl = null
        if (site != LiveWebSite.Reddit || !AppLock.isUnlocked) return
        val key = redditNsfwNoteKey(pending)
        if (key == null || key != redditNsfwNoteKey(webView.url ?: currentUrl)) return
        if (key in redditNsfwNoteShownKeys) return
        showRedditNsfwNote(pending, key)
    }

    private fun showRedditNsfwNote(
        url: String,
        key: String?,
    ) {
        if (key == null || !redditNsfwNoteShownKeys.add(key)) return
        redditNsfwNoteUrl = url
    }
    var customVideoView by mutableStateOf<View?>(null)
        private set
    var rendererCrashed by mutableStateOf(false)
        private set
    private var customVideoCallback: WebChromeClient.CustomViewCallback? = null
    var onFileChooser: ((ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams) -> Unit)? = null
    var onImageLongPress: ((String) -> Unit)? = null
    var onNativeBookmarkTap: (() -> Unit)? = null
    var onPageLoaded: (() -> Unit)? = null

    /** Called when this site's signed-in state changes (and once after the first load). */
    var onSiteLoaded: (() -> Unit)? = null

    /** The signed-in state last reported through [onSiteLoaded]; null until the first check. */
    private var lastSessionState: Boolean? = null

    private val unblurRedditNsfw = site == LiveWebSite.Reddit && SiteTweak.RedditUnblurNsfw in config.tweaks

    /**
     * Tells the home screen about a sign-in or sign-out, and only then: refreshing every site's
     * connection state reads all their cookie jars, which is too much for each in-page navigation.
     */
    private fun notifyIfSessionChanged() {
        val signedIn = site.hasSession()
        if (lastSessionState == signedIn) return
        val firstCheck = lastSessionState == null
        lastSessionState = signedIn
        // A fresh sign-in (or sign-out) reaches disk now rather than at the next pause.
        if (!firstCheck) runCatching { site.cookieManager().flush() }
        onSiteLoaded?.invoke()
    }

    private val over18Prefs = redditOver18Prefs(context)

    /**
     * With the Unblur tweak on, tells Reddit a signed-out reader has already confirmed their age,
     * so mature previews load without the blur wall. Never on a signed-in account: there Reddit's
     * own account setting decides, and Crux does not change it.
     *
     * When the tweak is off or the reader is signed in, the cookie Crux itself set earlier is taken
     * back (1.0.12 set it by default for a year). Crux only removes a cookie it may have set, so a
     * reader's own answer to Reddit's age gate stays once Crux has stopped setting it.
     */
    private fun setRedditOver18Cookie() {
        if (site != LiveWebSite.Reddit) return
        val maybeSetByCrux = redditOver18MaybeSetByCrux(over18Prefs)
        val action =
            redditOver18CookieAction(
                tweakOn = unblurRedditNsfw,
                signedIn = site.hasSession(),
                maybeSetByCrux = maybeSetByCrux,
            )
        val cookie =
            when (action) {
                RedditOver18CookieAction.Set -> REDDIT_OVER18_COOKIE
                RedditOver18CookieAction.Expire -> REDDIT_OVER18_EXPIRED_COOKIE
                RedditOver18CookieAction.None -> return
            }
        runCatching {
            val cm = site.cookieManager()
            REDDIT_OVER18_ORIGINS.forEach { cm.setCookie(it, cookie) }
            val setNow = action == RedditOver18CookieAction.Set
            if (setNow != maybeSetByCrux || !over18Prefs.contains(REDDIT_OVER18_SET_BY_CRUX)) {
                over18Prefs.edit { putBoolean(REDDIT_OVER18_SET_BY_CRUX, setNow) }
            }
        }
    }

    fun exitFullscreenVideo() {
        val cb = customVideoCallback
        customVideoCallback = null
        customVideoView = null
        runCatching { cb?.onCustomViewHidden() }
    }

    val webView: WebView =
        createThemeSafeWebView(context).apply {
            useProfileOf(site)
            setBackgroundColor(
                if (context.currentUiNightMask() == Configuration.UI_MODE_NIGHT_YES) {
                    android.graphics.Color.BLACK
                } else {
                    android.graphics.Color.TRANSPARENT
                },
            )
            // No forced hardware layer: WebView already composites on the GPU, and an extra layer
            // re-rasterises the whole page each frame, which made Reels and video scrolling stutter.
            isNestedScrollingEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            // Heavy virtualized/shadow-DOM feeds (Reddit, X) churn GPU tile memory if offscreen tiles are pre-rastered.
            if (site != LiveWebSite.Reddit && site != LiveWebSite.X && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                settings.offscreenPreRaster = true
            }
            // The WebView's real identity: a spoofed one disagrees with its other signals, and that
            // mismatch is a common automation tell (it was part of the Instagram flag in Oct 2026).
            // Without these WebView ignores the page's mobile viewport, and wide layouts get cropped.
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.displayZoomControls = false
            settings.builtInZoomControls = false
            settings.setSupportMultipleWindows(true)
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.mediaPlaybackRequiresUserGesture = config.blockAutoplay
            hardenWebSettings(site, config.blockThirdPartyCookies)
            // Before the first request, so the first page already loads without the blur wall.
            setRedditOver18Cookie()
            val documentStartScripts = config.documentStartScripts(site, bridgeToken)
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                documentStartScripts.forEach { script ->
                    WebViewCompat.addDocumentStartJavaScript(this, script, site.documentStartOriginRules)
                }
            }
            configureWebViewHeadersAndDarkening(settings)
            attachSafeDownloadListener(this)
            webViewClient =
                object : WebViewClient() {
                    override fun onRenderProcessGone(
                        view: WebView,
                        detail: RenderProcessGoneDetail,
                    ): Boolean {
                        exitFullscreenVideo()
                        rendererCrashed = true
                        return true
                    }

                    override fun onPageStarted(
                        view: WebView,
                        url: String?,
                        favicon: Bitmap?,
                    ) {
                        super.onPageStarted(view, url, favicon)
                        if (site.isSignedOutXRoot(url)) {
                            view.post { view.loadUrl(site.startUrlForSession()) }
                            return
                        }
                        setRedditOver18Cookie()
                        if (url != null && url != "about:blank") {
                            currentUrl = url
                            loadError = null
                            isLoading = true
                            loadProgress = 10
                            canPullToRefresh = true
                            ptrBridgeHeard = false
                            if (redditNsfwNoteKey(redditNsfwNoteUrl) != redditNsfwNoteKey(url)) redditNsfwNoteUrl = null
                            canGoBack = computeWebViewCanGoBack(view.canGoBack(), url)
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
                        // Runs on WebView's network thread: a map lookup and a counter, no locks.
                        siteRequestResponse(site, config, request.url.host, privacyCounters)
                            ?: super.shouldInterceptRequest(view, request)

                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean = handleMainFrameUrlOverride(view, request, site, config)

                    override fun onPageCommitVisible(
                        view: WebView?,
                        url: String?,
                    ) {
                        super.onPageCommitVisible(view, url)
                        if (url != null && url != "about:blank") {
                            hasCommittedVisible = true
                            hasLoadedOnce = true
                        }
                    }

                    override fun onPageFinished(
                        view: WebView,
                        url: String?,
                    ) {
                        // Pausing the page already flushes cookies; flush here only where a sign-in
                        // may just have written them.
                        if (LiveWebSessionState.isAuthOrLoginUrl(url)) site.cookieManager().flush()
                        if (site.isSignedOutXRoot(url)) {
                            view.post { view.loadUrl(site.startUrlForSession()) }
                            return
                        }
                        // Fallback for WebViews that cannot inject at document start. Scope to site's own origins.
                        if (
                            !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) &&
                            !isExternalOAuthUrl(url) &&
                            site.allows(runCatching { java.net.URI(url.orEmpty()).host }.getOrNull())
                        ) {
                            config.documentStartScripts(site, bridgeToken).forEach { view.evaluateJavascript(it, null) }
                        }
                        val scrollToRestore = pendingRestoreScrollY
                        if (scrollToRestore > 0) {
                            pendingRestoreScrollY = 0
                            val restoreScrollJs =
                                if (site.feedScrollsInnerContainer) {
                                    "(function(){window.scrollTo(0,$scrollToRestore);var m=document.querySelector('main#workspace,main,[role=\"main\"]');if(m)m.scrollTop=$scrollToRestore;})()"
                                } else {
                                    "window.scrollTo(0,$scrollToRestore)"
                                }
                            view.postDelayed(
                                {
                                    view.scrollTo(0, scrollToRestore)
                                    view.evaluateJavascript(restoreScrollJs, null)
                                },
                                350L,
                            )
                        }
                        (url ?: view.url)?.takeIf { it.isNotBlank() && it != "about:blank" }?.let { currentUrl = it }
                        canGoBack = computeWebViewCanGoBack(view.canGoBack(), url ?: view.url)
                        // The bridge only reports changes, so its last word must not be overwritten.
                        if (!ptrBridgeHeard) canPullToRefresh = true
                        onPageLoaded?.invoke()
                        notifyIfSessionChanged()
                        hasLoadedOnce = true
                        isLoading = false
                        loadProgress = 100
                    }

                    override fun doUpdateVisitedHistory(
                        view: WebView,
                        url: String?,
                        isReload: Boolean,
                    ) {
                        // Runs on every in-page navigation (each post, Reel or Story), so no cookie
                        // flush and no tab-position write here: those happen when the page pauses.
                        if (site.isSignedOutXRoot(url)) {
                            view.post { view.loadUrl(site.startUrlForSession()) }
                            return
                        }
                        (url ?: view.url)?.takeIf { it.isNotBlank() && it != "about:blank" }?.let { currentUrl = it }
                        canGoBack = computeWebViewCanGoBack(view.canGoBack(), url ?: view.url)
                        if (redditNsfwNoteKey(redditNsfwNoteUrl) != redditNsfwNoteKey(url ?: view.url)) redditNsfwNoteUrl = null
                        notifyIfSessionChanged()
                    }
                }
            webChromeClient =
                object : WebChromeClient() {
                    private val emptyPoster: Bitmap by lazy {
                        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                    }

                    override fun getDefaultVideoPoster(): Bitmap = emptyPoster

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
                    ): Boolean = handleCreateWindow(view, site, config, isUserGesture, resultMsg)

                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        when (consoleMessage?.message()) {
                            bookmarkBridgeMsg -> {
                                // Page script can post this too: never open native UI under the App Lock.
                                this@LiveWebPage.webView.post {
                                    if (AppLock.isUnlocked) onNativeBookmarkTap?.invoke()
                                }
                                return true
                            }

                            ptrDisableBridgeMsg -> {
                                ptrBridgeHeard = true
                                canPullToRefresh = false
                                return true
                            }

                            ptrEnableBridgeMsg -> {
                                ptrBridgeHeard = true
                                canPullToRefresh = true
                                return true
                            }

                            nsfwNoteBridgeMsg -> {
                                this@LiveWebPage.webView.post { onRedditNsfwNoteRequested() }
                                return true
                            }
                        }
                        // Page messages can carry URLs and data from a signed-in session: debug builds only.
                        if (BuildConfig.DEBUG) {
                            consoleMessage?.let {
                                Log.d("CruxWeb", "[${site.name}] ${it.message()} (line ${it.lineNumber()} of ${it.sourceId()})")
                            }
                        }
                        return super.onConsoleMessage(consoleMessage)
                    }

                    // Page alerts and prompts are system dialogs in their own window: while App Lock
                    // is on they would draw above the cover (a fake PIN prompt), so cancel them.
                    override fun onJsAlert(
                        view: WebView?,
                        url: String?,
                        message: String?,
                        result: JsResult?,
                    ): Boolean = cancelWhileLocked(result) || super.onJsAlert(view, url, message, result)

                    override fun onJsConfirm(
                        view: WebView?,
                        url: String?,
                        message: String?,
                        result: JsResult?,
                    ): Boolean = cancelWhileLocked(result) || super.onJsConfirm(view, url, message, result)

                    override fun onJsPrompt(
                        view: WebView?,
                        url: String?,
                        message: String?,
                        defaultValue: String?,
                        result: JsPromptResult?,
                    ): Boolean = cancelWhileLocked(result) || super.onJsPrompt(view, url, message, defaultValue, result)

                    override fun onJsBeforeUnload(
                        view: WebView?,
                        url: String?,
                        message: String?,
                        result: JsResult?,
                    ): Boolean = cancelWhileLocked(result) || super.onJsBeforeUnload(view, url, message, result)

                    override fun onShowCustomView(
                        view: View?,
                        callback: CustomViewCallback?,
                    ) {
                        if (view == null) {
                            callback?.onCustomViewHidden()
                            return
                        }
                        if (customVideoView != null) {
                            callback?.onCustomViewHidden()
                            return
                        }
                        customVideoView = view
                        customVideoCallback = callback
                    }

                    override fun onHideCustomView() {
                        exitFullscreenVideo()
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
                        chooser(filePathCallback, fileChooserParams)
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

    init {
        registerLiveWebPage(webView, this)
    }

    fun goBack() {
        navigateWebViewBackOrEscapeOAuth(webView, site)
    }

    fun clearInMemoryAndReload() {
        pendingRestoreScrollY = 0
        hasCommittedVisible = false
        runCatching {
            webView.clearCache(true)
            webView.clearFormData()
            webView.clearHistory()
        }
        webView.loadUrl(site.startUrlForSession())
    }

    /**
     * Remembers the tab's page and place. Called when the page pauses or is hidden, never on each
     * navigation, so the offset saved is where the person actually stopped.
     */
    fun saveSessionState() {
        if (!persistSession) return
        val currentUrl = webView.url
        val nativeY = webView.scrollY.coerceAtLeast(0)
        // Saved at once with the view's own offset, since the page may be destroyed before the
        // script below answers; the page's scroll position then refines it.
        LiveWebSessionState.save(appContext, site, currentUrl, nativeY)
        val readScrollJs =
            if (site.feedScrollsInnerContainer) {
                "(function(){var m=document.querySelector('main#workspace,main,[role=\"main\"]');return Math.round(Math.max(window.scrollY||document.documentElement.scrollTop||0,(m&&m.scrollTop)||0));})()"
            } else {
                "Math.round(window.scrollY||document.documentElement.scrollTop||0)"
            }
        webView.evaluateJavascript(readScrollJs) { raw ->
            val jsY = raw?.trim()?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            if (jsY > nativeY) LiveWebSessionState.save(appContext, site, currentUrl, jsY)
        }
    }

    /** Same as tapping a feed pill again: back to the top, or reload if already there. */
    fun readPostInView(onResult: (LiveWebPost?) -> Unit) {
        // Only read posts from the site itself, never from a page it navigated to.
        val host = webView.url?.let { runCatching { java.net.URI(it).host }.getOrNull() }
        if (!site.allows(host)) {
            onResult(null)
            return
        }
        webView.evaluateJavascript(READ_POST_SCRIPT) { result -> onResult(parseLiveWebPost(result, site)) }
    }

    fun scrollToTopOrReload() {
        val nativeScrolled = webView.scrollY > 24 || webView.canScrollVertically(-1)
        webView.evaluateJavascript(
            """
            (function() {
                var winY = window.scrollY || window.pageYOffset || document.documentElement.scrollTop || document.body.scrollTop || 0;
                var scrolled = winY > 24;
                if (winY > 0) {
                    try { window.scrollTo({ top: 0, behavior: 'smooth' }); } catch (e) { window.scrollTo(0, 0); }
                }
                var containers = document.querySelectorAll('main, [role="main"], [data-testid="primaryColumn"], section');
                for (var i = 0; i < containers.length; i++) {
                    var el = containers[i];
                    if (el && el.scrollTop > 24) {
                        scrolled = true;
                        try { el.scrollTo({ top: 0, behavior: 'smooth' }); } catch (e) { el.scrollTop = 0; }
                    }
                }
                return scrolled;
            })()
            """.trimIndent(),
        ) { result ->
            val jsScrolled = result?.trim() == "true"
            if (nativeScrolled || jsScrolled) {
                webView.postDelayed(
                    {
                        if (webView.scrollY > 0) {
                            webView.scrollTo(0, 0)
                        }
                    },
                    420L,
                )
            } else if (!isAtSiteHomeUrl(site, webView.url)) {
                webView.loadUrl(site.homeUrl)
            } else {
                webView.reload()
            }
        }
    }

    fun destroy() {
        exitFullscreenVideo()
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
    settings.allowContentAccess = false
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

private fun isAuthUrl(url: String?): Boolean = LiveWebSessionState.isAuthOrLoginUrl(url)

/** Cancels a page's JS dialog while App Lock is covering the app; true means it was handled. */
private fun cancelWhileLocked(result: JsResult?): Boolean {
    if (AppLock.isUnlocked) return false
    result?.cancel()
    return true
}

/**
 * Sites whose signed-in feed scrolls an inner element (LinkedIn's `main#workspace`) rather than the
 * document, so the WebView's own scroll offset stays 0 mid-feed and says nothing about the top.
 */
internal val LiveWebSite.feedScrollsInnerContainer: Boolean
    get() = this == LiveWebSite.LinkedIn

/**
 * Whether the page lets a downward drag at the top become a refresh. Until the page's touch bridge
 * has spoken for this page, a site that scrolls an inner container is treated as not at the top,
 * since nothing native can tell.
 */
internal fun liveWebPageAllowsPull(
    bridgeAllows: Boolean,
    bridgeHeard: Boolean,
    scrollsInnerContainer: Boolean,
): Boolean = bridgeAllows && (bridgeHeard || !scrollsInnerContainer)

/**
 * One touch gesture as the pull-to-refresh container sees it. [downAtTop] is latched at ACTION_DOWN,
 * so a drag that starts mid-feed can never turn into a refresh, even when the page reports "at top"
 * late in the gesture. A mostly sideways drag past the touch slop never refreshes either.
 */
internal data class PullToRefreshGesture(
    val startX: Float = 0f,
    val startY: Float = 0f,
    val downAtTop: Boolean = false,
    val horizontal: Boolean = false,
) {
    val mayIntercept: Boolean
        get() = downAtTop && !horizontal

    fun moved(
        x: Float,
        y: Float,
        touchSlop: Float,
    ): PullToRefreshGesture {
        if (horizontal) return this
        val dx = abs(x - startX)
        val dy = abs(y - startY)
        return if (dx > touchSlop && dx > dy) copy(horizontal = true) else this
    }

    companion object {
        fun down(
            x: Float,
            y: Float,
            atTop: Boolean,
        ): PullToRefreshGesture = PullToRefreshGesture(startX = x, startY = y, downAtTop = atTop)
    }
}

private class HorizontalFriendlySwipeRefreshLayout(
    context: Context,
) : SwipeRefreshLayout(context) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val hideThresholdPx = 38f * context.resources.displayMetrics.density
    private val showThresholdPx = 22f * context.resources.displayMetrics.density
    private var gesture = PullToRefreshGesture()
    private var lastTrackX = 0f
    private var lastTrackY = 0f
    private var accumulatedDy = 0f
    private var lastReportedShow: Boolean? = null
    var canPullToRefreshProvider: () -> Boolean = { true }
    var onScrollDirectionChanged: ((Boolean) -> Unit)? = null

    override fun canChildScrollUp(): Boolean {
        val child = getChildAt(0) as? WebView ?: return super.canChildScrollUp()
        // Full-screen viewers, open modals, and inner-scrolled containers must not trigger reload.
        return !canPullToRefreshProvider() || isPrivateOrImmersiveUrl(child.url) || child.scrollY > 0 || child.canScrollVertically(-1)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val scrollCallback = onScrollDirectionChanged
        if (scrollCallback != null) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastTrackX = ev.x
                    lastTrackY = ev.y
                    accumulatedDy = 0f
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.x - lastTrackX
                    val dy = ev.y - lastTrackY
                    lastTrackX = ev.x
                    lastTrackY = ev.y
                    if (abs(dy) > abs(dx)) {
                        if ((accumulatedDy > 0f && dy < 0f) || (accumulatedDy < 0f && dy > 0f)) {
                            accumulatedDy = 0f
                        }
                        accumulatedDy += dy
                        if (accumulatedDy < -hideThresholdPx) {
                            accumulatedDy = 0f
                            if (lastReportedShow != false) {
                                val child = getChildAt(0) as? WebView
                                if (!isAuthUrl(child?.url)) {
                                    lastReportedShow = false
                                    scrollCallback.invoke(false)
                                }
                            }
                        } else if (accumulatedDy > showThresholdPx) {
                            accumulatedDy = 0f
                            if (lastReportedShow != true) {
                                lastReportedShow = true
                                scrollCallback.invoke(true)
                            }
                        }
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gesture = PullToRefreshGesture.down(ev.x, ev.y, atTop = !canChildScrollUp())
                // The base class resets its drag state on DOWN, so it always sees this one.
                return super.onInterceptTouchEvent(ev)
            }

            MotionEvent.ACTION_MOVE -> gesture = gesture.moved(ev.x, ev.y, touchSlop)
        }
        if (!gesture.mayIntercept) return false
        return super.onInterceptTouchEvent(ev)
    }
}

/** Lets the page's own upload buttons open the phone's file picker, respecting MIME types and single/multi-select. */
@Composable
internal fun BindFileChooser(page: LiveWebPage) {
    var pendingFiles by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uris = parseFileChooserActivityResult(result.resultCode, result.data)
            pendingFiles?.onReceiveValue(uris)
            pendingFiles = null
        }
    DisposableEffect(page) {
        page.onFileChooser = { callback, params ->
            pendingFiles?.onReceiveValue(null)
            pendingFiles = callback
            val intent = buildFileChooserIntent(params)
            val launched = runCatching { fileLauncher.launch(intent) }.isSuccess
            if (!launched) {
                pendingFiles?.onReceiveValue(null)
                pendingFiles = null
            }
        }
        onDispose {
            page.onFileChooser = null
            pendingFiles?.onReceiveValue(null)
            pendingFiles = null
        }
    }
}

internal fun buildFileChooserIntent(params: WebChromeClient.FileChooserParams?): Intent {
    val accept = params?.acceptTypes?.filter { it.isNotBlank() }.orEmpty()
    val allowMultiple = params?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE
    return Intent(Intent.ACTION_GET_CONTENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = if (accept.size == 1 && accept[0].contains("/")) accept[0] else "*/*"
        if (accept.size > 1 && accept.all { it.contains("/") }) {
            putExtra(Intent.EXTRA_MIME_TYPES, accept.toTypedArray())
        }
        if (allowMultiple) {
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
    }
}

internal fun parseFileChooserActivityResult(
    resultCode: Int,
    data: Intent?,
): Array<Uri>? {
    if (resultCode != Activity.RESULT_OK || data == null) return null
    val clip = data.clipData
    if (clip != null && clip.itemCount > 0) {
        return (0 until clip.itemCount).mapNotNull { clip.getItemAt(it)?.uri }.toTypedArray()
    }
    val single = data.data
    if (single != null) return arrayOf(single)
    return WebChromeClient.FileChooserParams.parseResult(resultCode, data)
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
    var crashGeneration by remember(site) { mutableIntStateOf(0) }
    val page = remember(site, pages, homeUrl, config, crashGeneration) { pages.page(site, context, config) }
    LiveWebDockImmersiveReporter(owner = site, isVisible = isCurrentlyVisible, url = page.currentUrl)
    LaunchedEffect(page.rendererCrashed) {
        if (page.rendererCrashed) {
            pages.evictCrashed(site)
            crashGeneration += 1
        }
    }
    val isImeVisible = WindowInsets.isImeVisible
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val downloadManager = koinInject<AndroidDownloadManager>()
    var contextImageUrl by remember { mutableStateOf<String?>(null) }
    BindFileChooser(page)
    DisposableEffect(page) {
        page.onImageLongPress = { imageUrl -> contextImageUrl = upgradeXImageUrl(imageUrl) }
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
    val accessory = LocalBottomBarAccessory.current
    val isFullscreenVideo = page.customVideoView != null
    LaunchedEffect(isCurrentlyVisible, page, isFullscreenVideo) {
        if (isCurrentlyVisible) {
            accessory?.webDockExpanded = !isFullscreenVideo
        } else {
            page.saveSessionState()
        }
    }
    DisposableEffect(accessory, isCurrentlyVisible, site, isImeVisible, isFullscreenVideo) {
        if (isCurrentlyVisible && !isImeVisible && !isFullscreenVideo) {
            accessory?.keepVisibleOwner = site
            accessory?.webDockExpanded = true
        } else if (accessory?.keepVisibleOwner == site) {
            accessory.keepVisibleOwner = null
            accessory.webDockExpanded = !isFullscreenVideo
        }
        onDispose {
            if (accessory?.keepVisibleOwner == site) {
                accessory.keepVisibleOwner = null
                accessory.webDockExpanded = true
            }
        }
    }
    val smartReply = rememberSmartReplyAction(site, page)
    var pendingBookmarkPost by remember { mutableStateOf<ExtractedSocialPost?>(null) }
    val bookmarkAction =
        remember(site, page) {
            {
                extractPostFromWebView(site, page.webView) { extracted ->
                    // The extraction is async: drop a result that lands after the app re-locked.
                    if (AppLock.isUnlocked) pendingBookmarkPost = extracted
                }
            }
        }
    DisposableEffect(accessory, isCurrentlyVisible, smartReply, bookmarkAction, page) {
        if (isCurrentlyVisible) {
            accessory?.bookmarkPost = bookmarkAction
            if (site.smartReplies) accessory?.smartReply = smartReply
            page.onNativeBookmarkTap = bookmarkAction
        }
        onDispose {
            if (accessory?.bookmarkPost === bookmarkAction) accessory.bookmarkPost = null
            if (accessory?.smartReply === smartReply) accessory.smartReply = null
            if (page.onNativeBookmarkTap === bookmarkAction) page.onNativeBookmarkTap = null
        }
    }
    pendingBookmarkPost?.let { postToSave ->
        SaveToBookmarkFolderSheet(
            post = postToSave,
            onDismiss = { pendingBookmarkPost = null },
            onSmartReply = if (site.smartReplies) smartReply else null,
        )
    }
    val scrollToTopRegistry = LocalScrollToTopRegistry.current
    DisposableEffect(scrollToTopRegistry, page, isCurrentlyVisible, accessory) {
        val callback = {
            accessory?.webDockExpanded = true
            page.scrollToTopOrReload()
        }
        if (isCurrentlyVisible) scrollToTopRegistry?.registerCallback(callback)
        onDispose { scrollToTopRegistry?.unregisterCallback(callback) }
    }
    // Automatically freezes background WebView renderers and media when hidden or minimized,
    // and while App Lock is covering the app; resumes after unlock.
    // Also runs this tab's private-screen guard, which stays on behind App Lock (isCurrentlyVisible).
    WebSessionLifecycleSuspender(
        webView = page.webView,
        isVisible = isCurrentlyVisible && AppLock.isUnlocked,
        privateScreenVisible = isCurrentlyVisible,
    )
    BackHandler(enabled = isCurrentlyVisible && (page.customVideoView != null || page.canGoBack)) {
        if (page.customVideoView != null) {
            page.exitFullscreenVideo()
        } else {
            page.goBack()
        }
    }
    val webBackgroundColor = MaterialTheme.colorScheme.background.toArgb()
    LaunchedEffect(page, webBackgroundColor) {
        page.webView.setBackgroundColor(webBackgroundColor)
    }
    val refreshIndicatorColor = MaterialTheme.colorScheme.primary.toArgb()
    val refreshBackgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh.toArgb()
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
                            HorizontalFriendlySwipeRefreshLayout(viewContext).apply {
                                canPullToRefreshProvider = { page.mayPullToRefresh() }
                                onScrollDirectionChanged = { show ->
                                    if (page.customVideoView == null) {
                                        accessory?.webDockExpanded = show
                                    }
                                }
                                setColorSchemeColors(refreshIndicatorColor)
                                setProgressBackgroundColorSchemeColor(refreshBackgroundColor)
                                setOnRefreshListener {
                                    accessory?.webDockExpanded = true
                                    page.webView.reload()
                                }
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
                    (it as? HorizontalFriendlySwipeRefreshLayout)?.onScrollDirectionChanged = null
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
            AnimatedVisibility(
                visible = !page.hasLoadedOnce && !page.hasCommittedVisible && page.isLoading && page.loadProgress < 50 && page.loadError == null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(top = contentPadding.calculateTopPadding()),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PlatformBadge(platform = CruxPlatform.forSite(site), size = 56.dp)
                        Spacer(Modifier.height(16.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.live_web_opening_site, site.label),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FilledTonalButton(onClick = { page.webView.reload() }) {
                                FAIcon(
                                    FontAwesomeIcons.Solid.RotateRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.crux_web_error_retry))
                            }
                            OutlinedButton(onClick = {
                                openInSystemBrowser(context, page.currentUrl)
                            }) {
                                FAIcon(
                                    FontAwesomeIcons.Solid.ArrowUpRightFromSquare,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.web_open_in_browser))
                            }
                        }
                    }
                }
            }
            if (site == LiveWebSite.Reddit) {
                LaunchedEffect(page, AppLock.isUnlocked) {
                    if (AppLock.isUnlocked) page.promotePendingRedditNsfwNote()
                }
            }
            if (
                site == LiveWebSite.Reddit &&
                isCurrentlyVisible &&
                AppLock.isUnlocked &&
                page.customVideoView == null &&
                page.loadError == null
            ) {
                RedditNsfwNote(
                    page = page,
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = contentPadding.calculateBottomPadding()),
                )
            }
            FullscreenWebVideoOverlay(page = page)
        }
    }
    val imageUrl = contextImageUrl
    if (imageUrl != null) {
        val copyLabel = stringResource(R.string.media_menu_copy_link)
        val shareTitle = stringResource(R.string.media_menu_share_image)
        val downloadStartedMsg = stringResource(R.string.media_download_started)
        val saveSuccessMsg = stringResource(R.string.media_save_success)
        val saveFailMsg = stringResource(R.string.media_save_fail)
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
                    headlineContent = { Text(stringResource(R.string.live_web_save_to_reading_list)) },
                    leadingContent = {
                        FAIcon(
                            FontAwesomeIcons.Solid.Bookmark,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    modifier =
                        Modifier.clickable {
                            val targetImg = imageUrl
                            contextImageUrl = null
                            extractPostFromWebView(site, page.webView, overrideImageUrl = targetImg) { extracted ->
                                if (AppLock.isUnlocked) pendingBookmarkPost = extracted
                            }
                        },
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
                                    .makeText(context, downloadStartedMsg, Toast.LENGTH_SHORT)
                                    .show()
                                downloadManager.downloadMedia(
                                    uri = imageUrl,
                                    fileName = fileName,
                                    callback =
                                        object : AndroidDownloadManager.DownloadCallback {
                                            override fun onDownloadSuccess(downloadId: Long) {
                                                scope.launch(Dispatchers.Main) {
                                                    Toast
                                                        .makeText(context, saveSuccessMsg, Toast.LENGTH_SHORT)
                                                        .show()
                                                }
                                            }

                                            override fun onDownloadFailed(downloadId: Long) {
                                                scope.launch(Dispatchers.Main) {
                                                    Toast
                                                        .makeText(context, saveFailMsg, Toast.LENGTH_SHORT)
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

@Composable
internal fun FullscreenWebVideoOverlay(page: LiveWebPage) {
    FullscreenCustomVideoOverlay(
        customView = page.customVideoView,
        onExit = page::exitFullscreenVideo,
    )
}

@Composable
internal fun FullscreenCustomVideoOverlay(
    customView: View?,
    onExit: () -> Unit,
) {
    if (customView == null) return
    FullscreenVideoWindowEffect()
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                FrameLayout(viewContext).apply {
                    (customView.parent as? ViewGroup)?.removeView(customView)
                    addView(
                        customView,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        ),
                    )
                }
            },
            onRelease = { it.removeAllViews() },
        )
        androidx.compose.material3.IconButton(
            onClick = onExit,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .displayCutoutPadding()
                    .padding(16.dp),
        ) {
            FAIcon(
                imageVector = FontAwesomeIcons.Solid.Xmark,
                contentDescription = stringResource(R.string.catch_up_close),
                tint = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * While a page's video is full screen: hides the status and navigation bars (a swipe from the
 * edge shows them for a moment) and lets the screen rotate with the phone while system
 * auto-rotate is on (SCREEN_ORIENTATION_FULL_USER), so a rotation lock is respected. The bars and
 * the orientation are put back as they were when the video leaves full screen.
 */
@Composable
private fun FullscreenVideoWindowEffect() {
    val activity = LocalActivity.current
    val view = LocalView.current
    DisposableEffect(activity, view) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousBehavior = controller?.systemBarsBehavior
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            if (controller != null) {
                controller.show(WindowInsetsCompat.Type.systemBars())
                if (previousBehavior != null) controller.systemBarsBehavior = previousBehavior
            }
        }
    }
    var originalOrientation by remember { mutableStateOf<Int?>(null) }
    MediaLandscapeEffect(
        enabled = true,
        originalOrientation = originalOrientation,
        setOriginalOrientation = { originalOrientation = it },
        // Rotates with the phone rather than forcing landscape, so a portrait video stays upright,
        // and only while system auto-rotate is on, so a rotation lock is respected.
        orientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER,
    )
}

internal sealed interface MainFrameUrlAction {
    data object AllowInWebView : MainFrameUrlAction

    data class LoadInWebView(
        val url: String,
    ) : MainFrameUrlAction

    data class OpenOutside(
        val url: String,
    ) : MainFrameUrlAction

    data class OpenDialOrMail(
        val url: String,
    ) : MainFrameUrlAction

    data object Block : MainFrameUrlAction
}

internal fun decideMainFrameUrlAction(
    rawUrl: String,
    site: LiveWebSite,
    cleanLinks: Boolean,
    hasGesture: Boolean,
    isForMainFrame: Boolean = true,
): MainFrameUrlAction {
    if (!isForMainFrame) return MainFrameUrlAction.AllowInWebView
    val trimmed = rawUrl.trim()
    if (trimmed.isEmpty()) return MainFrameUrlAction.Block
    if (site.isSignedOutXRoot(trimmed)) {
        return MainFrameUrlAction.LoadInWebView(site.startUrlForSession())
    }
    val uri = runCatching { java.net.URI(trimmed) }.getOrNull()
    val scheme = uri?.scheme?.lowercase() ?: trimmed.substringBefore(":", "").lowercase()
    val host = uri?.host
    val cleaned = if (cleanLinks) cleanOutgoingLink(trimmed) else trimmed
    val isRedirect = cleaned.substringBefore("?") != trimmed.substringBefore("?")
    if (scheme == "https" && site.allows(host) && !isRedirect) {
        return MainFrameUrlAction.AllowInWebView
    }
    if (scheme == "http" && site.allows(host) && !isRedirect) {
        return MainFrameUrlAction.LoadInWebView("https://" + trimmed.substringAfter("://"))
    }
    if (isRedirect) {
        val cleanedUri = runCatching { java.net.URI(cleaned) }.getOrNull()
        val cleanedScheme = cleanedUri?.scheme?.lowercase()
        if ((cleanedScheme == "https" || cleanedScheme == "http") && site.allows(cleanedUri.host)) {
            val upgraded = if (cleanedScheme == "http") "https://" + cleaned.substringAfter("://") else cleaned
            return MainFrameUrlAction.LoadInWebView(upgraded)
        }
    }
    return when {
        scheme == "https" || scheme == "http" -> MainFrameUrlAction.OpenOutside(trimmed)
        scheme in DIAL_AND_MAIL_SCHEMES && hasGesture -> MainFrameUrlAction.OpenDialOrMail(trimmed)
        else -> MainFrameUrlAction.Block
    }
}

internal fun handleMainFrameUrlOverride(
    view: WebView,
    request: WebResourceRequest,
    site: LiveWebSite,
    config: LiveWebConfig,
): Boolean =
    when (
        val action =
            decideMainFrameUrlAction(
                rawUrl = request.url.toString(),
                site = site,
                cleanLinks = config.cleanLinks,
                hasGesture = request.hasGesture(),
                isForMainFrame = request.isForMainFrame,
            )
    ) {
        MainFrameUrlAction.AllowInWebView -> false

        is MainFrameUrlAction.LoadInWebView -> {
            view.post { view.loadUrl(action.url) }
            true
        }

        is MainFrameUrlAction.OpenOutside -> {
            view.openTappedLinkOutsideWebView(action.url, config.cleanLinks, config.openInReader)
            true
        }

        is MainFrameUrlAction.OpenDialOrMail -> {
            runCatching {
                view.context.startActivity(
                    Intent(Intent.ACTION_VIEW, action.url.toUri())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            true
        }

        MainFrameUrlAction.Block -> true
    }

internal fun handleCreateWindow(
    parentWebView: WebView,
    site: LiveWebSite,
    config: LiveWebConfig,
    isUserGesture: Boolean,
    resultMsg: Message?,
): Boolean {
    if (!isUserGesture) return false
    // Fast path: if the user tapped a link with an explicit target URL, route it directly.
    val hitUrl = parentWebView.hitTestResult.extra
    if (!hitUrl.isNullOrEmpty() && (hitUrl.startsWith("http://") || hitUrl.startsWith("https://"))) {
        when (
            val action =
                decideMainFrameUrlAction(
                    rawUrl = hitUrl,
                    site = site,
                    cleanLinks = config.cleanLinks,
                    hasGesture = isUserGesture,
                    isForMainFrame = true,
                )
        ) {
            is MainFrameUrlAction.LoadInWebView -> {
                parentWebView.post { parentWebView.loadUrl(action.url) }
                return false
            }

            is MainFrameUrlAction.OpenOutside -> {
                parentWebView.openTappedLinkOutsideWebView(action.url, config.cleanLinks, config.openInReader)
                return false
            }

            else -> {}
        }
    }

    val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
    val popupCounters = LiveWebPrivacyCounters.get(parentWebView.context)
    val popupWebView =
        createThemeSafeWebView(parentWebView.context).apply {
            useProfileOf(site)
            hardenWebSettings(site, config.blockThirdPartyCookies)
            // The popup's first request leaves before it hands its URL over, so it gets the same
            // header rule and request decision as the page that opened it.
            configureWebViewHeadersAndDarkening(settings)
            webViewClient =
                object : WebViewClient() {
                    private var handled = false

                    override fun shouldInterceptRequest(
                        popup: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse? =
                        siteRequestResponse(site, config, request.url.host, popupCounters)
                            ?: super.shouldInterceptRequest(popup, request)

                    private fun handleTarget(targetUrl: String): Boolean {
                        if (handled) return true
                        if (targetUrl.isBlank() || targetUrl == "about:blank") return false
                        handled = true
                        when (
                            val action =
                                decideMainFrameUrlAction(
                                    rawUrl = targetUrl,
                                    site = site,
                                    cleanLinks = config.cleanLinks,
                                    hasGesture = isUserGesture,
                                    isForMainFrame = true,
                                )
                        ) {
                            MainFrameUrlAction.AllowInWebView -> {
                                parentWebView.loadUrl(targetUrl)
                            }

                            is MainFrameUrlAction.LoadInWebView -> {
                                parentWebView.loadUrl(action.url)
                            }

                            is MainFrameUrlAction.OpenOutside -> {
                                parentWebView.openTappedLinkOutsideWebView(action.url, config.cleanLinks, config.openInReader)
                            }

                            is MainFrameUrlAction.OpenDialOrMail -> {
                                runCatching {
                                    parentWebView.context.startActivity(
                                        Intent(Intent.ACTION_VIEW, action.url.toUri())
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                            }

                            MainFrameUrlAction.Block -> {}
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
            // Guarantee cleanup if the popup never navigates away from about:blank.
            postDelayed(
                {
                    if (parent == null) {
                        runCatching { destroy() }
                    }
                },
                15_000L,
            )
        }
    transport.webView = popupWebView
    resultMsg.sendToTarget()
    return true
}

internal fun attachSafeDownloadListener(webView: WebView) {
    webView.setDownloadListener { url, _, _, _, _ ->
        val trimmed = url?.trim().orEmpty()
        if (trimmed.startsWith("https://", ignoreCase = true) || trimmed.startsWith("http://", ignoreCase = true)) {
            openInSystemBrowser(webView.context, trimmed)
        }
    }
}

private val DIAL_AND_MAIL_SCHEMES = setOf("mailto", "tel", "sms")

private val REDDIT_OVER18_ORIGINS =
    listOf("https://www.reddit.com/", "https://reddit.com/", "https://sh.reddit.com/", "https://old.reddit.com/")

private const val REDDIT_OVER18_COOKIE = "over18=1; Path=/; Domain=.reddit.com; Max-Age=31536000; SameSite=Lax"

/** Same name, path and domain as [REDDIT_OVER18_COOKIE], so it replaces and expires that cookie. */
private const val REDDIT_OVER18_EXPIRED_COOKIE = "over18=; Path=/; Domain=.reddit.com; Max-Age=0; SameSite=Lax"

private const val REDDIT_OVER18_PREFS = "live_web_reddit_over18"

/**
 * True while the Reddit jar may hold the over18 cookie Crux set. Missing counts as true: 1.0.12
 * set it by default and kept no record, so the first check after the update takes it back once.
 */
private const val REDDIT_OVER18_SET_BY_CRUX = "over18_maybe_set_by_crux"

/** The small prefs file that remembers whether Crux may have set Reddit's over18 cookie. */
internal fun redditOver18Prefs(context: Context): SharedPreferences =
    context.applicationContext.getSharedPreferences(REDDIT_OVER18_PREFS, Context.MODE_PRIVATE)

/**
 * Whether the Reddit jar may still hold the over18 cookie Crux set. A missing flag (every 1.0.12
 * user, who never had it written) counts as true, so the cookie is taken back once.
 */
internal fun redditOver18MaybeSetByCrux(prefs: SharedPreferences): Boolean = prefs.getBoolean(REDDIT_OVER18_SET_BY_CRUX, true)

internal enum class RedditOver18CookieAction { Set, Expire, None }

/**
 * What to do with Reddit's over18 cookie before a page loads: set it only for a signed-out reader
 * with the Unblur tweak on; otherwise expire it, but only if Crux may have set it itself.
 */
internal fun redditOver18CookieAction(
    tweakOn: Boolean,
    signedIn: Boolean,
    maybeSetByCrux: Boolean,
): RedditOver18CookieAction =
    when {
        tweakOn && !signedIn -> RedditOver18CookieAction.Set
        maybeSetByCrux -> RedditOver18CookieAction.Expire
        else -> RedditOver18CookieAction.None
    }

/**
 * While a site WebView is on screen and on a private page (messages, sign-in), asks MainActivity
 * for FLAG_SECURE, marks the view sensitive for screen sharing on Android 15+, and on a DM thread
 * keeps the thread out of the autofill service. Leaving the page, hiding the view or disposing it
 * drops the request again; sign-in pages keep autofill so password managers still fill them.
 *
 * Runs from [WebSessionLifecycleSuspender], so every screen that shows a site page is covered:
 * the tabs, the sign-in and link screen, the share composer and the Instagram and LinkedIn
 * screens. A [LiveWebPage]'s URL is observed directly; other site WebViews are read every
 * [PRIVATE_SCREEN_POLL_MS] while visible. WebViews that belong to no site (the Hardened Browser,
 * the reader) are left alone. The first time a site page is actually on screen, Recents on
 * Android 13+ switches to a blank card for the rest of the process.
 */
@Composable
internal fun PrivateScreenGuard(
    webView: WebView,
    isVisible: Boolean,
) {
    val page = remember(webView) { liveWebPageOf(webView) }
    var polledUrl by remember(webView) { mutableStateOf<String?>(null) }
    var polledSite by remember(webView) { mutableStateOf(false) }
    if (page == null && isVisible) {
        LaunchedEffect(webView) {
            while (true) {
                polledSite = webView.siteOfWebView() != null
                polledUrl = webView.url
                delay(PRIVATE_SCREEN_POLL_MS)
            }
        }
    }
    val isSiteView = page != null || polledSite
    val url = page?.currentUrl ?: polledUrl
    val needsSecure = isSiteView && tabNeedsSecureWindow(isVisible, url)
    // Its own owner per guard, so two hosts of one view never clear each other's request.
    val owner = remember(webView) { Any() }
    DisposableEffect(owner, needsSecure) {
        LiveWebPrivateScreens.update(owner, needsSecure)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            runCatching {
                webView.contentSensitivity =
                    if (needsSecure) View.CONTENT_SENSITIVITY_SENSITIVE else View.CONTENT_SENSITIVITY_AUTO
            }
        }
        onDispose { LiveWebPrivateScreens.update(owner, false) }
    }
    LaunchedEffect(webView, isSiteView, isVisible) {
        if (isSiteView && isVisible) LiveWebPrivateScreens.markWebContentShown()
    }
    LaunchedEffect(webView, isSiteView, url) {
        if (isSiteView) webView.applyPrivateScreenAutofill(url)
    }
}

/** How often a site WebView without a [LiveWebPage] is checked for a private page while shown. */
private const val PRIVATE_SCREEN_POLL_MS = 300L

/** True when [url] is already at [site]'s main home feed (or sign-in page), so reselecting reloads instead of navigating home. */
internal fun isAtSiteHomeUrl(
    site: LiveWebSite,
    url: String?,
): Boolean {
    if (url.isNullOrBlank() || LiveWebSessionState.isAuthOrLoginUrl(url)) return true
    val uri = runCatching { java.net.URI(url.trim()) }.getOrNull() ?: return true
    if (!site.allows(uri.host)) return false
    val path = uri.path.orEmpty().trimEnd('/').lowercase()
    return when (site) {
        LiveWebSite.Instagram -> path.isEmpty()
        LiveWebSite.LinkedIn -> path == "/feed" && uri.rawQuery?.contains("urn", ignoreCase = true) != true
        LiveWebSite.X -> path == "/home" || path.isEmpty()
        LiveWebSite.Reddit -> path.isEmpty() || path in setOf("/new", "/hot", "/best", "/top", "/rising")
        LiveWebSite.Mastodon -> path.isEmpty() || path in setOf("/home", "/explore")
    }
}

private val IMMERSIVE_PATH =
    Regex("^/(?:(?:reels?|stories|direct|messages|messaging|chat|shorts)(?:/|$)|[^/]+/reel(?:/|$))", RegexOption.IGNORE_CASE)

/** True for full-screen video and message pages where pull-to-refresh must stay off. */
internal fun isImmersivePath(url: String?): Boolean {
    val path = url?.let { runCatching { java.net.URI(it).path }.getOrNull() } ?: return false
    return IMMERSIVE_PATH.containsMatchIn(path)
}

/** Reddit's own preferences page, where a signed-in reader turns on "Show mature content". */
internal const val REDDIT_MATURE_CONTENT_SETTINGS_URL = "https://www.reddit.com/settings/preferences"

/**
 * The key the Reddit 18+ note is remembered by: host and path without query, fragment or trailing
 * slash, so one post shows the note once however it was reached. Null for anything not on Reddit.
 */
internal fun redditNsfwNoteKey(url: String?): String? {
    val uri = url?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { java.net.URI(it) }.getOrNull() } ?: return null
    val scheme = uri.scheme?.lowercase()
    if (scheme != "https" && scheme != "http") return null
    val host = uri.host?.lowercase()?.trimEnd('.') ?: return null
    if (host != "reddit.com" && !host.endsWith(".reddit.com")) return null
    val path = uri.rawPath.orEmpty().trimEnd('/').ifEmpty { "/" }
    return "${host.removePrefix("www.")}$path"
}

/** What to do with a request for the Reddit 18+ note. */
internal enum class RedditNsfwNoteStep { Show, Defer, Ignore }

/**
 * Not a Reddit page, or already shown for this post: ignore. Behind the App Lock: defer until it
 * opens, so a post loaded behind the lock still gets its note. Otherwise show it.
 */
internal fun redditNsfwNoteStep(
    key: String?,
    alreadyShown: Boolean,
    unlocked: Boolean,
): RedditNsfwNoteStep =
    when {
        key == null || alreadyShown -> RedditNsfwNoteStep.Ignore
        !unlocked -> RedditNsfwNoteStep.Defer
        else -> RedditNsfwNoteStep.Show
    }

/** The note's one action: Reddit's settings page when signed in, otherwise Reddit's sign-in page. */
internal data class RedditNsfwNoteAction(
    val signedIn: Boolean,
    val url: String,
)

internal fun redditNsfwNoteAction(signedIn: Boolean): RedditNsfwNoteAction =
    RedditNsfwNoteAction(
        signedIn = signedIn,
        url = if (signedIn) REDDIT_MATURE_CONTENT_SETTINGS_URL else LiveWebSite.Reddit.loginUrl,
    )

/** Rewrites a www/sh/apex Reddit URL to old.reddit.com so withheld mobile NSFW embeds can still be viewed in-app. */
internal fun oldRedditUrlFor(url: String?): String? {
    val uri = url?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { java.net.URI(it) }.getOrNull() } ?: return null
    val scheme = uri.scheme?.lowercase()
    if (scheme != "https" && scheme != "http") return null
    val host = uri.host?.lowercase()?.trimEnd('.') ?: return null
    if (host == "old.reddit.com") return null
    if (host != "reddit.com" && !host.endsWith(".reddit.com")) return null
    val path = uri.rawPath.orEmpty().ifEmpty { "/" }
    val query = uri.rawQuery?.let { "?$it" }.orEmpty()
    return "https://old.reddit.com$path$query"
}

/**
 * Explains why a Reddit post's 18+ media box is empty: Reddit withholds it on mobile web unless the
 * signed-in account has "Show mature content" on. Crux never plays the media itself and never
 * changes the setting; the buttons let the user open Reddit's own page or view on old.reddit.com.
 */
@Composable
internal fun RedditNsfwNote(
    page: LiveWebPage,
    modifier: Modifier = Modifier,
) {
    val noteUrl = page.redditNsfwNoteUrl ?: return
    val action = remember(noteUrl) { redditNsfwNoteAction(signedIn = LiveWebSite.Reddit.hasSession()) }
    val oldRedditUrl = remember(noteUrl) { oldRedditUrlFor(noteUrl) }
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 6.dp)) {
            Text(
                text = stringResource(R.string.live_web_reddit_nsfw_note_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 8.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.live_web_reddit_nsfw_note_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { page.dismissRedditNsfwNote() }) {
                    Text(stringResource(R.string.live_web_reddit_nsfw_note_dismiss))
                }
                TextButton(onClick = {
                    page.dismissRedditNsfwNote()
                    page.webView.loadUrl(action.url)
                }) {
                    Text(
                        stringResource(
                            if (action.signedIn) R.string.live_web_reddit_nsfw_note_open_settings else R.string.crux_action_sign_in,
                        ),
                    )
                }
                if (oldRedditUrl != null) {
                    TextButton(onClick = {
                        page.dismissRedditNsfwNote()
                        page.webView.loadUrl(oldRedditUrl)
                    }) {
                        Text(stringResource(R.string.live_web_reddit_nsfw_note_open_old_reddit))
                    }
                }
            }
        }
    }
}
