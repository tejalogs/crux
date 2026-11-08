package org.crux.ui.screen.linkedin

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
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.DisposableEffect
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
import compose.icons.fontawesomeicons.brands.LinkedinIn
import compose.icons.fontawesomeicons.solid.ArrowLeft
import compose.icons.fontawesomeicons.solid.ArrowRight
import compose.icons.fontawesomeicons.solid.ArrowUpRightFromSquare
import compose.icons.fontawesomeicons.solid.Bookmark
import compose.icons.fontawesomeicons.solid.EllipsisVertical
import compose.icons.fontawesomeicons.solid.RotateRight
import compose.icons.fontawesomeicons.solid.ShieldHalved
import compose.icons.fontawesomeicons.solid.TrashCan
import compose.icons.fontawesomeicons.solid.Xmark
import kotlinx.coroutines.launch
import org.crux.R
import org.crux.data.datasource.linkedin.LinkedInFeedParser
import org.crux.data.datasource.linkedin.LinkedInFeedService
import org.crux.data.datasource.linkedin.LinkedInSyncService
import org.crux.data.readinglist.ReadingListItem
import org.crux.data.readinglist.ReadingListRepository
import org.crux.di.koinInject
import org.crux.ui.component.BackButton
import org.crux.ui.component.CruxDropdownMenu
import org.crux.ui.component.CruxScaffold
import org.crux.ui.component.CruxTopAppBar
import org.crux.ui.component.FAIcon
import org.crux.ui.screen.home.LiveWebSite
import org.crux.ui.screen.home.WebSessionLifecycleSuspender
import org.crux.ui.screen.home.clearSession
import org.crux.ui.screen.home.cookieManager
import org.crux.ui.screen.home.openInSystemBrowser
import org.crux.ui.screen.home.openLinkOutsideWebView
import org.crux.ui.screen.home.useProfileOf
import java.io.ByteArrayInputStream

private const val LINKEDIN_HOME_URL = "https://www.linkedin.com/feed/"

