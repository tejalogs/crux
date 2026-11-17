package org.crux.data.repository

import kotlinx.coroutines.flow.first
import org.crux.data.platform.INSTAGRAM_PLATFORM_ID
import org.crux.data.platform.LINKEDIN_PLATFORM_ID
import org.koin.core.annotation.Single

private const val X_PLATFORM_ID = "xQt"
private val SHADOW_WEB_PLATFORM_IDS = setOf(INSTAGRAM_PLATFORM_ID, LINKEDIN_PLATFORM_ID)

/**
 * Removes legacy native X accounts (along with their tabs) and, once live web tabs are migrated,
 * removes leftover Instagram/LinkedIn shadow DB accounts now superseded by cookie-jar checks.
 */
@Single
public class NativeXRemoval internal constructor(
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
) {
    public suspend fun run() {
        val accounts = accountRepository.allAccounts.first()
        val xAccounts = accounts.filter { it.platformId == X_PLATFORM_ID }
        settingsRepository.replaceNativeXTabs(xAccounts.map { it.accountKey.toString() }.toSet())
        xAccounts.forEach { accountRepository.delete(it.accountKey).join() }

        if (settingsRepository.appSettings.first().liveWebTabsMigrated) {
            accounts
                .filter { it.platformId in SHADOW_WEB_PLATFORM_IDS }
                .forEach { accountRepository.delete(it.accountKey).join() }
        }
    }
}
