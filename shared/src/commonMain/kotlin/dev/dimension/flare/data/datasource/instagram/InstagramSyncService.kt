package dev.dimension.flare.data.datasource.instagram

import dev.dimension.flare.common.PlatformDispatchers
import dev.dimension.flare.data.io.FileStorage
import dev.dimension.flare.data.platform.INSTAGRAM_HOST
import dev.dimension.flare.data.platform.INSTAGRAM_PLATFORM_ID
import dev.dimension.flare.data.platform.InstagramCredential
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.data.repository.addAccount
import dev.dimension.flare.data.repository.updateCredential
import dev.dimension.flare.di.koinGet
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import kotlin.concurrent.Volatile
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
@Single
public class InstagramSyncService internal constructor(
    private val accountService: AccountService? = null,
    fileStorage: FileStorage? = null,
) {
    public constructor(accountService: AccountService?) : this(accountService, null)
    public constructor() : this(null, null)

    private val syncScope = CoroutineScope(SupervisorJob() + PlatformDispatchers.IO)
    private val resolvedStorage: FileStorage? = fileStorage ?: runCatching { koinGet<FileStorage>() }.getOrNull()
    private val filePath by lazy { resolvedStorage?.dataStoreFile("instagram_feed_cache.json") }
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = false
        }

    private val loadMutex = Mutex()
    private val writeMutex = Mutex()

    @Volatile private var isLoaded = false

    @Volatile private var cacheVersion = 0L

    private val cachedItems = MutableStateFlow<List<InstagramMediaOrAd>>(emptyList())

    public suspend fun ensureLoaded(): List<InstagramMediaOrAd> {
        if (isLoaded) return cachedItems.value
        return loadMutex.withLock {
            if (!isLoaded) {
                writeMutex.withLock {
                    if (!isLoaded) {
                        val fromDisk = loadFromDisk()
                        val inMemory = cachedItems.value
                        val merged =
                            if (inMemory.isNotEmpty()) {
                                (inMemory + fromDisk)
                                    .distinctBy { it.id.ifBlank { it.pk.orEmpty() } }
                                    .take(150)
                            } else {
                                fromDisk
                            }
                        cachedItems.value = merged
                        isLoaded = true
                        merged
                    } else {
                        cachedItems.value
                    }
                }
            } else {
                cachedItems.value
            }
        }
    }

    private suspend fun loadFromDisk(): List<InstagramMediaOrAd> =
        withContext(PlatformDispatchers.IO) {
            val storage = resolvedStorage ?: return@withContext emptyList()
            val path = filePath ?: return@withContext emptyList()
            try {
                if (storage.exists(path)) {
                    val bytes = storage.read(path)
                    if (bytes.isEmpty()) return@withContext emptyList()
                    json.decodeFromString<List<InstagramMediaOrAd>>(bytes.decodeToString())
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                println("[InstagramSyncService] Corrupt cache file detected, resetting cache: ${e::class.simpleName}: ${e.message}")
                runCatching {
                    if (storage.exists(path)) {
                        storage.delete(path)
                    }
                }
                emptyList()
            }
        }

    private fun persistToDisk(versionAtCall: Long) {
        syncScope.launch {
            writeMutex.withLock {
                if (versionAtCall != cacheVersion) return@withLock
                withContext(PlatformDispatchers.IO) {
                    val storage = resolvedStorage ?: return@withContext
                    val path = filePath ?: return@withContext
                    if (!isLoaded) {
                        val fromDisk = loadFromDisk()
                        isLoaded = true
                        if (fromDisk.isNotEmpty()) {
                            val fullyMerged =
                                (cachedItems.value + fromDisk)
                                    .distinctBy { it.id.ifBlank { it.pk.orEmpty() } }
                                    .take(150)
                            cachedItems.value = fullyMerged
                        }
                    }
                    runCatching {
                        val bytes = json.encodeToString(cachedItems.value).encodeToByteArray()
                        storage.write(path, bytes)
                    }.onFailure { err ->
                        println("[InstagramSyncService] Failed to persist feed cache: ${err::class.simpleName}: ${err.message}")
                    }
                }
            }
        }
    }

    public suspend fun clearCache(): Unit =
        withContext(PlatformDispatchers.IO) {
            writeMutex.withLock {
                ++cacheVersion
                cachedItems.value = emptyList()
                isLoaded = true
                val storage = resolvedStorage ?: return@withContext
                val path = filePath ?: return@withContext
                runCatching {
                    if (storage.exists(path)) {
                        storage.delete(path)
                    }
                }.onFailure { err ->
                    println("[InstagramSyncService] Failed to clear feed cache: ${err::class.simpleName}: ${err.message}")
                }
            }
        }

    public fun cacheItems(items: List<InstagramMediaOrAd>) {
        if (items.isEmpty()) return
        val current = cachedItems.value
        val merged =
            (items + current)
                .distinctBy { it.id.ifBlank { it.pk.orEmpty() } }
                .take(150)
        cachedItems.value = merged
        val version = ++cacheVersion
        persistToDisk(version)
    }

    public fun getLatestCachedItems(): List<InstagramMediaOrAd> {
        if (!isLoaded) {
            syncScope.launch {
                ensureLoaded()
            }
        }
        return cachedItems.value
    }

    public suspend fun syncSession(
        cookies: String,
        explicitUserId: String? = null,
    ): Boolean {
        if (!InstagramFeedService.hasActiveSession(cookies)) {
            return false
        }
        val userId = explicitUserId ?: InstagramFeedService.extractUserId(cookies) ?: return false

        val accountKey =
            MicroBlogKey(
                id = userId,
                host = INSTAGRAM_HOST,
            )

        val service = accountService ?: return false
        val existingAccounts = service.allAccountServicesFlow().first()
        val existingAccount = existingAccounts.firstOrNull { it.accountKey == accountKey }

        if (existingAccount == null) {
            service.addAccount(
                account =
                    UiAccount(
                        accountKey = accountKey,
                        platformId = INSTAGRAM_PLATFORM_ID,
                        platformDisplayName = "Instagram",
                        platformIcon = UiIcon.Instagram,
                    ),
                credential =
                    InstagramCredential(
                        cookies = "",
                        userId = userId,
                    ),
            )
        } else {
            service.updateCredential(
                accountKey = accountKey,
                credential =
                    InstagramCredential(
                        cookies = "",
                        userId = userId,
                    ),
            )
        }

        return true
    }

    public suspend fun isInstagramAccountRegistered(): Boolean {
        val service = accountService ?: return false
        val existingAccounts = service.allAccountServicesFlow().first()
        return existingAccounts.any { it.platformId == INSTAGRAM_PLATFORM_ID }
    }
}
