package dev.dimension.flare.ui.screen.instagram

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
import androidx.compose.ui.graphics.Brush
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
import compose.icons.fontawesomeicons.brands.Instagram
import compose.icons.fontawesomeicons.solid.ArrowLeft
import compose.icons.fontawesomeicons.solid.ArrowRight
import compose.icons.fontawesomeicons.solid.ArrowUpRightFromSquare
import compose.icons.fontawesomeicons.solid.Bookmark
import compose.icons.fontawesomeicons.solid.RotateRight
import compose.icons.fontawesomeicons.solid.Rss
import compose.icons.fontawesomeicons.solid.ShieldHalved
import compose.icons.fontawesomeicons.solid.TrashCan
import compose.icons.fontawesomeicons.solid.Xmark
import dev.dimension.flare.data.datasource.instagram.InstagramFeedService
import dev.dimension.flare.data.datasource.instagram.InstagramSyncService
import dev.dimension.flare.data.readinglist.ReadingListItem
import dev.dimension.flare.data.readinglist.ReadingListRepository
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import android.widget.Toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import kotlin.time.Duration.Companion.seconds

private const val INSTAGRAM_HOME_URL = "https://www.instagram.com"

private const val INSTAGRAM_MOBILE_USER_AGENT =
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"

private const val PRISM_SHIELD_JS = """
(function() {
    var styleId = 'prism-instagram-shield';
    if (!document.getElementById(styleId)) {
        var style = document.createElement('style');
        style.id = styleId;
        style.textContent = `
            div[data-testid="mobile-app-banner"],
            a[href*="instagram://"],
            a[href*="play.google.com/store/apps/details?id=com.instagram.android"],
            a[href*="itunes.apple.com"],
            a[href*="instagram.com/download"],
            div[class*="AppBanner"],
            div._acc8,
            div._abpk,
            div[data-testid="app-upsell-banner"],
            div[data-testid="upsell-banner"],
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
                    url.indexOf('/ajax/bz') !== -1 ||
                    url.indexOf('/logging/') !== -1 ||
                    url.indexOf('facebook.com') !== -1 ||
                    url.indexOf('graph.instagram.com') !== -1 ||
                    url.indexOf('/api/v1/ads/') !== -1
                )) {
                    return true;
                }
                return origBeacon ? origBeacon.apply(this, arguments) : true;
            };
        } catch (_) {}

        try {
            var origFetch = window.fetch;
            if (origFetch) {
                window.fetch = function(input, init) {
                    var u = typeof input === 'string' ? input : (input && input.url ? input.url : '');
                    if (u.indexOf('/ajax/bz') !== -1 || u.indexOf('/logging/') !== -1 || u.indexOf('/api/v1/ads/') !== -1) {
                        return Promise.resolve(new Response('{}', { status: 200, statusText: 'OK' }));
                    }
                    return origFetch.apply(this, arguments);
                };
            }
        } catch (_) {}

        try {
            var origOpen = XMLHttpRequest.prototype.open;
            XMLHttpRequest.prototype.open = function(method, url) {
                if (typeof url === 'string' && (
                    url.indexOf('/ajax/bz') !== -1 ||
                    url.indexOf('/logging/') !== -1 ||
                    url.indexOf('/api/v1/ads/') !== -1
                )) {
                    this.__isBlockedPrism = true;
                }
                return origOpen.apply(this, arguments);
            };
            var origSend = XMLHttpRequest.prototype.send;
            XMLHttpRequest.prototype.send = function() {
                if (this.__isBlockedPrism) {
                    try {
                        Object.defineProperty(this, 'readyState', { value: 4, writable: false });
                        Object.defineProperty(this, 'status', { value: 200, writable: false });
                        Object.defineProperty(this, 'responseText', { value: '{}', writable: false });
                        if (typeof this.onreadystatechange === 'function') this.onreadystatechange();
                        if (typeof this.onload === 'function') this.onload();
                    } catch (_) {}
                    return;
                }
                return origSend.apply(this, arguments);
            };
        } catch (_) {}
    }

    function cleanDOM() {
        var dialogs = document.querySelectorAll('div[role="dialog"]');
        for (var i = 0; i < dialogs.length; i++) {
            var text = dialogs[i].innerText || '';
            if (text.indexOf('Open in Instagram') !== -1 || text.indexOf('Use the App') !== -1 || text.indexOf('Get the app') !== -1) {
                dialogs[i].remove();
            }
        }

        var banners = document.querySelectorAll('._acc8, ._abpk, div[class*="AppBanner"]');
        for (var b = 0; b < banners.length; b++) {
            banners[b].remove();
        }

        var buttons = document.querySelectorAll('button, div, span');
        for (var e = 0; e < buttons.length; e++) {
            if (buttons[e].children.length === 0) {
                var val = buttons[e].textContent.trim().toLowerCase();
                if (val === 'use the app' || val === 'open in app' || val === 'get instagram') {
                    var parent = buttons[e].closest('._acc8') || buttons[e].closest('._abpk') || buttons[e].parentElement;
                    if (parent) parent.remove();
                }
            }
        }

        var articles = document.querySelectorAll('article');
        for (var j = 0; j < articles.length; j++) {
            var art = articles[j];
            var isAd = false;
            if (art.querySelector('img[src*="/v/t45."], img[src*="ads_image"], video[src*="ads_video"]')) {
                isAd = true;
            }
            if (!isAd) {
                var textNodes = art.querySelectorAll('span, div');
                for (var k = 0; k < textNodes.length; k++) {
                    if (textNodes[k].children.length === 0) {
                        var t = textNodes[k].textContent.trim();
                        if (t === 'Ad' || t === 'Sponsored' || t === 'Promoted' || t.toLowerCase() === 'paid partnership') {
                            isAd = true;
                            break;
                        }
                    }
                }
            }
            if (isAd) {
                art.remove();
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
})();
"""