internal fun isLinkedInHost(host: String?): Boolean = LinkedInFeedParser.isValidLinkedInHost(host)

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
    val readingListRemovedMsg = stringResource(R.string.linkedin_reading_list_removed)
    val readingListSavedMsg = stringResource(R.string.linkedin_reading_list_saved)
    val sessionClearedMsg = stringResource(R.string.linkedin_session_cleared)
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    WebSessionLifecycleSuspender(webView = webViewRef.value)
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

    // Registers the signed-in session as an account row once cookies show a login. It is silent: a row
    // existing says nothing about any feed, so nothing in the UI reports it.
    fun checkAndSyncSession() {
        val cm = LiveWebSite.LinkedIn.cookieManager()
        cm.flush()
        val cookies = cm.getCookie("https://www.linkedin.com").orEmpty() + "; " + cm.getCookie("https://linkedin.com").orEmpty()
        if (LinkedInFeedService.hasActiveSession(cookies)) {
            scope.launch { linkedInSyncService.syncSession(cookies) }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            LiveWebSite.LinkedIn.cookieManager().flush()
        }
    }

    LaunchedEffect(Unit) {
        checkAndSyncSession()
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

    CruxScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                CruxTopAppBar(
                    navigationIcon = {
                        BackButton(onBack = onBack)
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
                                )
                            }
                        }
                    },
                    actions = {
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
                    .padding(innerPadding),
        ) {
            WebView(
                state = webViewState,
                modifier = Modifier.fillMaxSize(),
                onCreated = { webView ->
                    webView.useProfileOf(LiveWebSite.LinkedIn)
                    webViewRef.value = webView
                    webView.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        allowFileAccess = false
                        allowContentAccess = true
                        builtInZoomControls = true
                        displayZoomControls = false
                        loadWithOverviewMode = true
                        useWideViewPort = true
                    }

                    val cookieManager = LiveWebSite.LinkedIn.cookieManager()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(webView, false)

                    webView.webViewClient =
                        object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean {
                                val uri = request?.url ?: return false
                                val host = uri.host
                                if (isLinkedInHost(host)) {
                                    return false
                                }
                                openLinkOutsideWebView(view?.context ?: context, uri.toString(), clean = true)
                                return true
                            }

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): WebResourceResponse? = super.shouldInterceptRequest(view, request)

                            override fun onPageStarted(
                                view: WebView?,
                                url: String?,
                                favicon: android.graphics.Bitmap?,
                            ) {
                                super.onPageStarted(view, url, favicon)
                                val committed = url ?: view?.url
                                committedUrlHolder.url = committed
                                committed?.let {
                                    currentUrl = it
                                    canGoBack = view?.canGoBack() ?: false
                                    canGoForward = view?.canGoForward() ?: false
                                }
                                isPageLoading = true
                            }

                            override fun onPageFinished(
                                view: WebView?,
                                url: String?,
                            ) {
                                super.onPageFinished(view, url)
                                isPageLoading = false
                                val committed = url ?: view?.url
                                committedUrlHolder.url = committed
                                committed?.let {
                                    currentUrl = it
                                    canGoBack = view?.canGoBack() ?: false
                                    canGoForward = view?.canGoForward() ?: false
                                }
                                val host = committed?.let { Uri.parse(it).host }
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
                                committedUrlHolder.url = committed
                                committed?.let {
                                    currentUrl = it
                                    canGoBack = view?.canGoBack() ?: false
                                    canGoForward = view?.canGoForward() ?: false
                                }
                                val host = committed?.let { runCatching { Uri.parse(it).host }.getOrNull() }
                                if (LinkedInFeedParser.isValidLinkedInHost(host)) {
                                    checkAndSyncSession()
                                }
                            }
                        }

                    webView.webChromeClient =
                        object : WebChromeClient() {
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
                modifier =
                    Modifier
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
                        modifier =
                            Modifier
                                .size(48.dp)
                                .border(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), CircleShape),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.ShieldHalved,
                                contentDescription = stringResource(R.string.web_nav_expand),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                } else {
                    ElevatedCard(
                        shape = RoundedCornerShape(24.dp),
                        modifier =
                            Modifier.border(
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
                                modifier = Modifier.size(48.dp),
                            ) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.ArrowLeft,
                                    contentDescription = stringResource(R.string.web_nav_back),
                                    tint =
                                        if (canGoBack) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurface.copy(
                                                alpha = 0.38f,
                                            )
                                        },
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                            IconButton(
                                onClick = { webViewRef.value?.goForward() },
                                enabled = canGoForward,
                                modifier = Modifier.size(48.dp),
                            ) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.ArrowRight,
                                    contentDescription = stringResource(R.string.web_nav_forward),
                                    tint =
                                        if (canGoForward) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                                .copy(
                                                    alpha = 0.38f,
                                                )
                                        },
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                            IconButton(
                                onClick = { webViewRef.value?.reload() },
                                modifier = Modifier.size(48.dp),
                            ) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.RotateRight,
                                    contentDescription = stringResource(R.string.web_nav_reload),
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                            IconButton(
                                onClick = {
                                    openInSystemBrowser(context, currentUrl)
                                },
                                modifier = Modifier.size(48.dp),
                            ) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.ArrowUpRightFromSquare,
                                    contentDescription = stringResource(R.string.web_open_in_browser),
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                            IconButton(
                                onClick = { isDockExpanded = false },
                                modifier = Modifier.size(48.dp),
                            ) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.Xmark,
                                    contentDescription = stringResource(R.string.web_nav_collapse),
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
                        LiveWebSite.LinkedIn.clearSession()
                        webViewRef.value?.clearCache(true)
                        webViewRef.value?.clearHistory()
                        webViewRef.value?.loadUrl(LINKEDIN_HOME_URL)
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
