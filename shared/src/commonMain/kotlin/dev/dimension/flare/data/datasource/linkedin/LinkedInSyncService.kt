package dev.dimension.flare.data.datasource.linkedin

import dev.dimension.flare.data.platform.LINKEDIN_HOST
import dev.dimension.flare.data.platform.LINKEDIN_PLATFORM_ID
import dev.dimension.flare.data.platform.LinkedInCredential
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.data.repository.addAccount
import dev.dimension.flare.data.repository.updateCredential
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiIcon
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
@Single
public class LinkedInSyncService(
    private val accountService: AccountService? = null,
) {

    private val cachedUpdates = kotlinx.coroutines.flow.MutableStateFlow<List<LinkedInUpdate>>(emptyList())

    public fun cacheFeedPayload(jsonText: String) {
        val parsed = LinkedInFeedParser.parse(jsonText)
        if (parsed.elements.isNotEmpty()) {
            val current = cachedUpdates.value
            val merged = (parsed.elements + current)
                .distinctBy { it.id.ifBlank { it.urn.orEmpty() } }
                .take(100)
            cachedUpdates.value = merged
        }
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
