package org.crux.data.repository

import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single

private const val X_PLATFORM_ID = "xQt"

/** X is a live browser tab now, so old native X accounts and their tabs are removed once. */
@Single
public class NativeXRemoval internal constructor(
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
) {
    public suspend fun run() {
        val xAccounts = accountRepository.allAccounts.first().filter { it.platformId == X_PLATFORM_ID }
        settingsRepository.replaceNativeXTabs(xAccounts.map { it.accountKey.toString() }.toSet())
        xAccounts.forEach { accountRepository.delete(it.accountKey) }
    }
}
