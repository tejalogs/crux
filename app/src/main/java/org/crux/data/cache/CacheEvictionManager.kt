package org.crux.data.cache

import android.content.Context
import org.crux.rss.RssRepository
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

public data class CachePurgeResult(
    val purgedTimelineEntries: Int,
    val purgedStatuses: Int,
    val durationMs: Long,
)

@Single
internal class CacheEvictionManager(
    @Provided private val context: Context,
    private val offlineCacheRepository: OfflineCacheRepository,
) {
    suspend fun triggerBackgroundEvictionIfDue() {
        val config = offlineCacheRepository.settings.value
        if (!config.autoPruneDatabaseCache) return
        val now = System.currentTimeMillis()
        if (now - config.lastDatabasePruneTimestamp < MIN_PRUNE_INTERVAL_MS) return
        purgeExpiredCache()
    }

    suspend fun purgeExpiredCache(): CachePurgeResult {
        val start = System.currentTimeMillis()
        val config = offlineCacheRepository.settings.value
        val cutoff = start - config.timelineCacheRetentionDays.toLong() * MS_PER_DAY
        val deleted = RssRepository.get(context).pruneArticlesOlderThan(cutoff)
        val finish = System.currentTimeMillis()
        offlineCacheRepository.updateLastDatabasePruneTimestamp(finish)
        return CachePurgeResult(
            purgedTimelineEntries = deleted,
            purgedStatuses = deleted,
            durationMs = (finish - start).coerceAtLeast(1L),
        )
    }

    private companion object {
        private const val MS_PER_DAY = 24L * 60L * 60L * 1000L
        private const val MIN_PRUNE_INTERVAL_MS = 12L * 60L * 60L * 1000L
    }
}
