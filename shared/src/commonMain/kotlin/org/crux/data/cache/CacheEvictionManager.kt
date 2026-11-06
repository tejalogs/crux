package org.crux.data.cache

import kotlinx.coroutines.withContext
import org.crux.common.PlatformDispatchers
import org.crux.data.database.cache.CacheDatabase
import org.crux.data.database.cache.connect
import org.koin.core.annotation.Single
import kotlin.native.HiddenFromObjC
import kotlin.time.Clock

@HiddenFromObjC
public data class CacheEvictionResult(
    val purgedTimelineEntries: Int,
    val purgedStatuses: Int,
    val purgedPresentationReferences: Int,
    val purgedStatusReferences: Int,
    val durationMs: Long,
)

@HiddenFromObjC
public class CacheEvictionManager internal constructor(
    private val cacheDatabase: CacheDatabase,
    private val offlineCacheRepository: OfflineCacheRepository,
) {
    /**
     * Purges cached timeline entries and orphaned statuses older than the configured retention period
     * (14 to 30 days) while strictly protecting bookmarked, favorited, and reading list posts.
     */
    public suspend fun purgeExpiredCache(retentionDaysOverride: Int? = null): CacheEvictionResult =
        withContext(PlatformDispatchers.IO) {
            val startTime = Clock.System.now().toEpochMilliseconds()
            val retentionDays = (retentionDaysOverride ?: offlineCacheRepository.settings.value.timelineCacheRetentionDays).coerceIn(14, 30)
            val cutoffMillis = startTime - (retentionDays * 86_400_000L)
            // SnowflakeIdGenerator: 41 timestamp bits shifted left by 22, epoch 1700000000000L
            val cutoffSortId = (cutoffMillis - 1700000000000L) shl 22

            println("[CRUX-CACHE-EVICTION] Starting cache eviction: retentionDays=$retentionDays, cutoffSortId=$cutoffSortId")

            var purgedTimeline = 0
            var purgedPresentation = 0
            var purgedReferences = 0
            var purgedStatuses = 0

            cacheDatabase.connect {
                // 1. Evict expired unbookmarked timeline entries
                purgedTimeline = cacheDatabase.pagingTimelineDao().pruneExpiredTimelineEntries(cutoffSortId)

                // 2. Evict presentation references for deleted timelines
                purgedPresentation = cacheDatabase.pagingTimelineDao().pruneOrphanedPresentationReferences()

                // 3. Evict status references for unreferenced statuses
                purgedReferences = cacheDatabase.statusReferenceDao().pruneOrphanedStatusReferences()

                // 4. Evict orphaned statuses not in any timeline or reference
                purgedStatuses = cacheDatabase.statusDao().pruneOrphanedStatuses()

                // 5. Clean up dangling translations and empty paging keys
                cacheDatabase.translationDao().pruneOrphanedTranslations()
                cacheDatabase.pagingTimelineDao().pruneOrphanedPagingKeys()
            }

            offlineCacheRepository.updateLastDatabasePruneTimestamp(startTime)

            val durationMs = Clock.System.now().toEpochMilliseconds() - startTime
            println(
                "[CRUX-CACHE-EVICTION] Completed in ${durationMs}ms: " +
                    "purged $purgedTimeline timeline rows, $purgedStatuses statuses, " +
                    "$purgedPresentation presentation refs, $purgedReferences status refs",
            )

            CacheEvictionResult(
                purgedTimelineEntries = purgedTimeline,
                purgedStatuses = purgedStatuses,
                purgedPresentationReferences = purgedPresentation,
                purgedStatusReferences = purgedReferences,
                durationMs = durationMs,
            )
        }

    /**
     * Periodically triggers background eviction if enabled and more than 24 hours have elapsed
     * since the last run. Safe to call during app startup.
     */
    public suspend fun triggerBackgroundEvictionIfDue() {
        runCatching {
            val config = offlineCacheRepository.settings.value
            if (!config.autoPruneDatabaseCache) return

            val now = Clock.System.now().toEpochMilliseconds()
            val elapsed = now - config.lastDatabasePruneTimestamp
            val oneDayMillis = 24 * 60 * 60 * 1000L

            if (elapsed > oneDayMillis) {
                println("[CRUX-CACHE-EVICTION] 24+ hours elapsed since last eviction (${elapsed / 3600000}h), running background purge")
                purgeExpiredCache()
            }
        }.onFailure { err ->
            println("[CRUX-CACHE-EVICTION] Background eviction skipped due to error: ${err.message}")
        }
    }
}
