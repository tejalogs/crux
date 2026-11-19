package org.crux.ui.screen.settings

import android.net.Uri
import android.view.ViewGroup.LayoutParams
import android.webkit.CookieManager
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kevinnzou.web.WebView
import com.kevinnzou.web.WebViewState
import com.kevinnzou.web.rememberWebViewState
import org.crux.R
import org.crux.ui.component.BackButton
import org.crux.ui.component.CruxLargeFlexibleTopAppBar
import org.crux.ui.component.CruxScaffold
import org.crux.ui.screen.home.WebSessionLifecycleSuspender
import org.crux.ui.screen.home.currentUiNightMask
import org.crux.ui.screen.home.restoreUiModeNightMaskIfNeeded

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HardenedBrowserScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val inputUrlState = remember { mutableStateOf("") }
    val currentUrlState = remember { mutableStateOf("") }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    WebSessionLifecycleSuspender(webView = webViewRef.value)
    BackHandler(enabled = canGoBack) {
        webViewRef.value?.goBack()
    }
    val webViewState = rememberWebViewState("")

    val load = { target: String ->
        val trimmed = target.trim()
        if (trimmed.isNotBlank()) {
            val normalized = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
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
                        currentUrlState.value.takeIf { it.isNotEmpty() }?.let { uriHandler.openUri(it) }
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
                    view.setBackgroundColor(webBackgroundColor)
                    webViewRef.value = view
                    with(view.settings) {
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        allowFileAccess = false
                        allowContentAccess = false
                        javaScriptCanOpenWindowsAutomatically = false
                        @Suppress("DEPRECATION")
                        saveFormData = false
                        setSafeBrowsingEnabled(true)
                    }
                    CookieManager.getInstance().setAcceptThirdPartyCookies(view, false)
                    view.setWebViewClient(
                        object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean {
                                if (!request.isForMainFrame) return false
                                val target = request.url.toString()
                                return when {
                                    target.startsWith("http://") || target.startsWith("https://") -> {
                                        false
                                    }

                                    else -> {
                                        if (request.hasGesture()) runCatching { uriHandler.openUri(target) }
                                        true
                                    }
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
