package org.crux.ui.screen.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.ViewGroup.LayoutParams
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebChromeClient.FileChooserParams
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.kevinnzou.web.WebView
import com.kevinnzou.web.rememberWebViewState
import org.crux.R
import org.crux.ui.component.BackButton
import org.crux.ui.component.CruxLargeFlexibleTopAppBar
import org.crux.ui.component.CruxScaffold
import org.crux.ui.screen.home.WebSessionLifecycleSuspender
import org.crux.ui.screen.home.configureWebViewHeadersAndDarkening
import org.crux.ui.screen.home.currentUiNightMask
import org.crux.ui.screen.home.openInSystemBrowser
import org.crux.ui.screen.home.restoreUiModeNightMaskIfNeeded
import java.net.URI

private const val HARDENED_BROWSER_PROFILE = "HardenedBrowser"
private val HARDENED_SAFE_EXTERNAL_SCHEMES = setOf("mailto", "tel", "sms")

internal fun normalizeHardenedBrowserInput(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() || it.code < 0x20 }) return null
    val candidate =
        when {
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("http://", ignoreCase = true) -> "https://" + trimmed.substringAfter("://")
            trimmed.contains("://") -> return null
            else -> "https://$trimmed"
        }
    val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank()) return null
    return candidate
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HardenedBrowserScreen(onBack: () -> Unit) {
    // Without separate WebView profiles this browser would share the signed-in sites' cookie jar,
    // so it is not offered at all rather than pretending to be isolated.
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
        HardenedBrowserUnavailable(onBack = onBack)
        return
    }
    val context = LocalContext.current
    val inputUrlState = remember { mutableStateOf("") }
    val currentUrlState = remember { mutableStateOf("") }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    WebSessionLifecycleSuspender(webView = webViewRef.value)
    BackHandler(enabled = canGoBack) {
        webViewRef.value?.goBack()
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                webViewRef.value?.apply {
                    clearCache(true)
                    clearHistory()
                    clearFormData()
                }
                if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                    val profile = ProfileStore.getInstance().getOrCreateProfile(HARDENED_BROWSER_PROFILE)
                    profile.cookieManager.removeAllCookies(null)
                    profile.cookieManager.flush()
                    profile.webStorage.deleteAllData()
                }
            }
        }
    }
    val webViewState = rememberWebViewState("")

    val load = { target: String ->
        val normalized = normalizeHardenedBrowserInput(target)
        if (normalized != null) {
            inputUrlState.value = normalized
            webViewRef.value?.loadUrl(normalized)
        }
    }

    CruxScaffold(
        topBar = {
            CruxLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_hardened_browser_title))
                },
                navigationIcon = {
                    BackButton(onBack)
                },
                scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(),
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(it)
                    .imePadding()
                    .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = inputUrlState.value,
                    onValueChange = { inputUrlState.value = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { load(inputUrlState.value) }),
                    label = {
                        Text(text = stringResource(id = R.string.settings_hardened_browser_url))
                    },
                )
                Button(
                    onClick = {
                        load(inputUrlState.value)
                    },
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(text = stringResource(id = R.string.browser_go))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        webViewRef.value?.goBack()
                    },
                ) {
                    Text(text = stringResource(id = R.string.browser_back))
                }
                TextButton(
                    onClick = {
                        webViewRef.value?.reload()
                    },
                ) {
                    Text(text = stringResource(id = R.string.browser_reload))
                }
                TextButton(
                    onClick = {
                        currentUrlState.value.takeIf { it.isNotEmpty() }?.let { openInSystemBrowser(context, it) }
                    },
                ) {
                    Text(text = stringResource(id = R.string.browser_open_external))
                }
            }
            val expectedNightMask = remember(context) { context.currentUiNightMask() }
            val webBackgroundColor = MaterialTheme.colorScheme.background.toArgb()
            LaunchedEffect(webViewRef.value, webBackgroundColor) {
                webViewRef.value?.setBackgroundColor(webBackgroundColor)
            }
            WebView(
                webViewState,
                FrameLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT,
                ),
                modifier = Modifier.fillMaxSize(),
                onCreated = { view: WebView ->
                    restoreUiModeNightMaskIfNeeded(view.context, expectedNightMask)
                    view.post { restoreUiModeNightMaskIfNeeded(view.context, expectedNightMask) }
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                        ProfileStore.getInstance().getOrCreateProfile(HARDENED_BROWSER_PROFILE)
                        WebViewCompat.setProfile(view, HARDENED_BROWSER_PROFILE)
                    }
                    view.setBackgroundColor(webBackgroundColor)
                    webViewRef.value = view
                    with(view.settings) {
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        allowFileAccess = false
                        allowContentAccess = false
                        setGeolocationEnabled(false)
                        @Suppress("DEPRECATION")
                        setAllowFileAccessFromFileURLs(false)
                        @Suppress("DEPRECATION")
                        setAllowUniversalAccessFromFileURLs(false)
                        javaScriptCanOpenWindowsAutomatically = false
                        @Suppress("DEPRECATION")
                        saveFormData = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            safeBrowsingEnabled = true
                        }
                    }
                    configureWebViewHeadersAndDarkening(view.settings)
                    val cookieManager =
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                            ProfileStore.getInstance().getOrCreateProfile(HARDENED_BROWSER_PROFILE).cookieManager
                        } else {
                            CookieManager.getInstance()
                        }
                    cookieManager.setAcceptThirdPartyCookies(view, false)
                    view.setWebViewClient(
                        object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean {
                                if (!request.isForMainFrame) return false
                                val target = request.url.toString()
                                val scheme = request.url.scheme?.lowercase()
                                return when {
                                    scheme == "https" -> false

                                    scheme == "http" -> {
                                        val upgraded = "https://" + target.substringAfter("://")
                                        view.post { view.loadUrl(upgraded) }
                                        true
                                    }

                                    scheme in HARDENED_SAFE_EXTERNAL_SCHEMES && request.hasGesture() -> {
                                        runCatching {
                                            view.context.startActivity(
                                                Intent(Intent.ACTION_VIEW, target.toUri())
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                            )
                                        }
                                        true
                                    }

                                    else -> true
                                }
                            }

                            override fun onPageFinished(
                                view: WebView,
                                url: String?,
                            ) {
                                super.onPageFinished(view, url)
                                currentUrlState.value = url ?: ""
                                canGoBack = view.canGoBack()
                            }
                        },
                    )
                    view.setWebChromeClient(
                        object : WebChromeClient() {
                            override fun onGeolocationPermissionsShowPrompt(
                                origin: String?,
                                callback: GeolocationPermissions.Callback?,
                            ) {
                                callback?.invoke(origin, false, false)
                            }

                            override fun onPermissionRequest(request: PermissionRequest) {
                                request.deny()
                            }

                            override fun onShowFileChooser(
                                view: WebView,
                                filePathCallback: ValueCallback<Array<Uri>>,
                                fileChooserParams: FileChooserParams,
                            ): Boolean = false
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun HardenedBrowserUnavailable(onBack: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    androidx.compose.foundation.layout.Box(
        modifier =
            androidx.compose.ui.Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(32.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.hardened_browser_unavailable),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            androidx.compose.material3.Button(onClick = { openInSystemBrowser(context, "https://duckduckgo.com") }) {
                Text(stringResource(R.string.hardened_browser_open_system))
            }
            androidx.compose.material3.TextButton(onClick = onBack) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    }
}

