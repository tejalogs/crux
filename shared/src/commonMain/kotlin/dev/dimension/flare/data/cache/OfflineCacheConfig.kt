package dev.dimension.flare.data.cache

import kotlinx.serialization.Serializable

@Serializable
public data class OfflineCacheConfig(
    val maxMediaCacheSizeMb: Int = 512,
    val prefetchMediaForReadingList: Boolean = true,
    val wifiOnlyPrefetch: Boolean = false,
    val timelineCacheRetentionDays: Int = 14,
    val autoPruneExpiredMedia: Boolean = true,
    val autoPruneDatabaseCache: Boolean = true,
    val lastDatabasePruneTimestamp: Long = 0L,
) {
    public companion object {
        public val Default: OfflineCacheConfig = OfflineCacheConfig()
        public val ALLOWED_SIZES_MB: List<Int> = listOf(256, 512, 1024, 2048, 4096)
        public val ALLOWED_RETENTION_DAYS: List<Int> = listOf(14, 21, 30)
    }
}
