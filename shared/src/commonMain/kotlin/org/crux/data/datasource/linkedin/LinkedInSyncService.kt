package org.crux.data.datasource.linkedin

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
import org.crux.common.PlatformDispatchers
import org.crux.data.io.FileStorage
import org.crux.data.platform.LINKEDIN_HOST
import org.crux.data.platform.LINKEDIN_PLATFORM_ID
import org.crux.data.platform.LinkedInCredential
import org.crux.data.repository.AccountService
import org.crux.data.repository.addAccount
import org.crux.data.repository.updateCredential
import org.crux.di.koinGet
import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiAccount
import org.crux.ui.model.UiIcon
import org.koin.core.annotation.Single
import kotlin.concurrent.Volatile
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
@Single
public class LinkedInSyncService internal constructor(
    private val accountService: AccountService? = null,
    fileStorage: FileStorage? = null,
) {
    public constructor(accountService: AccountService?) : this(accountService, null)
    public constructor() : this(null, null)

    private val syncScope = CoroutineScope(SupervisorJob() + PlatformDispatchers.IO)

    private val resolvedAccountService: AccountService? by lazy {
        accountService ?: runCatching { koinGet<AccountService>() }.getOrNull()
    }
    private val resolvedStorage: FileStorage? by lazy {
        fileStorage ?: runCatching { koinGet<FileStorage>() }.getOrNull()
    }
    private val filePath by lazy { resolvedStorage?.dataStoreFile("linkedin_feed_cache.json") }
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

    private val cachedUpdates = MutableStateFlow<List<LinkedInUpdate>>(emptyList())

    public suspend fun ensureLoaded(): List<LinkedInUpdate> {
        if (isLoaded) return cachedUpdates.value
        return loadMutex.withLock {
            if (!isLoaded) {
                writeMutex.withLock {
                    if (!isLoaded) {
                        val fromDisk = loadFromDisk()
                        val inMemory = cachedUpdates.value
                        val merged =
                            if (inMemory.isNotEmpty()) {
                                (inMemory + fromDisk)
                                    .distinctBy { it.id.ifBlank { it.urn ?: it.entityUrn ?: it.trackingUrn }.orEmpty() }
                                    .take(150)
                            } else {
                                fromDisk
                            }
                        cachedUpdates.value = merged
                        isLoaded = true
                        merged
                    } else {
                        cachedUpdates.value
                    }
                }
            } else {
                cachedUpdates.value
            }
        }
    }

    private suspend fun loadFromDisk(): List<LinkedInUpdate> =
        withContext(PlatformDispatchers.IO) {
            val storage = resolvedStorage ?: return@withContext emptyList()
            val path = filePath ?: return@withContext emptyList()
            try {
                if (storage.exists(path)) {
                    val bytes = storage.read(path)
                    if (bytes.isEmpty()) return@withContext emptyList()
                    json.decodeFromString<List<LinkedInUpdate>>(bytes.decodeToString())
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                println("[LinkedInSyncService] Corrupt cache file detected, resetting cache: ${e::class.simpleName}: ${e.message}")
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
                                (cachedUpdates.value + fromDisk)
                                    .distinctBy { it.id.ifBlank { it.urn ?: it.entityUrn ?: it.trackingUrn }.orEmpty() }
                                    .take(150)
                            cachedUpdates.value = fullyMerged
                        }
                    }
                    runCatching {
                        val bytes = json.encodeToString(cachedUpdates.value).encodeToByteArray()
                        storage.write(path, bytes)
                    }.onFailure { err ->
                        println("[LinkedInSyncService] Failed to persist feed cache: ${err::class.simpleName}: ${err.message}")
                    }
                }
            }
        }
    }

    public suspend fun clearCache(): Unit =
        withContext(PlatformDispatchers.IO) {
            writeMutex.withLock {
                ++cacheVersion
                cachedUpdates.value = emptyList()
                isLoaded = true
                val storage = resolvedStorage ?: return@withContext
                val path = filePath ?: return@withContext
                runCatching {
                    if (storage.exists(path)) {
                        storage.delete(path)
                    }
                }.onFailure { err ->
                    println("[LinkedInSyncService] Failed to clear feed cache: ${err::class.simpleName}: ${err.message}")
                }
            }
        }

    public fun cacheUpdates(updates: List<LinkedInUpdate>) {
        if (updates.isEmpty()) return
        val current = cachedUpdates.value
        val merged =
            (updates + current)
                .distinctBy { it.id.ifBlank { it.urn ?: it.entityUrn ?: it.trackingUrn }.orEmpty() }
                .take(150)
        cachedUpdates.value = merged
        val version = ++cacheVersion
        persistToDisk(version)
    }

    public fun getLatestCachedUpdates(): List<LinkedInUpdate> {
        if (!isLoaded) {
            syncScope.launch {
                ensureLoaded()
            }
        }
        return cachedUpdates.value
    }

    public suspend fun syncSession(
        cookies: String,
        explicitUserId: String? = null,
    ): Boolean {
        if (!LinkedInFeedService.hasActiveSession(cookies)) {
            return false
        }
        val userId = explicitUserId ?: LinkedInFeedService.extractUserId(cookies) ?: return false

        val accountKey =
            MicroBlogKey(
                id = userId,
                host = LINKEDIN_HOST,
            )

        val service = resolvedAccountService ?: return false
        val existingAccounts = service.allAccountServicesFlow().first()
        val existingAccount = existingAccounts.firstOrNull { it.accountKey == accountKey }

        if (existingAccount == null) {
            service.addAccount(
                account =
                    UiAccount(
                        accountKey = accountKey,
                        platformId = LINKEDIN_PLATFORM_ID,
                        platformDisplayName = "LinkedIn",
                        platformIcon = UiIcon.LinkedIn,
                    ),
                credential =
                    LinkedInCredential(
                        cookies = "",
                        userId = userId,
                    ),
            )
        } else {
            service.updateCredential(
                accountKey = accountKey,
                credential =
                    LinkedInCredential(
                        cookies = "",
                        userId = userId,
                    ),
            )
        }

        return true
    }

    public suspend fun isLinkedInAccountRegistered(): Boolean {
        val service = resolvedAccountService ?: return false
        val existingAccounts = service.allAccountServicesFlow().first()
        return existingAccounts.any { it.platformId == LINKEDIN_PLATFORM_ID }
    }
}
