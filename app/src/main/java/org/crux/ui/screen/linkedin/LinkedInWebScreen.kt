package org.crux.ui.screen.linkedin

import android.net.Uri
import android.os.Build
import android.os.Message
import android.view.View
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
import androidx.compose.ui.graphics.Color
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
import compose.icons.fontawesomeicons.brands.LinkedinIn
import compose.icons.fontawesomeicons.solid.ArrowUpRightFromSquare
import compose.icons.fontawesomeicons.solid.Bookmark
import compose.icons.fontawesomeicons.solid.EllipsisVertical
import compose.icons.fontawesomeicons.solid.RotateRight
import kotlinx.coroutines.launch
import org.crux.R
import org.crux.data.datasource.linkedin.LinkedInFeedParser
import org.crux.data.datasource.linkedin.LinkedInFeedService
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LinkedInWebScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialUrl: String? = null,
    forSignIn: Boolean = false,
) {
    val context = LocalContext.current
    val sessionClearedMsg = stringResource(R.string.linkedin_session_cleared)
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    WebSessionLifecycleSuspender(webView = webViewRef.value)
    val scope = rememberCoroutineScope()
    val targetUrl =
        remember(initialUrl, forSignIn) {
            val clean = initialUrl?.trim()
            if (!clean.isNullOrEmpty() && (clean.startsWith("http://") || clean.startsWith("https://"))) {
                clean
            } else {
                LiveWebSite.LinkedIn.startUrlForSession()
            }
        }
    val webViewState = rememberWebViewState(targetUrl)
    val pages = LocalLiveWebPages.current
    val connections = LocalCruxConnections.current
    val autoReturnOnSignIn =
        remember(forSignIn) {
            forSignIn && !CruxPlatform.LinkedIn.isSignedIn()
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

    // Refreshes cookie-backed session state once LinkedIn login cookies are present.
    fun checkAndSyncSession() {
        val cm = LiveWebSite.LinkedIn.cookieManager()
        cm.flush()
        val cookies = cm.getCookie("https://www.linkedin.com").orEmpty() + "; " + cm.getCookie("https://linkedin.com").orEmpty()
        if (LinkedInFeedService.hasActiveSession(cookies)) {
            scope.launch {
                pages?.reload(LiveWebSite.LinkedIn)
                connections?.refresh()
                if (autoReturnOnSignIn && !hasAutoReturned) {
                    hasAutoReturned = true
                    Toast.makeText(context, "Signed in to LinkedIn", Toast.LENGTH_SHORT).show()
                    onBack()
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exitFullscreenVideo()
            LiveWebSite.LinkedIn.cookieManager().flush()
        }
    }

    LaunchedEffect(Unit) {
        checkAndSyncSession()
    }

    BackHandler(enabled = customVideoView != null || canGoBack) {
        if (customVideoView != null) {
            exitFullscreenVideo()
        } else {
            webViewRef.value?.let { navigateWebViewBackOrEscapeOAuth(it, LiveWebSite.LinkedIn) }
        }
    }

    pendingBookmarkPost?.let { postToSave ->
        SaveToBookmarkFolderSheet(
            post = postToSave,
            onDismiss = { pendingBookmarkPost = null },
        )
    }

    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val filePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            filePathCallback?.onReceiveValue(parseFileChooserActivityResult(result.resultCode, result.data))
            filePathCallback = null
        }

    CruxScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                CruxTopAppBar(
                    navigationIcon = {
                        BackButton(
                            onBack = {
                                val wv = webViewRef.value
                                if (customVideoView != null) {
                                    exitFullscreenVideo()
                                } else if (wv != null && isExternalOAuthUrl(wv.url)) {
                                    navigateWebViewBackOrEscapeOAuth(wv, LiveWebSite.LinkedIn)
                                } else {
                                    onBack()
                                }
                            },
                        )
                    },
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
                                    imageVector = FontAwesomeIcons.Brands.LinkedinIn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                            Column {
                                Text(
                                    text = "LinkedIn",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
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
                    actions = {
                        IconButton(
                            onClick = {
                                val wv = webViewRef.value ?: return@IconButton
                                extractPostFromWebView(
                                    site = LiveWebSite.LinkedIn,
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
                                val urlToOpen = webViewRef.value?.url?.takeIf { it.isNotBlank() } ?: currentUrl
                                openInSystemBrowser(context, urlToOpen)
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
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                )
                if (isPageLoading) {
                    LinearProgressIndicator(
                        progress = { pageProgress },
                        modifier = Modifier.fillMaxWidth().height(2.5.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent,
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding(),
        ) {
            val expectedNightMask = remember(context) { context.currentUiNightMask() }
            val webBackgroundColor = MaterialTheme.colorScheme.background.toArgb()
            LaunchedEffect(webViewRef.value, webBackgroundColor) {
                webViewRef.value?.setBackgroundColor(webBackgroundColor)
            }
            WebView(
                state = webViewState,
                modifier = Modifier.fillMaxSize(),
                onCreated = { webView ->
                    restoreUiModeNightMaskIfNeeded(webView.context, expectedNightMask)
                    webView.post { restoreUiModeNightMaskIfNeeded(webView.context, expectedNightMask) }
                    webView.useProfileOf(LiveWebSite.LinkedIn)
                    webView.setBackgroundColor(webBackgroundColor)
                    webView.isNestedScrollingEnabled = false
                    webView.overScrollMode = View.OVER_SCROLL_NEVER
                    webView.isVerticalScrollBarEnabled = false
                    webView.isHorizontalScrollBarEnabled = false
                    webViewRef.value = webView
                    val privacyStore = LiveWebPrivacyStore.get(webView.context)
                    val privacyCounters = LiveWebPrivacyCounters.get(webView.context.applicationContext)
                    val config = privacyStore.configFor(LiveWebSite.LinkedIn)
                    @Suppress("DEPRECATION")
                    webView.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        allowFileAccess = false
                        allowContentAccess = true
                        javaScriptCanOpenWindowsAutomatically = true
                        setSupportMultipleWindows(true)
                        mediaPlaybackRequiresUserGesture = config.blockAutoplay
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            safeBrowsingEnabled = true
                        }
                        builtInZoomControls = false
                        displayZoomControls = false
                        loadWithOverviewMode = true
                        useWideViewPort = true
                    }

                    val acceptThirdParty = !config.blockThirdPartyCookies
                    runCatching {
                        val globalCookieManager = android.webkit.CookieManager.getInstance()
                        globalCookieManager.setAcceptCookie(true)
                        globalCookieManager.setAcceptThirdPartyCookies(webView, acceptThirdParty)
                    }
                    val cookieManager = LiveWebSite.LinkedIn.cookieManager()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(webView, acceptThirdParty)
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                        config.documentStartScripts(LiveWebSite.LinkedIn).forEach { script ->
                            WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("*"))
                        }
                    }
                    configureWebViewHeadersAndDarkening(webView.settings)
                    attachSafeDownloadListener(webView)

                    webView.webViewClient =
                        object : WebViewClient() {
                            override fun onRenderProcessGone(
                                view: WebView?,
                                detail: RenderProcessGoneDetail?,
                            ): Boolean {
                                exitFullscreenVideo()
                                loadError = "Page renderer restarted"
                                isPageLoading = false
                                return true
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?,
                            ) {
                                super.onReceivedError(view, request, error)
                                if (request?.isForMainFrame == true) {
                                    loadError = error?.description?.toString() ?: "Failed to connect"
                                    isPageLoading = false
                                }
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean {
                                if (view == null || request == null) return false
                                return handleMainFrameUrlOverride(view, request, LiveWebSite.LinkedIn, config)
                            }

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): WebResourceResponse? =
                                if (request != null && config.blocks(LiveWebSite.LinkedIn, request.url.host)) {
                                    privacyCounters.recordBlockedRequest(LiveWebSite.LinkedIn)
                                    blockedTrackerResponse()
                                } else {
                                    super.shouldInterceptRequest(view, request)
                                }

                            override fun onPageStarted(
                                view: WebView?,
                                url: String?,
                                favicon: android.graphics.Bitmap?,
                            ) {
                                super.onPageStarted(view, url, favicon)
                                val committed = url ?: view?.url
                                committed?.let {
                                    currentUrl = it
                                    canGoBack = computeWebViewCanGoBack(view?.canGoBack() ?: false, it)
                                }
                                loadError = null
                                isPageLoading = true
                            }

                            override fun onPageFinished(
                                view: WebView?,
                                url: String?,
                            ) {
                                super.onPageFinished(view, url)
                                isPageLoading = false
                                if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                                    config.documentStartScripts(LiveWebSite.LinkedIn).forEach { script ->
                                        view?.evaluateJavascript(script, null)
                                    }
                                }
                                val committed = url ?: view?.url
                                committed?.let {
                                    currentUrl = it
                                    canGoBack = computeWebViewCanGoBack(view?.canGoBack() ?: false, it)
                                }
                                val host = committed?.let { runCatching { Uri.parse(it).host }.getOrNull() }
                                if (LinkedInFeedParser.isValidLinkedInHost(host)) {
                                    checkAndSyncSession()
                                }
                            }

                            override fun doUpdateVisitedHistory(
                                view: WebView?,
                                url: String?,
                                isReload: Boolean,
                            ) {
                                super.doUpdateVisitedHistory(view, url, isReload)
                                val committed = url ?: view?.url
                                committed?.let {
                                    currentUrl = it
                                    canGoBack = computeWebViewCanGoBack(view?.canGoBack() ?: false, it)
                                }
                                val host = committed?.let { runCatching { Uri.parse(it).host }.getOrNull() }
                                if (LinkedInFeedParser.isValidLinkedInHost(host)) {
                                    checkAndSyncSession()
                                }
                            }
                        }

                    webView.webChromeClient =
                        object : WebChromeClient() {
                            override fun onCreateWindow(
                                view: WebView?,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: Message?,
                            ): Boolean {
                                if (view == null) return false
                                return handleCreateWindow(view, LiveWebSite.LinkedIn, config, isUserGesture, resultMsg)
                            }

                            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                if (consoleMessage?.message() == "__crux_bm_tap__") {
                                    webView.post {
                                        extractPostFromWebView(LiveWebSite.LinkedIn, webView) { extracted ->
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

                            override fun onProgressChanged(
                                view: WebView?,
                                newProgress: Int,
                            ) {
                                pageProgress = newProgress / 100f
                                if (newProgress >= 100) {
                                    isPageLoading = false
                                }
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
                                webView: WebView?,
                                callback: ValueCallback<Array<Uri>>?,
                                fileChooserParams: FileChooserParams?,
                            ): Boolean {
                                filePathCallback?.onReceiveValue(null)
                                filePathCallback = callback
                                val launched =
                                    runCatching {
                                        filePickerLauncher.launch(buildFileChooserIntent(fileChooserParams))
                                    }.isSuccess
                                if (!launched) {
                                    filePathCallback?.onReceiveValue(null)
                                    filePathCallback = null
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
                        PlatformBadge(platform = CruxPlatform.LinkedIn, size = 56.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.crux_web_error_title, "LinkedIn"),
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
            title = { Text(stringResource(id = R.string.linkedin_logout_title)) },
            text = {
                Text(stringResource(id = R.string.linkedin_logout_description))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmDialog = false
                        LiveWebSite.LinkedIn.clearSession(context)
                        webViewRef.value?.clearCache(true)
                        webViewRef.value?.clearHistory()
                        pages?.clearAndReload(LiveWebSite.LinkedIn)
                        connections?.refresh()
                        webViewRef.value?.loadUrl(LiveWebSite.LinkedIn.startUrlForSession())
                        Toast
                            .makeText(
                                context,
                                sessionClearedMsg,
                                Toast.LENGTH_SHORT,
                            ).show()
                    },
                ) {
                    Text(
                        text = stringResource(id = R.string.linkedin_logout_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text(stringResource(id = R.string.action_cancel))
                }
            },
        )
    }
}
