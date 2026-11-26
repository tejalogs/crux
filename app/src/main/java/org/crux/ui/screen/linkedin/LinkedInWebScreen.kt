package org.crux.ui.screen.linkedin

import android.net.Uri
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
import android.webkit.WebView
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kevinnzou.web.AccompanistWebViewClient
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
import org.crux.BuildConfig
import org.crux.R
import org.crux.data.datasource.linkedin.LinkedInFeedParser
import org.crux.data.datasource.linkedin.LinkedInFeedService
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
import org.crux.ui.screen.home.LiveWebPrivacyCounters
import org.crux.ui.screen.home.LiveWebPrivacyStore
import org.crux.ui.screen.home.LiveWebSite
import org.crux.ui.screen.home.LocalLiveWebPages
import org.crux.ui.screen.home.SaveToBookmarkFolderSheet
import org.crux.ui.screen.home.WebSessionLifecycleSuspender
import org.crux.ui.screen.home.bridgeBookmarkMessage
import org.crux.ui.screen.home.buildFileChooserIntent
import org.crux.ui.screen.home.clearSession
import org.crux.ui.screen.home.computeWebViewCanGoBack
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
import org.crux.ui.screen.home.useProfileOf
import org.crux.ui.screen.instagram.LockAwareWebChromeClient
import org.crux.ui.screen.instagram.ScreenBackAction
import org.crux.ui.screen.instagram.ScreenWebConfig
import org.crux.ui.screen.instagram.WebSessionChange
import org.crux.ui.screen.instagram.WebSessionTracker
import org.crux.ui.screen.instagram.initialSessionSignedIn
import org.crux.ui.screen.instagram.hardenScreenWebView
import org.crux.ui.screen.instagram.releaseScreenWebView
import org.crux.ui.screen.instagram.screenBackAction
import org.crux.ui.screen.instagram.screenRequestResponse
import org.crux.ui.screen.instagram.shouldFlushSessionCookies

