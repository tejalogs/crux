package dev.dimension.flare.ui.screen.instagram

import android.net.Uri
import android.view.ViewGroup.LayoutParams
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kevinnzou.web.WebView
import com.kevinnzou.web.rememberWebViewState
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.brands.Instagram
import compose.icons.fontawesomeicons.solid.ArrowLeft
import compose.icons.fontawesomeicons.solid.ArrowRight
import compose.icons.fontawesomeicons.solid.ArrowUpRightFromSquare
import compose.icons.fontawesomeicons.solid.RotateRight
import compose.icons.fontawesomeicons.solid.ShieldHalved
import compose.icons.fontawesomeicons.solid.TrashCan
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import kotlinx.coroutines.delay
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
            div[data-testid="mobile-app-banner"] { display: none !important; }
            a[href*="instagram://"] { display: none !important; }
            a[href*="play.google.com/store/apps/details?id=com.instagram.android"] { display: none !important; }
            a[href*="itunes.apple.com"] { display: none !important; }
            a[href*="instagram.com/download"] { display: none !important; }
            div[class*="AppBanner"] { display: none !important; }
            html, body { -webkit-tap-highlight-color: transparent; }
        `;
        (document.head || document.documentElement).appendChild(style);
    }
    var dialogs = document.querySelectorAll('div[role="dialog"]');
    for (var i = 0; i < dialogs.length; i++) {
        var text = dialogs[i].innerText || '';
        if (text.indexOf('Open in Instagram') !== -1 || text.indexOf('Use the App') !== -1 || text.indexOf('Get the app') !== -1) {
            dialogs[i].remove();
        }
    }
    var articles = document.querySelectorAll('article');
    for (var j = 0; j < articles.length; j++) {
        var spans = articles[j].querySelectorAll('span');
        for (var k = 0; k < spans.length; k++) {
            if (spans[k].textContent === 'Sponsored') {
                articles[j].style.display = 'none';
                break;
            }
        }
    }
})();
"""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InstagramWebScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    val webViewState = rememberWebViewState(INSTAGRAM_HOME_URL)

    var currentUrl by remember { mutableStateOf(INSTAGRAM_HOME_URL) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var pageProgress by remember { mutableFloatStateOf(0f) }
    var isPageLoading by remember { mutableStateOf(true) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

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

    // Periodic shield refresher for dynamically loaded DOM elements
    LaunchedEffect(Unit) {
        while (true) {
            delay(2.seconds)
            webViewRef.value?.evaluateJavascript(PRISM_SHIELD_JS, null)
            canGoBack = webViewRef.value?.canGoBack() == true
            canGoForward = webViewRef.value?.canGoForward() == true
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
                        text = "• Storage: Isolated prismbrowser sandbox\n• Third-Party Tracking: Blocked\n• Ad & App Nag Banners: Stripped\n• Zero Checkpoint Ban Risk",
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
                            Column {
                                Text(
                                    text = "Instagram",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "Privacy Sandbox Active",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        BackButton(onBack = onBack)
                    },
                    actions = {
                        IconButton(onClick = { showPrivacyDialog = true }) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.ShieldHalved,
                                contentDescription = "Privacy Shield",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        IconButton(onClick = { webViewRef.value?.reload() }) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.RotateRight,
                                contentDescription = "Reload",
                                modifier = Modifier.size(18.dp),
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

            // Floating Navigation Dock
            Surface(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
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
                            if (currentUrl.isNotBlank()) {
                                uriHandler.openUri(currentUrl)
                            }
                        },
                    ) {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.ArrowUpRightFromSquare,
                            contentDescription = "Open External",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}
