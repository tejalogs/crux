package org.crux.ui.screen.instagram

import android.net.Uri
import android.os.Build
import android.os.Message
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import kotlinx.coroutines.launch
import org.crux.R
import org.crux.data.datasource.instagram.InstagramFeedService
import org.crux.ui.component.BackButton
import org.crux.ui.component.CruxDropdownMenu
import org.crux.ui.component.CruxScaffold
import org.crux.ui.component.CruxTopAppBar
import org.crux.ui.component.FAIcon
import org.crux.ui.screen.crux.CruxPlatform
import org.crux.ui.screen.crux.LocalCruxConnections
import org.crux.ui.screen.crux.PlatformBadge
import org.crux.ui.screen.crux.isSignedIn
import org.crux.ui.screen.home.ExtractedSocialPost
import org.crux.ui.screen.home.FullscreenCustomVideoOverlay
import org.crux.ui.screen.home.LiveWebPrivacyCounters
import org.crux.ui.screen.home.LiveWebPrivacyStore
import org.crux.ui.screen.home.LiveWebSite
import org.crux.ui.screen.home.LocalLiveWebPages
import org.crux.ui.screen.home.SaveToBookmarkFolderSheet
import org.crux.ui.screen.home.WebSessionLifecycleSuspender
import org.crux.ui.screen.home.attachSafeDownloadListener
import org.crux.ui.screen.home.blockedTrackerResponse
import org.crux.ui.screen.home.buildFileChooserIntent
import org.crux.ui.screen.home.clearSession
import org.crux.ui.screen.home.computeWebViewCanGoBack
import org.crux.ui.screen.home.configureWebViewHeadersAndDarkening
import org.crux.ui.screen.home.cookieManager
import org.crux.ui.screen.home.currentUiNightMask
import org.crux.ui.screen.home.extractPostFromWebView
import org.crux.ui.screen.home.formatCompactHostPath
import org.crux.ui.screen.home.handleCreateWindow
import org.crux.ui.screen.home.handleMainFrameUrlOverride
import org.crux.ui.screen.home.isExternalOAuthUrl
import org.crux.ui.screen.home.navigateWebViewBackOrEscapeOAuth
import org.crux.ui.screen.home.openInSystemBrowser
import org.crux.ui.screen.home.parseFileChooserActivityResult
import org.crux.ui.screen.home.restoreUiModeNightMaskIfNeeded
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