/** Reads LinkedIn's own cookie jar for a signed-in session. No flush is needed to read. */
private fun readLinkedInSignedIn(): Boolean =
    runCatching {
        val cm = LiveWebSite.LinkedIn.cookieManager()
        val cookies = cm.getCookie("https://www.linkedin.com").orEmpty() + "; " + cm.getCookie("https://linkedin.com").orEmpty()
        LinkedInFeedService.hasActiveSession(cookies)
    }.getOrDefault(false)

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
    // Pause page script while App Lock covers the app.
    WebSessionLifecycleSuspender(webView = webViewRef.value, isVisible = AppLock.isUnlocked)
    val scope = rememberCoroutineScope()
    val targetUrl =
        remember(initialUrl, forSignIn) {
            sanitizeSiteInitialUrl(LiveWebSite.LinkedIn, initialUrl)
        }
    val bridgeToken = remember { generateWebBridgeToken() }
    val bookmarkBridgeMsg = remember(bridgeToken) { bridgeBookmarkMessage(bridgeToken) }
    // Settings are followed live, as on the home tabs: the clients read webConfig.current on each
    // use, and a change to the page's scripts, autoplay or cookie rule is written in and reloaded.
    val privacyStore = remember(context) { LiveWebPrivacyStore.get(context) }
    val allSettings by privacyStore.settings.collectAsState()
    val config = remember(allSettings) { allSettings.configFor(LiveWebSite.LinkedIn) }
    val webConfig = remember { ScreenWebConfig(LiveWebSite.LinkedIn, bridgeToken, config) }
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

    // A WebView whose renderer died can't be reloaded: build a fresh one where the user was.
    fun recreateWebViewAfterRendererCrash() {
        loadError = null
        restoreUrl = sanitizeSiteInitialUrl(LiveWebSite.LinkedIn, currentUrl)
        rendererGone = false
        webViewGeneration += 1
    }

    // Reloads the home tab and refreshes connections only when the sign-in state really changes
    // while this screen is open. Opening a LinkedIn link while already signed in must not reload
    // the home tab (that reshuffles the feed and loses the user's place). This runs from
    // onPageFinished and doUpdateVisitedHistory. The sign-in flow starts from "signed out"
    // because Crux saw no session when it opened it.
    val sessionTracker =
        remember {
            WebSessionTracker(initialSessionSignedIn(autoReturnOnSignIn, ::readLinkedInSignedIn))
        }

    fun checkAndSyncSession(url: String?) {
        val change = sessionTracker.update(readLinkedInSignedIn())
        if (shouldFlushSessionCookies(url, change)) {
            runCatching { LiveWebSite.LinkedIn.cookieManager().flush() }
        }
        if (change == WebSessionChange.None) return
        scope.launch {
            pages?.reload(LiveWebSite.LinkedIn)
            connections?.refresh()
            if (change == WebSessionChange.SignedIn && autoReturnOnSignIn && !hasAutoReturned) {
                hasAutoReturned = true
                Toast.makeText(context, "Signed in to LinkedIn", Toast.LENGTH_SHORT).show()
                onBack()
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
        checkAndSyncSession(targetUrl)
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
                                when (screenBackAction(customVideoView != null, wv != null && canGoBack, isExternalOAuthUrl(wv?.url))) {
                                    ScreenBackAction.ExitVideo -> exitFullscreenVideo()
                                    ScreenBackAction.PageBack -> wv?.let { navigateWebViewBackOrEscapeOAuth(it, LiveWebSite.LinkedIn) }
                                    ScreenBackAction.Leave -> onBack()
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
                            view: WebView?,
                            detail: RenderProcessGoneDetail?,
                        ): Boolean {
                            exitFullscreenVideo()
                            // This WebView can't be used again: drop it so it is removed and destroyed.
                            if (view != null && webViewRef.value === view) webViewRef.value = null
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

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean {
                            if (view == null || request == null) return false
                            return handleMainFrameUrlOverride(view, request, LiveWebSite.LinkedIn, webConfig.current)
                        }

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): WebResourceResponse? =
                            screenRequestResponse(LiveWebSite.LinkedIn, webConfig.current, request, privacyCounters)
                                ?: super.shouldInterceptRequest(view, request)

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

                        override fun onPageFinished(
                            view: WebView,
                            url: String?,
                        ) {
                            super.onPageFinished(view, url)
                            isPageLoading = false
                            val committed = url ?: view.url
                            webConfig.runScriptsAfterLoad(view, committed)
                            committed?.let {
                                currentUrl = it
                                canGoBack = computeWebViewCanGoBack(view.canGoBack(), it)
                            }
                            val host = committed?.let { runCatching { Uri.parse(it).host }.getOrNull() }
                            if (LinkedInFeedParser.isValidLinkedInHost(host)) {
                                checkAndSyncSession(committed)
                            }
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
                            if (LinkedInFeedParser.isValidLinkedInHost(host)) {
                                checkAndSyncSession(committed)
                            }
                        }
                    }
                }
            val chromeClient =
                remember {
                    object : LockAwareWebChromeClient() {
                        override fun onCreateWindow(
                            view: WebView?,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: Message?,
                        ): Boolean {
                            if (view == null) return false
                            return handleCreateWindow(view, LiveWebSite.LinkedIn, webConfig.current, isUserGesture, resultMsg)
                        }

                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                            if (consoleMessage?.message() == bookmarkBridgeMsg) {
                                // Page script can post this at any time: ignore it while App Lock is up,
                                // and re-check when the async extraction returns.
                                runIfUnlocked {
                                    webViewRef.value?.let { wv ->
                                        wv.post {
                                            extractPostFromWebView(LiveWebSite.LinkedIn, wv) { extracted ->
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

                        override fun onProgressChanged(
                            view: WebView,
                            newProgress: Int,
                        ) {
                            super.onProgressChanged(view, newProgress)
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
                            view: WebView?,
                            callback: ValueCallback<Array<Uri>>?,
                            fileChooserParams: WebChromeClient.FileChooserParams?,
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
                }
            if (!rendererGone) {
                key(webViewGeneration) {
                    val webViewState = rememberWebViewState(restoreUrl ?: targetUrl)
                    WebView(
                        state = webViewState,
                        modifier = Modifier.fillMaxSize(),
                        client = webViewClient,
                        chromeClient = chromeClient,
                        onCreated = { webView ->
                            webView.useProfileOf(LiveWebSite.LinkedIn)
                            webView.hardenScreenWebView(webBackgroundColor)
                            webConfig.applyTo(webView)
                            webViewRef.value = webView
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
                        // clearAndReload below already reloads the home tab for this sign-out.
                        sessionTracker.update(isSignedIn = false)
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
