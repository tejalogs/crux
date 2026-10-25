package dev.dimension.flare.data.ai.scribe

import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.presenter.settings.AccountsState
import kotlinx.serialization.Serializable

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
    public fun getPlatformCharacterLimit(platformId: String): Int {
        return when (platformId.lowercase()) {
            "x", "twitter", "xqt" -> 280
            "bluesky", "bsky" -> 300
            "mastodon" -> 500
            "misskey" -> 1000
            "linkedin" -> 3000
            "nostr" -> 10000
            else -> 280
        }
    }

    /**
     * Maps available UI accounts to broadcast targets with character limits and platform metadata.
     */
    public fun mapToBroadcastTargets(accounts: List<UiAccount>): List<BroadcastTargetAccount> {
        return accounts.map { account ->
            BroadcastTargetAccount(
                accountKey = account.accountKey,
                platformId = account.platformId,
                handle = account.platformDisplayName,
                displayName = account.platformDisplayName,
                avatarUrl = null,
                maxCharacterLimit = getPlatformCharacterLimit(account.platformId),
            )
        }
    }

    /**
     * Maps rich AccountItem models to broadcast targets with full profile metadata.
     */
    public fun mapAccountItems(items: List<AccountsState.AccountItem>): List<BroadcastTargetAccount> {
        return items.map { item ->
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
    }

    /**
     * Validates whether a text payload satisfies a platform's character limits.
     */
    public fun isLengthValid(text: String, platformId: String): Boolean {
        val limit = getPlatformCharacterLimit(platformId)
        return text.length <= limit
    }

    /**
     * Selects the optimal text format (Micro vs Story) based on platform profile.
     */
    public fun selectOptimalText(
        result: OmniScribeResult,
        platformId: String,
    ): String {
        return when (platformId.lowercase()) {
            "linkedin" -> result.storyPost
            else -> result.microPost
        }
    }
}
