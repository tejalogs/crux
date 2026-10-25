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
    private val accountService: AccountService,
) {

    public suspend fun syncSession(cookies: String, explicitUserId: String? = null): Boolean {
        if (!LinkedInFeedService.hasActiveSession(cookies)) {
            return false
        }
        val userId = explicitUserId ?: LinkedInFeedService.extractUserId(cookies) ?: return false

        val accountKey = MicroBlogKey(
            id = userId,
            host = LINKEDIN_HOST,
        )

        val existingAccounts = accountService.allAccountServicesFlow().first()
        val existingAccount = existingAccounts.firstOrNull { it.accountKey == accountKey }

        if (existingAccount == null) {
            accountService.addAccount(
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
            accountService.updateCredential(
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
        val existingAccounts = accountService.allAccountServicesFlow().first()
        return existingAccounts.any { it.platformId == LINKEDIN_PLATFORM_ID }
    }
}
