package org.crux.ui.screen.instagram

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.webkit.ConsoleMessage
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
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.kevinnzou.web.AccompanistWebChromeClient
import com.kevinnzou.web.AccompanistWebViewClient
import com.kevinnzou.web.WebView
import com.kevinnzou.web.rememberWebViewState
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.brands.Instagram
import compose.icons.fontawesomeicons.solid.ArrowUpRightFromSquare
import compose.icons.fontawesomeicons.solid.Bookmark
import compose.icons.fontawesomeicons.solid.EllipsisVertical
import compose.icons.fontawesomeicons.solid.RotateRight
import compose.icons.fontawesomeicons.solid.Rss
import compose.icons.fontawesomeicons.solid.TrashCan
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.launch
import org.crux.BuildConfig
import org.crux.R
import org.crux.ui.component.BackButton
import org.crux.ui.component.CruxDropdownMenu
import org.crux.ui.component.CruxScaffold
import org.crux.ui.component.CruxTopAppBar
import org.crux.ui.component.FAIcon
import org.crux.ui.lock.AppLock
import org.crux.ui.screen.crux.CruxPlatform
import org.crux.ui.screen.crux.LocalCruxConnections
import org.crux.ui.screen.crux.PlatformBadge
import org.crux.ui.screen.crux.isSignedIn
import org.crux.ui.screen.home.ExtractedSocialPost
import org.crux.ui.screen.home.FullscreenCustomVideoOverlay
import org.crux.ui.screen.home.LiveWebConfig
import org.crux.ui.screen.home.LiveWebPrivacyCounters
import org.crux.ui.screen.home.LiveWebPrivacyStore
import org.crux.ui.screen.home.LiveWebSessionState
import org.crux.ui.screen.home.LiveWebSite
import org.crux.ui.screen.home.LocalLiveWebPages
import org.crux.ui.screen.home.SaveToBookmarkFolderSheet
import org.crux.ui.screen.home.WebSessionLifecycleSuspender
import org.crux.ui.screen.home.attachSafeDownloadListener
import org.crux.ui.screen.home.bridgeBookmarkMessage
import org.crux.ui.screen.home.buildFileChooserIntent
import org.crux.ui.screen.home.clearSession
import org.crux.ui.screen.home.computeWebViewCanGoBack
import org.crux.ui.screen.home.configureWebViewHeadersAndDarkening
import org.crux.ui.screen.home.cookieManager
import org.crux.ui.screen.home.createThemeSafeWebView
import org.crux.ui.screen.home.extractPostFromWebView
import org.crux.ui.screen.home.formatCompactHostPath
import org.crux.ui.screen.home.generateWebBridgeToken
import org.crux.ui.screen.home.handleCreateWindow
import org.crux.ui.screen.home.handleMainFrameUrlOverride
import org.crux.ui.screen.home.isExternalOAuthUrl
import org.crux.ui.screen.home.navigateWebViewBackOrEscapeOAuth
import org.crux.ui.screen.home.openInSystemBrowser
import org.crux.ui.screen.home.parseFileChooserActivityResult
import org.crux.ui.screen.home.sanitizeSiteInitialUrl
import org.crux.ui.screen.home.siteRequestResponse
import org.crux.ui.screen.home.useProfileOf

private const val INSTAGRAM_HOME_URL = "https://www.instagram.com"

internal fun isAdOrTrackingUrl(url: String): Boolean {
    val lower = url.lowercase()
    return lower.contains("doubleclick.net") ||
        lower.contains("google-analytics.com") ||
        lower.contains("googletagmanager.com") ||
        lower.contains("adservice.google.com") ||
        lower.contains("branch.io") ||
        lower.contains("adjust.com") ||
        lower.contains("appsflyer.com")
}

