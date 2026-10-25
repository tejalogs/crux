package dev.dimension.flare.data.datasource.linkedin

import dev.dimension.flare.data.io.FileStorage
import dev.dimension.flare.data.platform.LINKEDIN_HOST
import dev.dimension.flare.data.platform.LINKEDIN_PLATFORM_ID
import dev.dimension.flare.data.platform.LinkedInCredential
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
public class LinkedInSyncService internal constructor(
    private val accountService: AccountService? = null,
    private val fileStorage: FileStorage? = null,
) {
    public constructor(accountService: AccountService?) : this(accountService, null)
    public constructor() : this(null, null)

    private val filePath by lazy { fileStorage?.dataStoreFile("linkedin_feed_cache.json") }
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val cachedUpdates = MutableStateFlow<List<LinkedInUpdate>>(loadFromDisk())

    private fun loadFromDisk(): List<LinkedInUpdate> {
        val storage = fileStorage ?: return emptyList()
        val path = filePath ?: return emptyList()
        return runCatching {
            if (storage.exists(path)) {
                val bytes = storage.read(path)
                json.decodeFromString<List<LinkedInUpdate>>(bytes.decodeToString())
            } else {
                emptyList()
            }
        }.getOrElse { emptyList() }
    }

    private fun persistToDisk(list: List<LinkedInUpdate>) {
        val storage = fileStorage ?: return
        val path = filePath ?: return
        runCatching {
            val bytes = json.encodeToString(list).encodeToByteArray()
            storage.write(path, bytes)
        }
    }

    public fun cacheFeedPayload(jsonText: String) {
        val parsed = LinkedInFeedParser.parse(jsonText)
        if (parsed.elements.isNotEmpty()) {
            cacheUpdates(parsed.elements)
        }
    }

    public fun cacheUpdates(updates: List<LinkedInUpdate>) {
        if (updates.isEmpty()) return
        val current = cachedUpdates.value
        val merged = (updates + current)
            .distinctBy { it.id.ifBlank { it.urn ?: it.entityUrn ?: it.trackingUrn }.orEmpty() }
            .take(150)
        cachedUpdates.value = merged
        persistToDisk(merged)
    }

    public fun getLatestCachedUpdates(): List<LinkedInUpdate> = cachedUpdates.value

    public suspend fun syncSession(cookies: String, explicitUserId: String? = null): Boolean {
        if (!LinkedInFeedService.hasActiveSession(cookies)) {
            return false
        }
        val userId = explicitUserId ?: LinkedInFeedService.extractUserId(cookies) ?: return false

        val accountKey = MicroBlogKey(
            id = userId,
            host = LINKEDIN_HOST,
        )

        val service = accountService ?: return false
        val existingAccounts = service.allAccountServicesFlow().first()
        val existingAccount = existingAccounts.firstOrNull { it.accountKey == accountKey }

        if (existingAccount == null) {
            service.addAccount(
                account = UiAccount(
                    accountKey = accountKey,
                    platformId = LINKEDIN_PLATFORM_ID,
                    platformDisplayName = "LinkedIn",
                    platformIcon = UiIcon.LinkedIn,
                ),
                credential = LinkedInCredential(
                    cookies = cookies,
                    userId = userId,
                ),
            )
        } else {
            service.updateCredential(
                accountKey = accountKey,
                credential = LinkedInCredential(
                    cookies = cookies,
                    userId = userId,
                ),
            )
        }

        return true
    }

    public suspend fun isLinkedInAccountRegistered(): Boolean {
        val service = accountService ?: return false
        val existingAccounts = service.allAccountServicesFlow().first()
        return existingAccounts.any { it.platformId == LINKEDIN_PLATFORM_ID }
    }
}
