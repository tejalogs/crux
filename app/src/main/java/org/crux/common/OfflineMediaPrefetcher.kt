package org.crux.common

import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.crux.data.readinglist.ReadingListItem

public data class PrefetchProgress(
    val isPrefetching: Boolean = false,
    val completedCount: Int = 0,
    val totalCount: Int = 0,
) {
    val progress: Float
        get() = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f
}

public class OfflineMediaPrefetcher(
    private val context: Context,
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val _status = MutableStateFlow(PrefetchProgress())
    public val status: StateFlow<PrefetchProgress> = _status.asStateFlow()

    public fun prefetchItem(item: ReadingListItem) {
        val urls =
            buildList {
                item.authorAvatarUrl?.takeIf { it.isNotBlank() }?.let { add(it) }
                addAll(item.mediaUrls.filter { it.isNotBlank() })
            }.filter { it.startsWith("http://") || it.startsWith("https://") }

        if (urls.isEmpty()) return
        prefetchUrls(urls)
    }

    public fun prefetchAll(items: List<ReadingListItem>) {
        val allUrls =
            items
                .flatMap { item ->
                    buildList {
                        item.authorAvatarUrl?.takeIf { it.isNotBlank() }?.let { add(it) }
                        addAll(item.mediaUrls.filter { it.isNotBlank() })
                    }
                }.distinct()
                .filter { it.startsWith("http://") || it.startsWith("https://") }

        if (allUrls.isEmpty()) return
        prefetchUrls(allUrls)
    }

    public fun prefetchUrls(urls: List<String>) {
        if (urls.isEmpty()) return
        scope.launch {
            val total = urls.size
            _status.value = PrefetchProgress(isPrefetching = true, completedCount = 0, totalCount = total)
            val imageLoader = SingletonImageLoader.get(context)

            var completed = 0
            for (url in urls) {
                runCatching {
                    val request =
                        ImageRequest
                            .Builder(context)
                            .data(url)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build()
                    imageLoader.execute(request)
                }
                completed++
                _status.value =
                    PrefetchProgress(
                        isPrefetching = completed < total,
                        completedCount = completed,
                        totalCount = total,
                    )
            }
            _status.value = PrefetchProgress(isPrefetching = false, completedCount = total, totalCount = total)
        }
    }

    public fun isUrlCached(url: String): Boolean {
        val imageLoader = SingletonImageLoader.get(context)
        val diskCache = imageLoader.diskCache ?: return false
        return diskCache.openSnapshot(url)?.use { true } ?: false
    }
}