/**
 * Chrome client for the in-app web screens that cancels a page's alert, confirm, prompt and
 * beforeunload dialogs while App Lock is covering the app, so a page cannot draw over the lock
 * or keep the user's attention while it is up. Extends the library's client so its loading state
 * keeps working when passed as the `chromeClient` of com.kevinnzou.web.WebView.
 */
internal open class LockAwareWebChromeClient(
    private val isUnlocked: () -> Boolean = { AppLock.isUnlocked },
) : AccompanistWebChromeClient() {
    /**
     * Runs [block] only while the app is unlocked. Page-triggered actions (such as the bookmark
     * bridge, which opens a bottom sheet in its own window) must not draw over the App Lock cover.
     */
    internal fun runIfUnlocked(block: () -> Unit) {
        if (isUnlocked()) block()
    }

    /** True when the dialog was cancelled because the app is locked. */
    private fun cancelWhileLocked(result: JsResult?): Boolean {
        if (isUnlocked()) return false
        result?.cancel()
        return true
    }

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

    // A blank 1x1 poster, so videos show no grey play-button placeholder before their first frame.
    private val emptyVideoPoster: Bitmap by lazy { Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888) }

    override fun getDefaultVideoPoster(): Bitmap = emptyVideoPoster
}

/** How a web screen's sign-in state moved since its last check. */
internal enum class WebSessionChange { None, SignedIn, SignedOut }

/**
 * Tracks a web screen's sign-in state from the moment the screen opened, so the home tab is
 * reloaded only on a real sign-in or sign-out, not every time a signed-in user opens a link.
 */
internal class WebSessionTracker(
    initialSignedIn: Boolean,
) {
    var signedIn: Boolean = initialSignedIn
        private set

    /** Records the latest state and reports whether it changed. */
    fun update(isSignedIn: Boolean): WebSessionChange {
        if (isSignedIn == signedIn) return WebSessionChange.None
        signedIn = isSignedIn
        return if (isSignedIn) WebSessionChange.SignedIn else WebSessionChange.SignedOut
    }
}

/**
 * The sign-in state a web screen starts from. The sign-in flow (auto-return on) always starts
 * signed out, because Crux saw no session when it opened it: the first signed-in check then
 * reports SignedIn and returns the user to Crux. Any other open starts from the real cookies, so
 * an already-signed-in user opening a link never reloads the home tab. Cookies are not read when
 * the flow starts signed out.
 */
internal fun initialSessionSignedIn(
    autoReturnOnSignIn: Boolean,
    readSignedIn: () -> Boolean,
): Boolean = !autoReturnOnSignIn && readSignedIn()

/**
 * Cookie flushes block and may do disk I/O, so the screens only flush where sign-in cookies were
 * just written: on a sign-in page, or right after a sign-in was noticed. Reading needs no flush.
 */
internal fun shouldFlushSessionCookies(
    url: String?,
    change: WebSessionChange,
): Boolean = change == WebSessionChange.SignedIn || LiveWebSessionState.isAuthOrLoginUrl(url)

/** Frees a screen's WebView when it leaves composition, including one whose renderer died. */
internal fun releaseScreenWebView(webView: WebView) {
    runCatching {
        webView.stopLoading()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    }
}

/** What the top-bar back arrow of a standalone web screen does. */
internal enum class ScreenBackAction { ExitVideo, PageBack, Leave }

/**
 * The arrow agrees with system Back: it closes a fullscreen video first, then walks the page's own
 * history (or out of an external sign-in page), and leaves the screen only when there is nothing
 * left to go back to. The overflow menu's Close leaves at once from anywhere.
 */
internal fun screenBackAction(
    videoShowing: Boolean,
    pageCanGoBack: Boolean,
    onExternalOAuthPage: Boolean,
): ScreenBackAction =
    when {
        videoShowing -> ScreenBackAction.ExitVideo
        pageCanGoBack || onExternalOAuthPage -> ScreenBackAction.PageBack
        else -> ScreenBackAction.Leave
    }

