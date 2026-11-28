package org.crux.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.crux.ui.presenter.PresenterBase
import org.crux.ui.route.DeeplinkRoute

public class DeepLinkPresenter(
    private val onRoute: (DeeplinkRoute) -> Unit,
    private val onLink: (String) -> Unit,
) : PresenterBase<DeepLinkPresenter.State>() {
    @Immutable
    public interface State {
        public fun handle(url: String)
    }

    @Composable
    override fun body(): State {
        var pendingUrl by remember { mutableStateOf<String?>(null) }
        pendingUrl?.let { url ->
            LaunchedEffect(url) {
                if (DeeplinkRoute.isDeeplink(url)) {
                    DeeplinkRoute.parse(url)?.let { route ->
                        when (route) {
                            is DeeplinkRoute.OpenLinkDirectly -> {
                                withContext(Dispatchers.Main) {
                                    onLink(route.url)
                                }
                            }

                            else -> {
                                withContext(Dispatchers.Main) {
                                    onRoute(route)
                                }
                            }
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onLink(url)
                    }
                }
                pendingUrl = null
            }
        }

        return object : State {
            override fun handle(url: String) {
                pendingUrl = url
            }
        }
    }
}
