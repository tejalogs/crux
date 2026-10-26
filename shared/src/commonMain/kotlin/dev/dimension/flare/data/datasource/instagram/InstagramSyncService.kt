package dev.dimension.flare.data.datasource.instagram

import dev.dimension.flare.data.io.FileStorage
import dev.dimension.flare.data.platform.INSTAGRAM_HOST
import dev.dimension.flare.data.platform.INSTAGRAM_PLATFORM_ID
import dev.dimension.flare.data.platform.InstagramCredential
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.data.repository.addAccount
import dev.dimension.flare.data.repository.updateCredential
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiIcon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
@Single
public class InstagramSyncService internal constructor(
    private val accountService: AccountService? = null,
    private val fileStorage: FileStorage? = null,
) {
    public constructor(accountService: AccountService?) : this(accountService, null)
    public constructor() : this(null, null)

    private val filePath by lazy { fileStorage?.dataStoreFile("instagram_feed_cache.json") }
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val cachedItems = MutableStateFlow<List<InstagramMediaOrAd>>(loadFromDisk())

    private fun loadFromDisk(): List<InstagramMediaOrAd> {
        val storage = fileStorage ?: return emptyList()
        val path = filePath ?: return emptyList()
        return runCatching {
            if (storage.exists(path)) {
                val bytes = storage.read(path)
                json.decodeFromString<List<InstagramMediaOrAd>>(bytes.decodeToString())
            } else {
                emptyList()
            }
        }.getOrElse { emptyList() }
    }

    private fun persistToDisk(list: List<InstagramMediaOrAd>) {
        val storage = fileStorage ?: return
        val path = filePath ?: return
        runCatching {
            val bytes = json.encodeToString(list).encodeToByteArray()
            storage.write(path, bytes)
        }
    }

    public fun cacheItems(items: List<InstagramMediaOrAd>) {
        if (items.isEmpty()) return
        val current = cachedItems.value
        val merged = (items + current)
            .distinctBy { it.id.ifBlank { it.pk.orEmpty() } }
            .take(150)
        cachedItems.value = merged
        persistToDisk(merged)
    }

    public fun getLatestCachedItems(): List<InstagramMediaOrAd> = cachedItems.value

    public suspend fun syncSession(cookies: String, explicitUserId: String? = null): Boolean {
        if (!InstagramFeedService.hasActiveSession(cookies)) {
            return false
        }
        val userId = explicitUserId ?: InstagramFeedService.extractUserId(cookies) ?: return false

        val accountKey = MicroBlogKey(
            id = userId,
            host = INSTAGRAM_HOST,
        )

        val service = accountService ?: return false
        val existingAccounts = service.allAccountServicesFlow().first()
        val existingAccount = existingAccounts.firstOrNull { it.accountKey == accountKey }

        if (existingAccount == null) {
            service.addAccount(
                account = UiAccount(
                    accountKey = accountKey,
                    platformId = INSTAGRAM_PLATFORM_ID,
                    platformDisplayName = "Instagram",
                    platformIcon = UiIcon.Instagram,
                ),
                credential = InstagramCredential(
                    cookies = cookies,
                    userId = userId,
                ),
            )
        } else {
            service.updateCredential(
                accountKey = accountKey,
                credential = InstagramCredential(
                    cookies = cookies,
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