/**
 * True when a settings change must be written into a WebView that is already built (its
 * document-start scripts, autoplay rule or third-party cookie rule) and the page reloaded. Every
 * other part of the config (the request decision, link cleaning, the reader) is read on each use,
 * so it takes effect with no reload. False before the first WebView was set up.
 */
internal fun screenConfigNeedsReapply(
    site: LiveWebSite,
    applied: LiveWebConfig?,
    current: LiveWebConfig,
): Boolean =
    applied != null &&
        applied != current &&
        (
            applied.blockAutoplay != current.blockAutoplay ||
                applied.blockThirdPartyCookies != current.blockThirdPartyCookies ||
                applied.documentStartScripts(site) != current.documentStartScripts(site)
        )

/**
 * The live privacy config of a standalone web screen (Instagram, LinkedIn links), so it follows
 * Settings the way the home tabs do instead of keeping the values it opened with. [current] is
 * read from WebView's network threads, hence the atomic reference. [applyTo] writes the
 * config-dependent parts into a WebView and can run again on the same one: the scripts it added
 * before are removed first, so they never stack.
 */
internal class ScreenWebConfig(
    private val site: LiveWebSite,
    private val bridgeToken: String,
    initial: LiveWebConfig,
) {
    private val latest = AtomicReference(initial)
    private var applied: LiveWebConfig? = null
    private val scripts = ReplaceableScripts(::addDocumentStartScript)

    val current: LiveWebConfig get() = latest.get()

    /** Records [config] and says whether the built WebView needs [applyTo] and a reload. Main thread. */
    fun update(config: LiveWebConfig): Boolean {
        latest.set(config)
        return screenConfigNeedsReapply(site, applied, config)
    }

    /** Main thread. */
    fun applyTo(view: WebView) {
        val config = current
        view.settings.mediaPlaybackRequiresUserGesture = config.blockAutoplay
        val acceptThirdParty = !config.blockThirdPartyCookies
        runCatching {
            val globalCookieManager = android.webkit.CookieManager.getInstance()
            globalCookieManager.setAcceptCookie(true)
            globalCookieManager.setAcceptThirdPartyCookies(view, acceptThirdParty)
        }
        site.cookieManager().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(view, acceptThirdParty)
        }
        val startScripts =
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                config.documentStartScripts(site, bridgeToken)
            } else {
                emptyList()
            }
        scripts.replace(view, startScripts)
        applied = config
    }

    private fun addDocumentStartScript(
        view: WebView,
        script: String,
    ): (() -> Unit)? =
        runCatching { WebViewCompat.addDocumentStartJavaScript(view, script, site.documentStartOriginRules) }
            .getOrNull()
            ?.let { handler -> { handler.remove() } }

    /** For WebViews that cannot inject at document start: run the scripts once the page has loaded. */
    fun runScriptsAfterLoad(
        view: WebView,
        url: String?,
    ) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        if (isExternalOAuthUrl(url)) return
        if (!site.allows(runCatching { java.net.URI(url.orEmpty()).host }.getOrNull())) return
        current.documentStartScripts(site, bridgeToken).forEach { view.evaluateJavascript(it, null) }
    }
}

/**
 * The document-start scripts one owner has added to a WebView, so writing a new set first removes
 * the old one instead of stacking both. [install] adds one script and returns how to remove it, or
 * null when it could not be added. A different view (a rebuilt WebView) starts a fresh set: the old
 * view's scripts went with it. Main thread only.
 */
internal class ReplaceableScripts<V : Any>(
    private val install: (V, String) -> (() -> Unit)?,
) {
    private var target: WeakReference<V>? = null
    private val removers = mutableListOf<() -> Unit>()

    /** How many scripts of the last [replace] are in place. */
    val installedCount: Int get() = removers.size

    fun replace(
        view: V,
        scripts: List<String>,
    ) {
        if (target?.get() === view) removers.forEach { runCatching { it() } }
        removers.clear()
        target = WeakReference(view)
        scripts.forEach { script -> install(view, script)?.let { removers += it } }
    }
}

