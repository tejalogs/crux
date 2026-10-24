package dev.dimension.flare.data.datasource.instagram

import dev.dimension.flare.data.platform.INSTAGRAM_HOST
import dev.dimension.flare.data.platform.INSTAGRAM_PLATFORM_ID
import dev.dimension.flare.data.platform.InstagramCredential
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
public class InstagramSyncService(
    private val accountService: AccountService,
) {

    public suspend fun syncSession(cookies: String, explicitUserId: String? = null): Boolean {
        if (!InstagramFeedService.hasActiveSession(cookies)) {
            return false
        }
        val userId = explicitUserId ?: InstagramFeedService.extractUserId(cookies) ?: return false

        val accountKey = MicroBlogKey(
            id = userId,
            host = INSTAGRAM_HOST,
        )

        val existingAccounts = accountService.allAccountServicesFlow().first()
        val existingAccount = existingAccounts.firstOrNull { it.accountKey == accountKey }

        if (existingAccount == null) {
            accountService.addAccount(
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
            accountService.updateCredential(
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
        val existingAccounts = accountService.allAccountServicesFlow().first()
        return existingAccounts.any { it.platformId == INSTAGRAM_PLATFORM_ID }
    }
}
