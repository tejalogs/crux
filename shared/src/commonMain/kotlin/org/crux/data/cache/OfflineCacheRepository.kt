package org.crux.data.cache

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.crux.data.io.FileStorage
import org.koin.core.annotation.Single
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
@Single
public class OfflineCacheRepository internal constructor(
    private val fileStorage: FileStorage,
) {
    private val mutex = Mutex()
    private val filePath by lazy { fileStorage.dataStoreFile("offline_cache_config.json") }
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = false
        }

    private val _settings = MutableStateFlow(loadSettings())
    public val settings: StateFlow<OfflineCacheConfig> = _settings.asStateFlow()

    private fun loadSettings(): OfflineCacheConfig =
        runCatching {
            if (fileStorage.exists(filePath)) {
                val bytes = fileStorage.read(filePath)
                json.decodeFromString<OfflineCacheConfig>(bytes.decodeToString())
            } else {
                OfflineCacheConfig.Default
            }
        }.getOrElse { OfflineCacheConfig.Default }

    public suspend fun updateSettings(config: OfflineCacheConfig) {
        mutex.withLock {
            runCatching {
                val serialized = json.encodeToString(config).encodeToByteArray()
                fileStorage.write(filePath, serialized)
                _settings.value = config
            }
        }
    }

    public suspend fun setMaxMediaCacheSizeMb(sizeMb: Int) {
        val current = _settings.value
        updateSettings(current.copy(maxMediaCacheSizeMb = sizeMb))
    }

    public suspend fun setPrefetchMediaForReadingList(enabled: Boolean) {
        val current = _settings.value
        updateSettings(current.copy(prefetchMediaForReadingList = enabled))
    }

    public suspend fun setWifiOnlyPrefetch(wifiOnly: Boolean) {
        val current = _settings.value
        updateSettings(current.copy(wifiOnlyPrefetch = wifiOnly))
    }

    public suspend fun setTimelineCacheRetentionDays(days: Int) {
        val current = _settings.value
        updateSettings(current.copy(timelineCacheRetentionDays = days.coerceIn(14, 30)))
    }

    public suspend fun setAutoPruneDatabaseCache(enabled: Boolean) {
        val current = _settings.value
        updateSettings(current.copy(autoPruneDatabaseCache = enabled))
    }

    public suspend fun updateLastDatabasePruneTimestamp(timestamp: Long) {
        val current = _settings.value
        updateSettings(current.copy(lastDatabasePruneTimestamp = timestamp))
    }
}