/**
 * The settings every standalone web screen's WebView shares, whatever the privacy config says:
 * no file or content access, no geolocation, no form-data saving, Safe Browsing on, popups only
 * through onCreateWindow. The config-dependent rest is [ScreenWebConfig.applyTo].
 */
@SuppressLint("SetJavaScriptEnabled")
internal fun WebView.hardenScreenWebView(backgroundColor: Int) {
    setBackgroundColor(backgroundColor)
    isNestedScrollingEnabled = false
    overScrollMode = View.OVER_SCROLL_NEVER
    isVerticalScrollBarEnabled = false
    isHorizontalScrollBarEnabled = false
    with(settings) {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        allowFileAccess = false
        allowContentAccess = false
        setGeolocationEnabled(false)
        @Suppress("DEPRECATION")
        setAllowFileAccessFromFileURLs(false)
        @Suppress("DEPRECATION")
        setAllowUniversalAccessFromFileURLs(false)
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(true)
        @Suppress("DEPRECATION")
        saveFormData = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            safeBrowsingEnabled = true
        }
        cacheMode = WebSettings.LOAD_DEFAULT
        useWideViewPort = true
        loadWithOverviewMode = true
        displayZoomControls = false
        builtInZoomControls = false
    }
    configureWebViewHeadersAndDarkening(settings)
    attachSafeDownloadListener(this)
}

/**
 * A standalone screen's request decision: the same one the home tabs use ([siteRequestResponse]),
 * so these pages show in the Traffic log and are blocked only when the site's level says so.
 */
internal fun screenRequestResponse(
    site: LiveWebSite,
    config: LiveWebConfig,
    request: WebResourceRequest?,
    counters: LiveWebPrivacyCounters,
): WebResourceResponse? = request?.let { siteRequestResponse(site, config, it.url?.host, counters) }

internal fun extractInstagramProfile(url: String): String? {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
    val host = uri.host?.lowercase()?.trimEnd('.').orEmpty()
    if (host != "instagram.com" && !host.endsWith(".instagram.com")) return null
    val segments = uri.pathSegments ?: return null
    if (segments.isEmpty()) return null
    val first = segments[0].lowercase()
    val nonProfiles =
        setOf(
            "explore",
            "direct",
            "accounts",
            "stories",
            "reels",
            "reel",
            "p",
            "tv",
            "developer",
            "about",
            "legal",
            "privacy",
            "terms",
            "api",
            "static",
            "challenge",
        )
    if (first in nonProfiles) return null
    if (!first.matches(Regex("^[a-zA-Z0-9._]+$"))) return null
    return segments[0]
}

/** Reads Instagram's own cookie jar for a signed-in session. No flush is needed to read. */
private fun readInstagramSignedIn(): Boolean =
    runCatching {
        val cm = LiveWebSite.Instagram.cookieManager()
        val cookies = cm.getCookie(INSTAGRAM_HOME_URL).orEmpty() + "; " + cm.getCookie("https://instagram.com").orEmpty()
        cookies.contains("sessionid=") && cookies.contains("ds_user_id=")
    }.getOrDefault(false)

