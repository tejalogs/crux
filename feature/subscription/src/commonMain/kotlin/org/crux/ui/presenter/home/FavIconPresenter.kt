package org.crux.ui.presenter.home

import androidx.compose.runtime.Composable
import org.crux.common.MemCacheable
import org.crux.common.collectAsState
import org.crux.data.network.FaviconService
import org.crux.ui.model.UiState
import org.crux.ui.model.toUi
import org.crux.ui.presenter.PresenterBase

public class FavIconPresenter(
    private val host: String,
) : PresenterBase<UiState<String>>() {
    private val cacheKey by lazy {
        "$host-favIcon"
    }

    private val flow by lazy {
        MemCacheable(cacheKey) {
            FaviconService.fetchIcon(host.toFavIconFetchUrl()) ?: throw IllegalStateException("Favicon not found")
        }
    }

    @Composable
    override fun body(): UiState<String> = flow.collectAsState().toUi()
}

internal fun String.toFavIconFetchUrl(): String =
    if (startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)) {
        this
    } else {
        "https://$this"
    }
