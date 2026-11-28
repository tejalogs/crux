package org.crux.ui.presenter.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import org.crux.di.koinGet
import org.crux.rss.RssRepository
import org.crux.ui.presenter.PresenterBase

@Immutable
public interface StorageState {
    public val userCount: Long
    public val statusCount: Long

    public fun clearCache()
}

public class StoragePresenter : PresenterBase<StorageState>() {
    @Composable
    override fun body(): StorageState {
        val context = LocalContext.current
        val repo = remember(context) { RssRepository.get(context) }
        val feeds by repo.feeds.collectAsState()
        val articles by repo.articles.collectAsState()
        val scope = rememberCoroutineScope()
        LaunchedEffect(repo) {
            repo.ensureLoaded()
        }
        return object : StorageState {
            override val userCount: Long = feeds.size.toLong()
            override val statusCount: Long = articles.size.toLong()

            override fun clearCache() {
                scope.launch {
                    repo.clearArticleCache()
                }
            }
        }
    }

    public suspend fun clearCacheSuspend() {
        val context: Context = koinGet()
        RssRepository.get(context).clearArticleCache()
    }
}
