package org.crux.ui.presenter.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.crux.data.database.cache.CacheDatabase
import org.crux.di.koinInject
import org.crux.ui.presenter.PresenterBase
import org.crux.web.shared.WebPresenter

@WebPresenter("storage")
public class StoragePresenter : PresenterBase<StorageState>() {
    private val cacheDatabase by koinInject<CacheDatabase>()

    public suspend fun clearCacheSuspend() {
        cacheDatabase.clearAllTables()
    }

    @Composable
    override fun body(): StorageState {
        val scope = rememberCoroutineScope()
        val statusCount by remember {
            cacheDatabase
                .statusDao()
                .count()
        }.collectAsState(0L)
        val userCount by remember {
            cacheDatabase
                .userDao()
                .count()
        }.collectAsState(0L)
        return object : StorageState {
            override val userCount: Long = userCount
            override val statusCount: Long = statusCount

            override fun clearCache() {
                scope.launch {
                    clearCacheSuspend()
                }
            }
        }
    }
}

@Immutable
public interface StorageState {
    public val userCount: Long
    public val statusCount: Long

    public fun clearCache()
}
