package dev.dimension.flare.ui.screen.serviceselect

import android.view.ViewGroup.LayoutParams
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.kevinnzou.web.WebView
import com.kevinnzou.web.rememberWebViewState
import dev.dimension.flare.ui.component.FlareScaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.seconds

private const val USER_AGENT =
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.36"

@Composable
internal fun WebCookieLoginScreen(
    url: String,
    callback: (String?) -> Boolean,
    onBack: () -> Unit,
) {
    val webViewState = rememberWebViewState(url)
    LaunchedEffect(url) {
        var attempts = 0
        val maxAttempts = 90 // 3 minutes timeout (90 * 2s)
        while (isActive && attempts < maxAttempts) {
            webViewState.lastLoadedUrl?.let { loadedUrl ->
                val cookies =
                    CookieManager
                        .getInstance()
                        .getCookie(loadedUrl)
                if (!cookies.isNullOrBlank() && callback(cookies)) {
                    onBack()
                    return@LaunchedEffect
                }
            }
            attempts++
            delay(2.seconds)
        }
    }
    FlareScaffold {
        WebView(
            webViewState,
            layoutParams =
                FrameLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT,
                ),
            modifier =
                Modifier
                    .alpha(0.99f)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(it)
                    .fillMaxSize(),
            onCreated = {
                WebStorage.getInstance().deleteAllData()
                CookieManager.getInstance().removeAllCookies(null)
                with(it.settings) {
                    userAgentString = USER_AGENT
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    javaScriptCanOpenWindowsAutomatically = false
                    cacheMode = WebSettings.LOAD_DEFAULT
                }
            },
        )
    }
}
