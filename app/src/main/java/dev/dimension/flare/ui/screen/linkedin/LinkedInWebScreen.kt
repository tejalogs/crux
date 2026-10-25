package dev.dimension.flare.ui.screen.linkedin

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup.LayoutParams
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kevinnzou.web.WebView
import com.kevinnzou.web.rememberWebViewState
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.brands.LinkedinIn
import compose.icons.fontawesomeicons.solid.ArrowLeft
import compose.icons.fontawesomeicons.solid.ArrowRight
import compose.icons.fontawesomeicons.solid.ArrowUpRightFromSquare
import compose.icons.fontawesomeicons.solid.Bookmark
import compose.icons.fontawesomeicons.solid.RotateRight
import compose.icons.fontawesomeicons.solid.ShieldHalved
import compose.icons.fontawesomeicons.solid.TrashCan
import compose.icons.fontawesomeicons.solid.Xmark
import dev.dimension.flare.data.datasource.linkedin.LinkedInFeedService
import dev.dimension.flare.data.datasource.linkedin.LinkedInSyncService
import dev.dimension.flare.data.readinglist.ReadingListItem
import dev.dimension.flare.data.readinglist.ReadingListRepository
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import kotlin.time.Duration.Companion.seconds

private const val LINKEDIN_HOME_URL = "https://www.linkedin.com/feed/"

private const val LINKEDIN_MOBILE_USER_AGENT =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

private const val PRISM_LINKEDIN_SHIELD_JS = """
(function() {
    var styleId = 'prism-linkedin-shield';
    if (!document.getElementById(styleId)) {
        var style = document.createElement('style');
        style.id = styleId;
        style.textContent = `
            div[class*="app-banner"],
            div[class*="upsell-banner"],
            div[data-tracking-control-name="public_profile_contextual-sign-in-modal"],
            div[data-tracking-control-name="cold_join"],
            a[href*="linkedin.com/download"],
            a[href*="play.google.com/store/apps/details?id=com.linkedin.android"],
            div.modal__overlay--active[data-tracking-control-name*="app_banner"],
            html, body { -webkit-tap-highlight-color: transparent; }
        `;
        (document.head || document.documentElement).appendChild(style);
    }

    if (!window.__prismTelemetryBlocked) {
        window.__prismTelemetryBlocked = true;
        try {
            var origBeacon = navigator.sendBeacon;
            navigator.sendBeacon = function(url) {
                if (typeof url === 'string' && (
                    url.indexOf('snap.licdn.com') !== -1 ||
                    url.indexOf('px.ads.linkedin.com') !== -1 ||
                    url.indexOf('platform.linkedin.com/litms/') !== -1 ||
                    url.indexOf('/li/track') !== -1 ||
                    url.indexOf('google-analytics.com') !== -1
                )) {
                    return true;
                }
                return origBeacon ? origBeacon.apply(this, arguments) : true;
            };
        } catch (_) {}
    }

    function cleanDOM() {
        var dialogs = document.querySelectorAll('div[role="dialog"]');
        for (var i = 0; i < dialogs.length; i++) {
            var text = dialogs[i].innerText || '';
            if (text.indexOf('Open in LinkedIn') !== -1 || text.indexOf('Use the app') !== -1 || text.indexOf('Join LinkedIn') !== -1) {
                dialogs[i].remove();
            }
        }
    }

    if (!window.__prismObserverActive && document.body) {
        window.__prismObserverActive = true;
        try {
            var obs = new MutationObserver(function() {
                cleanDOM();
            });
            obs.observe(document.body, { childList: true, subtree: true });
        } catch (_) {}
    }
    cleanDOM();

    if (!window.__prismFeedHooked) {
        window.__prismFeedHooked = true;
        try {
            var origFetch = window.fetch;
            if (origFetch) {
                window.fetch = async function() {
                    var response = await origFetch.apply(this, arguments);
                    try {
                        var arg = arguments[0];
                        var u = typeof arg === 'string' ? arg : (arg ? arg.url : '');
                        if (u && (u.indexOf('voyager/api/feed') !== -1 || u.indexOf('feedUpdates') !== -1 || u.indexOf('voyager/api/graphql') !== -1 || u.indexOf('dash/updates') !== -1 || u.indexOf('updatesV2') !== -1)) {
                            response.clone().text().then(function(t) {
                                if (window.LinkedInPrismBridge && t) {
                                    window.LinkedInPrismBridge.onFeedData(t);
                                }
                            });
                        }
                    } catch (_) {}
                    return response;
                };
            }

            var origOpen = XMLHttpRequest.prototype.open;
            var origSend = XMLHttpRequest.prototype.send;
            XMLHttpRequest.prototype.open = function(method, url) {
                this.__prismUrl = url;
                return origOpen.apply(this, arguments);
            };
            XMLHttpRequest.prototype.send = function() {
                this.addEventListener('load', function() {
                    try {
                        if (this.__prismUrl && (
                            this.__prismUrl.indexOf('voyager/api/feed') !== -1 ||
                            this.__prismUrl.indexOf('feedUpdates') !== -1 ||
                            this.__prismUrl.indexOf('voyager/api/graphql') !== -1 ||
                            this.__prismUrl.indexOf('dash/updates') !== -1 ||
                            this.__prismUrl.indexOf('updatesV2') !== -1
                        )) {
                            if (window.LinkedInPrismBridge && this.responseText) {
                                window.LinkedInPrismBridge.onFeedData(this.responseText);
                            }
                        }
                    } catch (_) {}
                });
                return origSend.apply(this, arguments);
            };
        } catch (_) {}
    }
})();
"""