internal fun extractInstagramProfile(url: String): String? {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
    val host = uri.host?.lowercase().orEmpty()
    if (!host.endsWith("instagram.com")) return null
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
    WebSessionLifecycleSuspender(webView = webViewRef.value)
    val scope = rememberCoroutineScope()
    val targetUrl =
        remember(initialUrl, forSignIn) {
            val clean = initialUrl?.trim()
            if (!clean.isNullOrEmpty() && (clean.startsWith("http://") || clean.startsWith("https://"))) {
                clean
            } else {
                LiveWebSite.Instagram.startUrlForSession()
            }
        }
    val webViewState = rememberWebViewState(targetUrl)
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

    // Refreshes cookie-backed session state once Instagram login cookies are present.
    val checkAndSyncSession =
        remember(pages, connections, autoReturnOnSignIn) {
            {
                val cm = LiveWebSite.Instagram.cookieManager()
                cm.flush()
                val cookies = cm.getCookie(INSTAGRAM_HOME_URL).orEmpty() + "; " + cm.getCookie("https://instagram.com").orEmpty()
                if (InstagramFeedService.hasActiveSession(cookies)) {
                    scope.launch {
                        pages?.reload(LiveWebSite.Instagram)
                        connections?.refresh()
                        if (autoReturnOnSignIn && !hasAutoReturned) {
                            hasAutoReturned = true
                            Toast.makeText(context, "Signed in to Instagram", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    }
                }
            }
        }

    DisposableEffect(Unit) {
        checkAndSyncSession()
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
                                if (customVideoView != null) {
                                    exitFullscreenVideo()
                                } else if (wv != null && isExternalOAuthUrl(wv.url)) {
                                    navigateWebViewBackOrEscapeOAuth(wv, LiveWebSite.Instagram)
                                } else {
                                    onBack()
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
                                        webViewRef.value?.reload()
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
            val expectedNightMask = remember(context) { context.currentUiNightMask() }
            val webBackgroundColor = MaterialTheme.colorScheme.background.toArgb()
            LaunchedEffect(webViewRef.value, webBackgroundColor) {
                webViewRef.value?.setBackgroundColor(webBackgroundColor)
            }
            WebView(
                state = webViewState,
                layoutParams =
                    FrameLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT,
                    ),
                modifier = Modifier.fillMaxSize(),
                onCreated = { view: WebView ->
                    restoreUiModeNightMaskIfNeeded(view.context, expectedNightMask)
                    view.post { restoreUiModeNightMaskIfNeeded(view.context, expectedNightMask) }
                    view.useProfileOf(LiveWebSite.Instagram)
                    view.setBackgroundColor(webBackgroundColor)
                    view.isNestedScrollingEnabled = false
                    view.overScrollMode = View.OVER_SCROLL_NEVER
                    view.isVerticalScrollBarEnabled = false
                    view.isHorizontalScrollBarEnabled = false
                    webViewRef.value = view
                    val privacyStore = LiveWebPrivacyStore.get(view.context)
                    val privacyCounters = LiveWebPrivacyCounters.get(view.context.applicationContext)
                    val config = privacyStore.configFor(LiveWebSite.Instagram)
                    with(view.settings) {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        allowFileAccess = false
                        allowContentAccess = true
                        setGeolocationEnabled(false)
                        @Suppress("DEPRECATION")
                        setAllowFileAccessFromFileURLs(false)
                        @Suppress("DEPRECATION")
                        setAllowUniversalAccessFromFileURLs(false)
                        javaScriptCanOpenWindowsAutomatically = true
                        setSupportMultipleWindows(true)
                        mediaPlaybackRequiresUserGesture = config.blockAutoplay
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
                    val acceptThirdParty = !config.blockThirdPartyCookies
                    runCatching {
                        val globalCookieManager = android.webkit.CookieManager.getInstance()
                        globalCookieManager.setAcceptCookie(true)
                        globalCookieManager.setAcceptThirdPartyCookies(view, acceptThirdParty)
                    }
                    LiveWebSite.Instagram.cookieManager().apply {
                        setAcceptCookie(true)
                        setAcceptThirdPartyCookies(view, acceptThirdParty)
                    }
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                        config.documentStartScripts(LiveWebSite.Instagram).forEach { script ->
                            WebViewCompat.addDocumentStartJavaScript(view, script, setOf("*"))
                        }
                    }
                    configureWebViewHeadersAndDarkening(view.settings)
                    attachSafeDownloadListener(view)

                    view.webViewClient =
                        object : WebViewClient() {
                            override fun onRenderProcessGone(
                                view: WebView,
                                detail: RenderProcessGoneDetail,
                            ): Boolean {
                                exitFullscreenVideo()
                                loadError = "Page renderer restarted"
                                isPageLoading = false
                                return true
                            }

                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                error: WebResourceError,
                            ) {
                                super.onReceivedError(view, request, error)
                                if (request.isForMainFrame) {
                                    loadError = error.description?.toString() ?: "Failed to connect"
                                    isPageLoading = false
                                }
                            }

                            override fun shouldInterceptRequest(
                                view: WebView,
                                request: WebResourceRequest,
                            ): WebResourceResponse? =
                                if (config.blocks(LiveWebSite.Instagram, request.url.host)) {
                                    privacyCounters.recordBlockedRequest(LiveWebSite.Instagram)
                                    blockedTrackerResponse()
                                } else {
                                    super.shouldInterceptRequest(view, request)
                                }

                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean = handleMainFrameUrlOverride(view, request, LiveWebSite.Instagram, config)

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
                                if (InstagramFeedService.isValidInstagramHost(host)) {
                                    checkAndSyncSession()
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
                                if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                                    config.documentStartScripts(LiveWebSite.Instagram).forEach { script ->
                                        view.evaluateJavascript(script, null)
                                    }
                                }
                                val host = committed?.let { runCatching { Uri.parse(it).host }.getOrNull() }
                                if (InstagramFeedService.isValidInstagramHost(host)) {
                                    checkAndSyncSession()
                                }
                            }
                        }

                    view.webChromeClient =
                        object : WebChromeClient() {
                            override fun onCreateWindow(
                                view: WebView,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: Message?,
                            ): Boolean = handleCreateWindow(view, LiveWebSite.Instagram, config, isUserGesture, resultMsg)

                            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                if (consoleMessage?.message() == "__crux_bm_tap__") {
                                    view.post {
                                        extractPostFromWebView(LiveWebSite.Instagram, view) { extracted ->
                                            pendingBookmarkPost = extracted
                                        }
                                    }
                                    return true
                                }
                                return super.onConsoleMessage(consoleMessage)
                            }

                            override fun onShowCustomView(
                                customView: View?,
                                callback: CustomViewCallback?,
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
                                pageProgress = newProgress / 100f
                                isPageLoading = newProgress < 100
                            }

                            override fun onShowFileChooser(
                                view: WebView,
                                filePathCallback: ValueCallback<Array<Uri>>,
                                fileChooserParams: FileChooserParams,
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
                },
            )
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
                                loadError = null
                                webViewRef.value?.reload()
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
