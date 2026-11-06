package org.crux.data.ai.scribe

import kotlinx.serialization.Serializable
import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiAccount
import org.crux.ui.model.UiProfile
import org.crux.ui.model.UiState
import org.crux.ui.presenter.settings.AccountsState

/**
 * Metadata for a connected account eligible for Omni-Broadcast publication.
 */
@Serializable
public data class BroadcastTargetAccount(
    public val accountKey: MicroBlogKey,
    public val platformId: String,
    public val handle: String,
    public val displayName: String,
    public val avatarUrl: String? = null,
    public val maxCharacterLimit: Int = 280,
)

/**
 * Result of publishing a post to an individual network.
 */
@Serializable
public data class NetworkBroadcastResult(
    public val accountKey: MicroBlogKey,
    public val platformId: String,
    public val success: Boolean,
    public val message: String? = null,
)

/**
 * Coordinates multi-network broadcasting across connected accounts.
 */
public object OmniBroadcastService {
    /**
     * Resolves the maximum character limit for a social platform.
     */
    public fun getPlatformCharacterLimit(platformId: String): Int =
        when (platformId.lowercase()) {
            "x", "twitter", "xqt" -> 280
            "linkedin" -> 3000
            else -> 280
        }

    /**
     * Maps available UI accounts to broadcast targets with character limits and platform metadata.
     */
    public fun mapToBroadcastTargets(accounts: List<UiAccount>): List<BroadcastTargetAccount> =
        accounts.map { account ->
            BroadcastTargetAccount(
                accountKey = account.accountKey,
                platformId = account.platformId,
                handle = account.platformDisplayName,
                displayName = account.platformDisplayName,
                avatarUrl = null,
                maxCharacterLimit = getPlatformCharacterLimit(account.platformId),
            )
        }

    /**
     * Maps rich AccountItem models to broadcast targets with full profile metadata.
     */
    public fun mapAccountItems(items: List<AccountsState.AccountItem>): List<BroadcastTargetAccount> =
        items.map { item ->
            val profile = (item.profile as? UiState.Success<UiProfile>)?.data
            BroadcastTargetAccount(
                accountKey = item.account.accountKey,
                platformId = item.account.platformId,
                handle = profile?.handle?.canonical ?: item.account.platformDisplayName,
                displayName = profile?.name?.raw ?: item.account.platformDisplayName,
                avatarUrl = profile?.avatar?.url,
                maxCharacterLimit = getPlatformCharacterLimit(item.account.platformId),
            )
        }

    /**
     * Validates whether a text payload satisfies a platform's character limits.
     */
    public fun isLengthValid(
        text: String,
        platformId: String,
    ): Boolean {
        val limit = getPlatformCharacterLimit(platformId)
        return text.length <= limit
    }

    /**
     * Selects the optimal text format (Micro vs Story) based on platform profile.
     */
    public fun selectOptimalText(
        result: OmniScribeResult,
        platformId: String,
    ): String =
        when (platformId.lowercase()) {
            "linkedin" -> result.storyPost
            else -> result.microPost
        }
}