private const val PRISM_LINKEDIN_FETCH_FEED_JS = """
(function() {
    try {
        var csrf = (document.cookie.match(/JSESSIONID="?([^";]+)"?/) || [])[1] || '';
        if (csrf && window.LinkedInPrismBridge) {
            fetch('/voyager/api/feed/dash/updates?count=20&q=feedUpdates', {
                headers: { 'csrf-token': csrf }
            }).then(function(r) { return r.text(); }).then(function(t) {
                if (t && window.LinkedInPrismBridge) window.LinkedInPrismBridge.onFeedData(t);
            }).catch(function() {
                fetch('/voyager/api/feed/updatesV2?count=20&q=feedUpdates', {
                    headers: { 'csrf-token': csrf }
                }).then(function(r) { return r.text(); }).then(function(t) {
                    if (t && window.LinkedInPrismBridge) window.LinkedInPrismBridge.onFeedData(t);
                }).catch(function() {});
            });
        }
    } catch (_) {}
})();
"""

internal fun isLinkedInAdOrTrackingUrl(url: String): Boolean {
    val lower = url.lowercase()
    return lower.contains("snap.licdn.com") ||
        lower.contains("px.ads.linkedin.com") ||
        lower.contains("ads.linkedin.com") ||
        lower.contains("platform.linkedin.com/litms/") ||
        lower.contains("/li/track") ||
        lower.contains("doubleclick.net") ||
        lower.contains("google-analytics.com") ||
        lower.contains("googletagmanager.com") ||
        lower.contains("scorecardresearch.com") ||
        lower.contains("connect.facebook.net") ||
        lower.contains("clarity.ms") ||
        lower.contains("branch.io") ||
        lower.contains("adjust.com") ||
        lower.contains("appsflyer.com")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LinkedInWebScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialUrl: String? = null,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    val readingListRepo by koinInject<ReadingListRepository>()
    val linkedInSyncService by koinInject<LinkedInSyncService>()
    val scope = rememberCoroutineScope()
    val targetUrl =
        remember(initialUrl) {
            val clean = initialUrl?.trim()
            if (!clean.isNullOrEmpty() && (clean.startsWith("http://") || clean.startsWith("https://"))) {
                clean
            } else {
                LINKEDIN_HOME_URL
            }
        }
    val webViewState = rememberWebViewState(targetUrl)

    var currentUrl by remember { mutableStateOf(targetUrl) }
    val isSavedState by remember(currentUrl) { readingListRepo.isSaved(currentUrl) }.collectAsState(false)
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var pageProgress by remember { mutableFloatStateOf(0f) }
    var isPageLoading by remember { mutableStateOf(true) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var isFeedSynced by remember { mutableStateOf(false) }
    var isDockExpanded by remember { mutableStateOf(false) }

    fun checkAndSyncSession(silent: Boolean = false) {
        val cookies = CookieManager.getInstance().getCookie("https://www.linkedin.com")
        if (!cookies.isNullOrBlank() && LinkedInFeedService.hasActiveSession(cookies)) {
            webViewRef.value?.evaluateJavascript(PRISM_LINKEDIN_FETCH_FEED_JS, null)
            scope.launch {
                val synced = linkedInSyncService.syncSession(cookies)
                if (synced) {
                    isFeedSynced = true
                    if (!silent) {
                        Toast.makeText(context, "LinkedIn feed synced with Prism!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        checkAndSyncSession(silent = true)
        while (true) {
            delay(10.seconds)
            checkAndSyncSession(silent = true)
        }
    }

    BackHandler(enabled = canGoBack) {
        webViewRef.value?.goBack()
    }

    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val filePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetMultipleContents(),
        ) { uris: List<Uri> ->
            filePathCallback?.onReceiveValue(uris.toTypedArray())
            filePathCallback = null
        }

    FlareScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                FlareTopAppBar(
                    navigationIcon = {
                        BackButton(onBack = onBack)
                    },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0A66C2)),
                                contentAlignment = Alignment.Center,
                            ) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Brands.LinkedinIn,
                                    contentDescription = "LinkedIn",
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp),
                                )
                            }
                            Text(
                                text = "LinkedIn",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            )
                            if (isFeedSynced) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF0A66C2).copy(alpha = 0.15f))
                                        .border(0.6.dp, Color(0xFF0A66C2).copy(alpha = 0.40f), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = "Feed Synced",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xFF0A66C2),
                                            fontWeight = FontWeight.Bold,
                                        ),
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    if (isSavedState) {
                                        readingListRepo.remove(currentUrl)
                                        Toast.makeText(context, "Removed from Reading List", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val title = webViewRef.value?.title?.takeIf { it.isNotBlank() } ?: "LinkedIn Web Post"
                                        readingListRepo.save(
                                            ReadingListItem(
                                                id = currentUrl,
                                                title = title,
                                                authorName = "LinkedIn",
                                                authorHandle = "",
                                                contentText = "Saved LinkedIn link: $currentUrl",
                                                sourceUrl = currentUrl,
                                                platformId = "linkedin",
                                                savedAtTimestamp = System.currentTimeMillis(),
                                            ),
                                        )
                                        Toast.makeText(context, "Saved to Reading List", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                        ) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.Bookmark,
                                contentDescription = if (isSavedState) "Remove from Reading List" else "Save to Reading List",
                                tint = if (isSavedState) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        IconButton(onClick = { showPrivacyDialog = true }) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.ShieldHalved,
                                contentDescription = "Privacy Shield",
                                tint = Color(0xFF0A66C2),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
                if (isPageLoading) {
                    LinearProgressIndicator(
                        progress = { pageProgress },
                        modifier = Modifier.fillMaxWidth().height(2.5.dp),
                        color = Color(0xFF0A66C2),
                        trackColor = Color.Transparent,
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            WebView(
                state = webViewState,
                modifier = Modifier.fillMaxSize(),
                onCreated = { webView ->
                    webViewRef.value = webView
                    webView.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        allowFileAccess = false
                        allowContentAccess = false
                        builtInZoomControls = true
                        displayZoomControls = false
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        userAgentString = LINKEDIN_MOBILE_USER_AGENT
                    }

                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(webView, false)

                    webView.addJavascriptInterface(
                        object {
                            @android.webkit.JavascriptInterface
                            fun onFeedData(json: String) {
                                scope.launch {
                                    linkedInSyncService.cacheFeedPayload(json)
                                }
                            }
                        },
                        "LinkedInPrismBridge",
                    )

                    webView.webViewClient =
                        object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): WebResourceResponse? {
                                val reqUrl = request?.url?.toString() ?: return null
                                if (isLinkedInAdOrTrackingUrl(reqUrl)) {
                                    return WebResourceResponse(
                                        "text/plain",
                                        "UTF-8",
                                        ByteArrayInputStream(ByteArray(0)),
                                    )
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                url?.let {
                                    currentUrl = it
                                    canGoBack = view?.canGoBack() ?: false
                                    canGoForward = view?.canGoForward() ?: false
                                }
                                isPageLoading = true
                                view?.evaluateJavascript(PRISM_LINKEDIN_SHIELD_JS, null)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isPageLoading = false
                                url?.let {
                                    currentUrl = it
                                    canGoBack = view?.canGoBack() ?: false
                                    canGoForward = view?.canGoForward() ?: false
                                }
                                view?.evaluateJavascript(PRISM_LINKEDIN_SHIELD_JS, null)
                                view?.evaluateJavascript(PRISM_LINKEDIN_FETCH_FEED_JS, null)
                                checkAndSyncSession(silent = true)
                            }

                            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                super.doUpdateVisitedHistory(view, url, isReload)
                                url?.let {
                                    currentUrl = it
                                    canGoBack = view?.canGoBack() ?: false
                                    canGoForward = view?.canGoForward() ?: false
                                }
                                checkAndSyncSession(silent = true)
                            }
                        }

                    webView.webChromeClient =
                        object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
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
                                return try {
                                    filePickerLauncher.launch("*/*")
                                    true
                                } catch (e: Exception) {
                                    filePathCallback = null
                                    false
                                }
                            }
                        }
                },
            )

            // Ergonomic Floating Navigation Dock
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 16.dp)
                    .zIndex(10f),
            ) {
                if (!isDockExpanded) {
                    Surface(
                        onClick = { isDockExpanded = true },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .size(46.dp)
                            .border(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), CircleShape),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.ShieldHalved,
                                contentDescription = "Open Navigation Controls",
                                tint = Color(0xFF0A66C2),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                } else {
                    ElevatedCard(
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.border(
                            0.8.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                            RoundedCornerShape(24.dp),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            IconButton(
                                onClick = { webViewRef.value?.goBack() },
                                enabled = canGoBack,
                                modifier = Modifier.size(36.dp),
                            ) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.ArrowLeft,
                                    contentDescription = "Back",
                                    tint = if (canGoBack) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                            IconButton(
                                onClick = { webViewRef.value?.goForward() },
                                enabled = canGoForward,
                                modifier = Modifier.size(36.dp),
                            ) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.ArrowRight,
                                    contentDescription = "Forward",
                                    tint = if (canGoForward) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                            IconButton(
                                onClick = { webViewRef.value?.reload() },
                                modifier = Modifier.size(36.dp),
                            ) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.RotateRight,
                                    contentDescription = "Reload",
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                            IconButton(
                                onClick = {
                                    checkAndSyncSession(silent = false)
                                },
                                modifier = Modifier.size(36.dp),
                            ) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Brands.LinkedinIn,
                                    contentDescription = "Sync Feed",
                                    tint = Color(0xFF0A66C2),
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                            IconButton(
                                onClick = {
                                    uriHandler.openUri(currentUrl)
                                },
                                modifier = Modifier.size(36.dp),
                            ) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.ArrowUpRightFromSquare,
                                    contentDescription = "Open in External Browser",
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                            IconButton(
                                onClick = { isDockExpanded = false },
                                modifier = Modifier.size(36.dp),
                            ) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.Xmark,
                                    contentDescription = "Collapse Dock",
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.ShieldHalved,
                        contentDescription = null,
                        tint = Color(0xFF0A66C2),
                        modifier = Modifier.size(24.dp),
                    )
                    Text(text = "Prism LinkedIn Shield")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Your LinkedIn browsing is sandboxed with strict sovereign privacy rules:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "• 3rd-party tracking scripts & telemetry blocked (Snap, Google Analytics, DoubleClick, px.ads.linkedin.com)\n" +
                            "• App-install nagging popups & redirects suppressed\n" +
                            "• Geolocation & sensor access denied\n" +
                            "• Automatic session sync to native Prism feed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text("Got it")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPrivacyDialog = false
                        showClearConfirmDialog = true
                    },
                ) {
                    Text(
                        text = "Clear Session",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
        )
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Log out of LinkedIn?") },
            text = {
                Text("This will clear all cookies, local cache, and saved session tokens for LinkedIn.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmDialog = false
                        CookieManager.getInstance().removeAllCookies(null)
                        WebStorage.getInstance().deleteAllData()
                        webViewRef.value?.clearCache(true)
                        webViewRef.value?.clearHistory()
                        webViewRef.value?.loadUrl(LINKEDIN_HOME_URL)
                        isFeedSynced = false
                        Toast.makeText(context, "LinkedIn session cleared", Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text(
                        text = "Log Out",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}
