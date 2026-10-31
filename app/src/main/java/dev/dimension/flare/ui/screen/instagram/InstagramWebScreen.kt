package dev.dimension.flare.ui.screen.instagram

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup.LayoutParams
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.res.stringResource
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
import compose.icons.fontawesomeicons.solid.EllipsisVertical
import compose.icons.fontawesomeicons.solid.RotateRight
import compose.icons.fontawesomeicons.solid.Rss
import compose.icons.fontawesomeicons.solid.ShieldHalved
import compose.icons.fontawesomeicons.solid.TrashCan
import compose.icons.fontawesomeicons.solid.Xmark
import dev.dimension.flare.R
import dev.dimension.flare.data.datasource.instagram.InstagramFeedService
import dev.dimension.flare.data.datasource.instagram.InstagramSyncService
import dev.dimension.flare.data.readinglist.ReadingListItem
import dev.dimension.flare.data.readinglist.ReadingListRepository
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareDropdownMenu
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.screen.home.LiveWebSite
import dev.dimension.flare.ui.screen.home.cookieManager
import dev.dimension.flare.ui.screen.home.useProfileOf
import dev.dimension.flare.ui.screen.home.webStorage
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream

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
    onNavigateToRss: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val feedSyncedMsg = stringResource(R.string.instagram_feed_synced)
    val syncUnableMsg = stringResource(R.string.instagram_sync_unable)
    val loginToSyncMsg = stringResource(R.string.instagram_login_to_sync)
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
    val committedUrlHolder =
        remember {
            object {
                @Volatile
                var url: String? = targetUrl
            }
        }
    val isSavedState by remember(currentUrl) { readingListRepo.isSaved(currentUrl) }.collectAsState(false)
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var pageProgress by remember { mutableFloatStateOf(0f) }
    var isPageLoading by remember { mutableStateOf(true) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var isDockExpanded by remember { mutableStateOf(false) }
    var isFeedSynced by remember { mutableStateOf(false) }

    val checkAndSyncSession =
        remember(instagramSyncService) {
            {
                val cookies = LiveWebSite.Instagram.cookieManager().getCookie(INSTAGRAM_HOME_URL)
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

    LaunchedEffect(Unit) {
        isFeedSynced = instagramSyncService.isInstagramAccountRegistered()
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
                        LiveWebSite.Instagram.cookieManager().removeAllCookies(null)
                        LiveWebSite.Instagram.webStorage().deleteAllData()
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
                                )
                                Text(
                                    text = stringResource(if (isFeedSynced) R.string.web_status_synced else R.string.web_status_syncing),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            IconButton(
                                onClick = { onNavigateToRss.invoke("https://instagram.com/$detectedProfile") },
                                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                            ) {
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
                                if (currentUrl.isNotBlank()) {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        uriHandler.openUri(currentUrl)
                                    }
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
                            FlareDropdownMenu(
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
                                    text = { Text("Reload") },
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
                    view.useProfileOf(LiveWebSite.Instagram)
                    webViewRef.value = view
                    with(view.settings) {
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
                    LiveWebSite.Instagram.cookieManager().setAcceptThirdPartyCookies(view, false)

                    view.webViewClient =
                        object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView,
                                request: WebResourceRequest,
                            ): WebResourceResponse? = super.shouldInterceptRequest(view, request)

                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean {
                                val url = request.url.toString()
                                val host = request.url.host
                                if (InstagramFeedService.isValidInstagramHost(host)) {
                                    return false
                                }
                                return if (url.startsWith("http://") || url.startsWith("https://")) {
                                    runCatching {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, request.url)
                                        view.context.startActivity(intent)
                                    }
                                    true
                                } else {
                                    // Deep links like instagram://, market://
                                    uriHandler.openUri(url)
                                    true
                                }
                            }

                            override fun onPageStarted(
                                view: WebView,
                                url: String?,
                                favicon: android.graphics.Bitmap?,
                            ) {
                                super.onPageStarted(view, url, favicon)
                                val committed = url ?: view.url
                                committedUrlHolder.url = committed
                                committed?.let {
                                    currentUrl = it
                                    canGoBack = view.canGoBack()
                                    canGoForward = view.canGoForward()
                                }
                                isPageLoading = true
                            }

                            override fun doUpdateVisitedHistory(
                                view: WebView,
                                url: String?,
                                isReload: Boolean,
                            ) {
                                super.doUpdateVisitedHistory(view, url, isReload)
                                val committed = url ?: view.url
                                committedUrlHolder.url = committed
                                committed?.let {
                                    currentUrl = it
                                    canGoBack = view.canGoBack()
                                    canGoForward = view.canGoForward()
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
                                committedUrlHolder.url = committed
                                committed?.let {
                                    currentUrl = it
                                    canGoBack = view.canGoBack()
                                    canGoForward = view.canGoForward()
                                }
                                isPageLoading = false
                                val host = committed?.let { runCatching { Uri.parse(it).host }.getOrNull() }
                                if (InstagramFeedService.isValidInstagramHost(host)) {
                                    checkAndSyncSession()
                                }
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
                        modifier = Modifier.size(48.dp),
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
                            modifier = Modifier.size(48.dp),
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
                            modifier = Modifier.size(48.dp),
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
                            modifier = Modifier.size(48.dp),
                        ) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.RotateRight,
                                contentDescription = "Reload",
                                modifier = Modifier.size(16.dp),
                            )
                        }

                        IconButton(
                            onClick = { showPrivacyDialog = true },
                            modifier = Modifier.size(48.dp),
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
                                val cookies = LiveWebSite.Instagram.cookieManager().getCookie(INSTAGRAM_HOME_URL)
                                if (!cookies.isNullOrBlank() && InstagramFeedService.hasActiveSession(cookies)) {
                                    scope.launch {
                                        val ok = instagramSyncService.syncSession(cookies)
                                        isFeedSynced = ok
                                        val msg = if (ok) feedSyncedMsg else syncUnableMsg
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast
                                        .makeText(
                                            context,
                                            loginToSyncMsg,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                }
                            },
                            modifier = Modifier.size(48.dp),
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
                            modifier = Modifier.size(48.dp),
                        ) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.ArrowUpRightFromSquare,
                                contentDescription = "Open External",
                                modifier = Modifier.size(16.dp),
                            )
                        }

                        IconButton(
                            onClick = { isDockExpanded = false },
                            modifier = Modifier.size(48.dp),
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