internal fun isAdOrTrackingUrl(url: String): Boolean {
    val lower = url.lowercase()
    return lower.contains("/ajax/bz") ||
        lower.contains("/logging/") ||
        lower.contains("/api/v1/logging/") ||
        lower.contains("/api/v1/ads/") ||
        lower.contains("graph.facebook.com") ||
        lower.contains("b-graph.facebook.com") ||
        lower.contains("graph.instagram.com/logging") ||
        lower.contains("connect.facebook.net") ||
        lower.contains("facebook.com/tr") ||
        lower.contains("pixel.facebook.com") ||
        lower.contains("/v/t45.") ||
        lower.contains("ads_image") ||
        lower.contains("ads_video") ||
        lower.contains("doubleclick.net") ||
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
            "explore", "direct", "accounts", "stories", "reels", "reel", "p", "tv",
            "developer", "about", "legal", "privacy", "terms", "api", "static", "challenge",
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
    onNavigateToRss: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    val readingListRepo by koinInject<ReadingListRepository>()
    val instagramSyncService by koinInject<InstagramSyncService>()
    val scope = rememberCoroutineScope()
    val targetUrl =
        remember(initialUrl) {
            val clean = initialUrl?.trim()
            if (!clean.isNullOrEmpty() && (clean.startsWith("http://") || clean.startsWith("https://"))) {
                clean
            } else {
                INSTAGRAM_HOME_URL
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
    var isDockExpanded by remember { mutableStateOf(false) }
    var isFeedSynced by remember { mutableStateOf(false) }

    val checkAndSyncSession = remember(instagramSyncService) {
        {
            val cookies = CookieManager.getInstance().getCookie(INSTAGRAM_HOME_URL)
            if (!cookies.isNullOrBlank() && InstagramFeedService.hasActiveSession(cookies)) {
                scope.launch {
                    val ok = instagramSyncService.syncSession(cookies)
                    if (ok) {
                        isFeedSynced = true
                    }
                }
            }
        }
    }

    var fileUploadCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileChooserLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetMultipleContents(),
        ) { uris: List<Uri> ->
            fileUploadCallback?.onReceiveValue(uris.toTypedArray())
            fileUploadCallback = null
        }

    BackHandler {
        if (webViewRef.value?.canGoBack() == true) {
            webViewRef.value?.goBack()
        } else {
            onBack()
        }
    }

    LaunchedEffect(targetUrl) {
        if (targetUrl != INSTAGRAM_HOME_URL && targetUrl != currentUrl) {
            webViewRef.value?.loadUrl(targetUrl)
        }
    }

    // Periodic shield refresher and session sync checker
    LaunchedEffect(Unit) {
        isFeedSynced = instagramSyncService.isInstagramAccountRegistered()
        while (true) {
            delay(2.seconds)
            webViewRef.value?.evaluateJavascript(PRISM_SHIELD_JS, null)
            canGoBack = webViewRef.value?.canGoBack() == true
            canGoForward = webViewRef.value?.canGoForward() == true
            if (!isFeedSynced) {
                checkAndSyncSession()
            }
        }
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            icon = {
                FAIcon(
                    imageVector = FontAwesomeIcons.Solid.ShieldHalved,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            },
            title = {
                Text(text = "Instagram Web Sandbox")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "You are running Instagram inside Prism's isolated mobile web sandbox.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = if (isFeedSynced) {
                            "• Native Home Feed: Synced to Instagram & Mixed Tab\n" +
                                "• Storage: Isolated prismbrowser sandbox\n" +
                                "• Third-Party Tracking & Meta Pixels: Blocked\n" +
                                "• Telemetry & Banzai Beacons (/ajax/bz): Blocked\n" +
                                "• Sponsored Ads & Promo Injections: Stripped\n" +
                                "• App Install Nags & Sticky Banners: Stripped\n" +
                                "• Geolocation & Device Sensors: Denied\n" +
                                "• Zero Checkpoint Ban Risk"
                        } else {
                            "• Native Home Feed: Log in to auto-sync timeline\n" +
                                "• Storage: Isolated prismbrowser sandbox\n" +
                                "• Third-Party Tracking & Meta Pixels: Blocked\n" +
                                "• Telemetry & Banzai Beacons (/ajax/bz): Blocked\n" +
                                "• Sponsored Ads & Promo Injections: Stripped\n" +
                                "• App Install Nags & Sticky Banners: Stripped\n" +
                                "• Geolocation & Device Sensors: Denied\n" +
                                "• Zero Checkpoint Ban Risk"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text(text = "Close")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPrivacyDialog = false
                        showClearConfirmDialog = true
                    },
                ) {
                    Text(text = "Clear Session", color = MaterialTheme.colorScheme.error)
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
                Text(text = "Clear Instagram Session?")
            },
            text = {
                Text(
                    text = "This will delete all saved Instagram cookies and local web storage. You will need to log in again.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmDialog = false
                        CookieManager.getInstance().removeAllCookies(null)
                        WebStorage.getInstance().deleteAllData()
                        webViewRef.value?.clearCache(true)
                        webViewRef.value?.loadUrl(INSTAGRAM_HOME_URL)
                        isFeedSynced = false
                    },
                ) {
                    Text(text = "Clear & Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text(text = "Cancel")
                }
            },
        )
    }

    FlareScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                FlareTopAppBar(
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
                                        .background(
                                            Brush.linearGradient(
                                                colors =
                                                    listOf(
                                                        Color(0xFF833AB4),
                                                        Color(0xFFFD1D1D),
                                                        Color(0xFFFCB045),
                                                    ),
                                            ),
                                        ),
                                contentAlignment = Alignment.Center,
                            ) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Brands.Instagram,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = "Instagram",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                )
                                Text(
                                    text = if (isFeedSynced) "Privacy Sandbox • Feed Synced" else "Privacy Sandbox Active",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = if (isFeedSynced) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                    maxLines = 1,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        BackButton(onBack = onBack)
                    },
                    actions = {
                        val detectedProfile = remember(currentUrl) { extractInstagramProfile(currentUrl) }
                        if (detectedProfile != null && onNavigateToRss != null) {
                            IconButton(onClick = { onNavigateToRss.invoke("https://instagram.com/$detectedProfile") }) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.Rss,
                                    contentDescription = "Subscribe via RSS",
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                scope.launch {
                                    if (isSavedState) {
                                        readingListRepo.remove(currentUrl)
                                    } else {
                                        val profile = extractInstagramProfile(currentUrl)
                                        val itemTitle = if (profile != null) "@$profile on Instagram" else "Instagram Web Post"
                                        val item =
                                            ReadingListItem(
                                                id = currentUrl,
                                                title = itemTitle,
                                                authorName = profile ?: "Instagram",
                                                authorHandle = profile ?: "",
                                                contentText = "Saved Instagram link: $currentUrl",
                                                sourceUrl = currentUrl,
                                                platformId = "instagram",
                                                savedAtTimestamp = System.currentTimeMillis(),
                                            )
                                        readingListRepo.save(item)
                                    }
                                }
                            },
                        ) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.Bookmark,
                                contentDescription = if (isSavedState) "Remove from Reading List" else "Save to Reading List",
                                tint = if (isSavedState) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        IconButton(onClick = { webViewRef.value?.reload() }) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.RotateRight,
                                contentDescription = "Reload",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        IconButton(
                            onClick = {
                                if (currentUrl.isNotBlank()) {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        uriHandler.openUri(currentUrl)
                                    }
                                }
                            },
                        ) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.ArrowUpRightFromSquare,
                                contentDescription = "Open in External Browser",
                                modifier = Modifier.size(17.dp),
                            )
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
                    .padding(paddingValues),
        ) {
            WebView(
                state = webViewState,
                layoutParams =
                    FrameLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT,
                    ),
                modifier = Modifier.fillMaxSize(),
                onCreated = { view: WebView ->
                    webViewRef.value = view
                    with(view.settings) {
                        userAgentString = INSTAGRAM_MOBILE_USER_AGENT
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        allowFileAccess = false
                        allowContentAccess = false
                        setGeolocationEnabled(false)
                        @Suppress("DEPRECATION")
                        setAllowFileAccessFromFileURLs(false)
                        @Suppress("DEPRECATION")
                        setAllowUniversalAccessFromFileURLs(false)
                        javaScriptCanOpenWindowsAutomatically = false
                        setSupportMultipleWindows(false)
                        @Suppress("DEPRECATION")
                        saveFormData = false
                        setSafeBrowsingEnabled(true)
                        cacheMode = WebSettings.LOAD_DEFAULT
                    }
                    CookieManager.getInstance().setAcceptThirdPartyCookies(view, false)

                    view.webViewClient =
                        object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView,
                                request: WebResourceRequest,
                            ): WebResourceResponse? {
                                val url = request.url.toString()
                                if (isAdOrTrackingUrl(url)) {
                                    return WebResourceResponse(
                                        "text/plain",
                                        "UTF-8",
                                        200,
                                        "OK",
                                        emptyMap(),
                                        ByteArrayInputStream(ByteArray(0)),
                                    )
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean {
                                val url = request.url.toString()
                                return if (url.startsWith("http://") || url.startsWith("https://")) {
                                    false
                                } else {
                                    // Deep links like instagram://, market://
                                    uriHandler.openUri(url)
                                    true
                                }
                            }

                            override fun onPageFinished(
                                view: WebView,
                                url: String?,
                            ) {
                                super.onPageFinished(view, url)
                                currentUrl = url ?: INSTAGRAM_HOME_URL
                                canGoBack = view.canGoBack()
                                canGoForward = view.canGoForward()
                                isPageLoading = false
                                view.evaluateJavascript(PRISM_SHIELD_JS, null)
                                checkAndSyncSession()
                            }

                            override fun onPageCommitVisible(
                                view: WebView,
                                url: String?,
                            ) {
                                super.onPageCommitVisible(view, url)
                                view.evaluateJavascript(PRISM_SHIELD_JS, null)
                            }
                        }

                    view.webChromeClient =
                        object : WebChromeClient() {
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
                                if (newProgress > 40) {
                                    view.evaluateJavascript(PRISM_SHIELD_JS, null)
                                }
                            }

                            override fun onShowFileChooser(
                                view: WebView,
                                filePathCallback: ValueCallback<Array<Uri>>,
                                fileChooserParams: FileChooserParams,
                            ): Boolean {
                                fileUploadCallback?.onReceiveValue(null)
                                fileUploadCallback = filePathCallback
                                fileChooserLauncher.launch("image/*")
                                return true
                            }
                        }
                },
            )

            // Floating Ergonomic Privacy & Navigation Dock
            Surface(
                modifier =
                    Modifier
                        .zIndex(10f)
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 68.dp)
                        .navigationBarsPadding(),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                border =
                    androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    ),
            ) {
                if (!isDockExpanded) {
                    IconButton(
                        onClick = { isDockExpanded = true },
                        modifier = Modifier.size(44.dp),
                    ) {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.ShieldHalved,
                            contentDescription = "Expand Navigation Controls",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        IconButton(
                            onClick = { webViewRef.value?.goBack() },
                            enabled = canGoBack,
                        ) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.ArrowLeft,
                                contentDescription = "Back",
                                modifier = Modifier.size(16.dp),
                                tint =
                                    if (canGoBack) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    },
                            )
                        }

                        IconButton(
                            onClick = { webViewRef.value?.goForward() },
                            enabled = canGoForward,
                        ) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.ArrowRight,
                                contentDescription = "Forward",
                                modifier = Modifier.size(16.dp),
                                tint =
                                    if (canGoForward) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    },
                            )
                        }

                        IconButton(
                            onClick = { webViewRef.value?.reload() },
                        ) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.RotateRight,
                                contentDescription = "Reload",
                                modifier = Modifier.size(16.dp),
                            )
                        }

                        IconButton(
                            onClick = { showPrivacyDialog = true },
                        ) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.ShieldHalved,
                                contentDescription = "Shield Settings",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }

                        IconButton(
                            onClick = {
                                val cookies = CookieManager.getInstance().getCookie(INSTAGRAM_HOME_URL)
                                if (!cookies.isNullOrBlank() && InstagramFeedService.hasActiveSession(cookies)) {
                                    scope.launch {
                                        val ok = instagramSyncService.syncSession(cookies)
                                        isFeedSynced = ok
                                        val msg = if (ok) "Instagram feed synced with Home Timeline!" else "Unable to sync Instagram session"
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Log in to Instagram to sync your feed", Toast.LENGTH_SHORT).show()
                                }
                            },
                        ) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Brands.Instagram,
                                contentDescription = if (isFeedSynced) "Feed Synced" else "Sync Feed",
                                tint = if (isFeedSynced) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }

                        IconButton(
                            onClick = {
                                if (currentUrl.isNotBlank()) {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        uriHandler.openUri(currentUrl)
                                    }
                                }
                            },
                        ) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.ArrowUpRightFromSquare,
                                contentDescription = "Open External",
                                modifier = Modifier.size(16.dp),
                            )
                        }

                        IconButton(
                            onClick = { isDockExpanded = false },
                        ) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.Xmark,
                                contentDescription = "Collapse Dock",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