private fun isValidInstagramHost(host: String?): Boolean {
    if (host.isNullOrBlank()) return false
    val lower = host.lowercase()
    return lower == "instagram.com" || lower.endsWith(".instagram.com") ||
        lower == "threads.net" || lower.endsWith(".threads.net") ||
        lower == "facebook.com" || lower.endsWith(".facebook.com")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InstagramWebScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialUrl: String? = null,
    forSignIn: Boolean = false,
    onNavigateToRss: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    // Pause page script while App Lock covers the app.
    WebSessionLifecycleSuspender(webView = webViewRef.value, isVisible = AppLock.isUnlocked)
    val scope = rememberCoroutineScope()
    val targetUrl =
        remember(initialUrl, forSignIn) {
            sanitizeSiteInitialUrl(LiveWebSite.Instagram, initialUrl)
        }
    val bridgeToken = remember { generateWebBridgeToken() }
    val bookmarkBridgeMsg = remember(bridgeToken) { bridgeBookmarkMessage(bridgeToken) }
    // Settings are followed live, as on the home tabs: the clients read webConfig.current on each
    // use, and a change to the page's scripts, autoplay or cookie rule is written in and reloaded.
    val privacyStore = remember(context) { LiveWebPrivacyStore.get(context) }
    val allSettings by privacyStore.settings.collectAsState()
    val config = remember(allSettings) { allSettings.configFor(LiveWebSite.Instagram) }
    val webConfig = remember { ScreenWebConfig(LiveWebSite.Instagram, bridgeToken, config) }
    LaunchedEffect(config) {
        if (webConfig.update(config)) {
            webViewRef.value?.let { view ->
                webConfig.applyTo(view)
                view.reload()
            }
        }
    }
    // After a renderer crash the dead WebView leaves composition (and is destroyed); Retry bumps
    // the generation so a fresh WebView with a fresh state is built, reopening where the user was.
    var webViewGeneration by remember { mutableIntStateOf(0) }
    var rendererGone by remember { mutableStateOf(false) }
    var restoreUrl by remember { mutableStateOf<String?>(null) }
    val pages = LocalLiveWebPages.current
    val connections = LocalCruxConnections.current
    val autoReturnOnSignIn =
        remember(forSignIn) {
            forSignIn && !CruxPlatform.Instagram.isSignedIn()
        }
    var hasAutoReturned by remember { mutableStateOf(false) }

    var currentUrl by remember { mutableStateOf(targetUrl) }
    var canGoBack by remember { mutableStateOf(false) }
    var pageProgress by remember { mutableFloatStateOf(0f) }
    var isPageLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var pendingBookmarkPost by remember { mutableStateOf<ExtractedSocialPost?>(null) }
    var customVideoView by remember { mutableStateOf<View?>(null) }
    var customVideoCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    fun exitFullscreenVideo() {
        val cb = customVideoCallback
        customVideoCallback = null
        customVideoView = null
        runCatching { cb?.onCustomViewHidden() }
    }

    // A WebView whose renderer died can't be reloaded: build a fresh one where the user was.
    fun recreateWebViewAfterRendererCrash() {
        loadError = null
        restoreUrl = sanitizeSiteInitialUrl(LiveWebSite.Instagram, currentUrl)
        rendererGone = false
        webViewGeneration += 1
    }

    // Reloads the home tab and refreshes connections only when the sign-in state really changes
    // while this screen is open. Opening a link while already signed in must not reload the home
    // tab (that loses the user's place). This runs from onPageFinished and doUpdateVisitedHistory.
    // The sign-in flow starts from "signed out" because Crux saw no session when it opened it.
    val sessionTracker =
        remember {
            WebSessionTracker(initialSessionSignedIn(autoReturnOnSignIn, ::readInstagramSignedIn))
        }
    val checkAndSyncSession: (String?) -> Unit =
        remember(pages, connections, autoReturnOnSignIn) {
            { url ->
                val change = sessionTracker.update(readInstagramSignedIn())
                if (shouldFlushSessionCookies(url, change)) {
                    runCatching { LiveWebSite.Instagram.cookieManager().flush() }
                }
                if (change != WebSessionChange.None) {
                    scope.launch {
                        pages?.reload(LiveWebSite.Instagram)
                        connections?.refresh()
                        if (change == WebSessionChange.SignedIn && autoReturnOnSignIn && !hasAutoReturned) {
                            hasAutoReturned = true
                            Toast.makeText(context, "Signed in to Instagram", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    }
                }
            }
        }

    DisposableEffect(Unit) {
        checkAndSyncSession(targetUrl)
        onDispose {
            exitFullscreenVideo()
            LiveWebSite.Instagram.cookieManager().flush()
        }
    }

    var fileUploadCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileChooserLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            fileUploadCallback?.onReceiveValue(parseFileChooserActivityResult(result.resultCode, result.data))
            fileUploadCallback = null
        }

    BackHandler(enabled = customVideoView != null || canGoBack) {
        if (customVideoView != null) {
            exitFullscreenVideo()
        } else {
            webViewRef.value?.let { navigateWebViewBackOrEscapeOAuth(it, LiveWebSite.Instagram) }
        }
    }

    LaunchedEffect(targetUrl) {
        if (targetUrl != INSTAGRAM_HOME_URL && targetUrl != currentUrl) {
            webViewRef.value?.loadUrl(targetUrl)
        }
    }

    pendingBookmarkPost?.let { postToSave ->
        SaveToBookmarkFolderSheet(
            post = postToSave,
            onDismiss = { pendingBookmarkPost = null },
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = {
                Text(text = stringResource(R.string.web_privacy_dialog_title))
            },
            text = {
                Text(
                    text = stringResource(R.string.web_privacy_dialog_description),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            icon = {
                FAIcon(
                    imageVector = FontAwesomeIcons.Solid.TrashCan,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp),
                )
            },
            title = {
                Text(text = stringResource(R.string.instagram_clear_title))
            },
            text = {
                Text(
                    text = stringResource(R.string.instagram_clear_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmDialog = false
                        LiveWebSite.Instagram.clearSession(context)
                        // clearAndReload below already reloads the home tab for this sign-out.
                        sessionTracker.update(isSignedIn = false)
                        webViewRef.value?.clearCache(true)
                        webViewRef.value?.clearHistory()
                        pages?.clearAndReload(LiveWebSite.Instagram)
                        connections?.refresh()
                        webViewRef.value?.loadUrl(LiveWebSite.Instagram.startUrlForSession())
                    },
                ) {
                    Text(text = stringResource(R.string.web_clear_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            },
        )
    }

    CruxScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                CruxTopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Brands.Instagram,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = "Instagram",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val subtitle = remember(currentUrl) { formatCompactHostPath(currentUrl) }
                                if (subtitle != null) {
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        BackButton(
                            onBack = {
                                val wv = webViewRef.value
                                when (screenBackAction(customVideoView != null, wv != null && canGoBack, isExternalOAuthUrl(wv?.url))) {
                                    ScreenBackAction.ExitVideo -> exitFullscreenVideo()
                                    ScreenBackAction.PageBack -> wv?.let { navigateWebViewBackOrEscapeOAuth(it, LiveWebSite.Instagram) }
                                    ScreenBackAction.Leave -> onBack()
                                }
                            },
                        )
                    },
                    actions = {
                        val detectedProfile = remember(currentUrl) { extractInstagramProfile(currentUrl) }
                        if (detectedProfile != null && onNavigateToRss != null) {
                            IconButton(
                                onClick = { onNavigateToRss.invoke("https://instagram.com/$detectedProfile") },
                                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                            ) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.Rss,
                                    contentDescription = stringResource(R.string.web_subscribe_feed),
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                val wv = webViewRef.value ?: return@IconButton
                                extractPostFromWebView(
                                    site = LiveWebSite.Instagram,
                                    webView = wv,
                                ) { extracted ->
                                    pendingBookmarkPost = extracted
                                }
                            },
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                        ) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.Bookmark,
                                contentDescription = stringResource(R.string.reading_list_title),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        IconButton(
                            onClick = {
                                if (currentUrl.isNotBlank()) {
                                    openInSystemBrowser(context, currentUrl)
                                }
                            },
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                        ) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.ArrowUpRightFromSquare,
                                contentDescription = stringResource(R.string.web_open_in_browser),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Box {
                            IconButton(
                                onClick = { showOverflowMenu = true },
                                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                            ) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.EllipsisVertical,
                                    contentDescription = stringResource(R.string.web_overflow_menu),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            CruxDropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false },
                            ) {
                                if (canGoBack) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.web_close_screen)) },
                                        onClick = {
                                            showOverflowMenu = false
                                            onBack()
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.web_privacy_info)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        showPrivacyDialog = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.web_nav_reload)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        if (rendererGone) {
                                            recreateWebViewAfterRendererCrash()
                                        } else {
                                            webViewRef.value?.reload()
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(R.string.web_clear_session),
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        showClearConfirmDialog = true
                                    },
                                )
                            }
                        }
                    },
                    scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
                )
                if (isPageLoading && pageProgress < 1f) {
                    LinearProgressIndicator(
                        progress = { pageProgress },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                    )
                }
            }
        },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .imePadding(),
        ) {
            val webBackgroundColor = MaterialTheme.colorScheme.background.toArgb()
            LaunchedEffect(webViewRef.value, webBackgroundColor) {
                webViewRef.value?.setBackgroundColor(webBackgroundColor)
            }
            val privacyCounters = remember(context) { LiveWebPrivacyCounters.get(context.applicationContext) }
            // The library sets its own clients right after onCreated, so ours go in as parameters;
            // a client assigned inside onCreated would be silently replaced. Built once: they read
            // the live config from webConfig.
            val webViewClient =
                remember {
                    object : AccompanistWebViewClient() {
                        override fun onRenderProcessGone(
                            view: WebView,
                            detail: RenderProcessGoneDetail,
                        ): Boolean {
                            exitFullscreenVideo()
                            // This WebView can't be used again: drop it so it is removed and destroyed.
                            if (webViewRef.value === view) webViewRef.value = null
                            rendererGone = true
                            // Nothing to go back in now: let system Back close the screen.
                            canGoBack = false
                            loadError = "Page renderer restarted"
                            isPageLoading = false
                            return true
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest?,
                            error: WebResourceError?,
                        ) {
                            super.onReceivedError(view, request, error)
                            if (request?.isForMainFrame == true) {
                                loadError = error?.description?.toString() ?: "Failed to connect"
                                isPageLoading = false
                            }
                        }

                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest,
                        ): WebResourceResponse? =
                            screenRequestResponse(LiveWebSite.Instagram, webConfig.current, request, privacyCounters)
                                ?: super.shouldInterceptRequest(view, request)

                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean = handleMainFrameUrlOverride(view, request, LiveWebSite.Instagram, webConfig.current)

                        override fun onPageStarted(
                            view: WebView,
                            url: String?,
                            favicon: android.graphics.Bitmap?,
                        ) {
                            super.onPageStarted(view, url, favicon)
                            val committed = url ?: view.url
                            committed?.let {
                                currentUrl = it
                                canGoBack = computeWebViewCanGoBack(view.canGoBack(), it)
                            }
                            loadError = null
                            isPageLoading = true
                        }

                        override fun doUpdateVisitedHistory(
                            view: WebView,
                            url: String?,
                            isReload: Boolean,
                        ) {
                            super.doUpdateVisitedHistory(view, url, isReload)
                            val committed = url ?: view.url
                            committed?.let {
                                currentUrl = it
                                canGoBack = computeWebViewCanGoBack(view.canGoBack(), it)
                            }
                            val host = committed?.let { runCatching { Uri.parse(it).host }.getOrNull() }
                            if (isValidInstagramHost(host)) {
                                checkAndSyncSession(committed)
                            }
                        }

                        override fun onPageFinished(
                            view: WebView,
                            url: String?,
                        ) {
                            super.onPageFinished(view, url)
                            val committed = url ?: view.url
                            committed?.let {
                                currentUrl = it
                                canGoBack = computeWebViewCanGoBack(view.canGoBack(), it)
                            }
                            isPageLoading = false
                            webConfig.runScriptsAfterLoad(view, committed)
                            val host = committed?.let { runCatching { Uri.parse(it).host }.getOrNull() }
                            if (isValidInstagramHost(host)) {
                                checkAndSyncSession(committed)
                            }
                        }
                    }
                }
            val chromeClient =
                remember {
                    object : LockAwareWebChromeClient() {
                        override fun onCreateWindow(
                            view: WebView,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: Message?,
                        ): Boolean = handleCreateWindow(view, LiveWebSite.Instagram, webConfig.current, isUserGesture, resultMsg)

                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                            if (consoleMessage?.message() == bookmarkBridgeMsg) {
                                // Page script can post this at any time: ignore it while App Lock is up,
                                // and re-check when the async extraction returns.
                                runIfUnlocked {
                                    webViewRef.value?.let { wv ->
                                        wv.post {
                                            extractPostFromWebView(LiveWebSite.Instagram, wv) { extracted ->
                                                runIfUnlocked { pendingBookmarkPost = extracted }
                                            }
                                        }
                                    }
                                }
                                return true
                            }
                            // Page messages can carry URLs and data from a signed-in session: debug builds only.
                            return if (BuildConfig.DEBUG) super.onConsoleMessage(consoleMessage) else true
                        }

                        override fun onShowCustomView(
                            customView: View?,
                            callback: WebChromeClient.CustomViewCallback?,
                        ) {
                            if (customView == null || customVideoView != null) {
                                callback?.onCustomViewHidden()
                                return
                            }
                            customVideoView = customView
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

                        override fun onProgressChanged(
                            view: WebView,
                            newProgress: Int,
                        ) {
                            super.onProgressChanged(view, newProgress)
                            pageProgress = newProgress / 100f
                            isPageLoading = newProgress < 100
                        }

                        override fun onShowFileChooser(
                            view: WebView,
                            filePathCallback: ValueCallback<Array<Uri>>,
                            fileChooserParams: WebChromeClient.FileChooserParams,
                        ): Boolean {
                            fileUploadCallback?.onReceiveValue(null)
                            fileUploadCallback = filePathCallback
                            val launched = runCatching { fileChooserLauncher.launch(buildFileChooserIntent(fileChooserParams)) }.isSuccess
                            if (!launched) {
                                fileUploadCallback?.onReceiveValue(null)
                                fileUploadCallback = null
                            }
                            return launched
                        }
                    }
                }
            if (!rendererGone) {
                key(webViewGeneration) {
                    val webViewState = rememberWebViewState(restoreUrl ?: targetUrl)
                    WebView(
                        state = webViewState,
                        layoutParams =
                            FrameLayout.LayoutParams(
                                LayoutParams.MATCH_PARENT,
                                LayoutParams.MATCH_PARENT,
                            ),
                        modifier = Modifier.fillMaxSize(),
                        client = webViewClient,
                        chromeClient = chromeClient,
                        onCreated = { view: WebView ->
                            view.useProfileOf(LiveWebSite.Instagram)
                            view.hardenScreenWebView(webBackgroundColor)
                            webConfig.applyTo(view)
                            webViewRef.value = view
                        },
                        // The keyboard-incognito WebView every site page uses, with the night-mode fix.
                        factory = { viewContext -> createThemeSafeWebView(viewContext) },
                        onDispose = ::releaseScreenWebView,
                    )
                }
            }
            if (loadError != null) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PlatformBadge(platform = CruxPlatform.Instagram, size = 56.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.crux_web_error_title, "Instagram"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.crux_web_error_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        FilledTonalButton(
                            onClick = {
                                if (rendererGone) {
                                    recreateWebViewAfterRendererCrash()
                                } else {
                                    loadError = null
                                    webViewRef.value?.reload()
                                }
                            },
                        ) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.RotateRight,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.crux_web_error_retry))
                        }
                    }
                }
            }
            FullscreenCustomVideoOverlay(
                customView = customVideoView,
                onExit = ::exitFullscreenVideo,
            )
        }
    }
}
