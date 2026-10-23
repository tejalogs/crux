package dev.dimension.flare.ui.screen.settings

import android.net.Uri
import android.view.ViewGroup.LayoutParams
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.ValueCallback
import android.webkit.WebChromeClient.FileChooserParams
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kevinnzou.web.WebView
import com.kevinnzou.web.rememberWebViewState
import com.kevinnzou.web.WebViewState
import dev.dimension.flare.R
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HardenedBrowserScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val inputUrlState = remember { mutableStateOf("") }
    val currentUrlState = remember { mutableStateOf("") }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    val webViewState = rememberWebViewState("")

    val load = { target: String ->
        if (target.isNotBlank()) {
            val normalized = if (target.startsWith("http://") || target.startsWith("https://")) target else "https://$target"
            inputUrlState.value = normalized
            webViewRef.value?.loadUrl(normalized)
        }
    }

    FlareScaffold(
        topBar = {
            FlareLargeFlexibleTopAppBar(
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
                    .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = inputUrlState.value,
                    onValueChange = { inputUrlState.value = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = {
                        Text(text = stringResource(id = R.string.settings_hardened_browser_url))
                    },
                )
                Button(
                    onClick = {
                        load(inputUrlState.value)
                    },
                ) {
                    Text(text = "Go")
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
                    Text(text = "Back")
                }
                TextButton(
                    onClick = {
                        webViewRef.value?.reload()
                    },
                ) {
                    Text(text = "Reload")
                }
                TextButton(
                    onClick = {
                        currentUrlState.value.takeIf { it.isNotEmpty() }?.let { uriHandler.openUri(it) }
                    },
                ) {
                    Text(text = "Open external")
                }
            }
            WebView(
                webViewState,
                FrameLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT,
                ),
                modifier = Modifier.fillMaxSize(),
                onCreated = { view: WebView ->
                    webViewRef.value = view
                    with(view.settings) {
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        allowFileAccess = false
                        allowContentAccess = false
                        javaScriptCanOpenWindowsAutomatically = false
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
                                val target = request.url.toString()
                                return when {
                                    target.startsWith("http://") || target.startsWith("https://") -> false
                                    else -> {
                                        uriHandler.openUri(target)
                                        true
                                    }
                                }
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                super.onPageFinished(view, url)
                                currentUrlState.value = url ?: ""
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
                            ): Boolean {
                                return false
                            }
                        },
                    )
                },
            )
        }
    }
}
