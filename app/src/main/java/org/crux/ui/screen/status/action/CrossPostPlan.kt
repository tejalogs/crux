package org.crux.ui.screen.status.action

import org.crux.model.MicroBlogKey
import org.crux.ui.screen.home.LiveWebSite

/**
 * Coordinates unified 1-tap broadcasting across both direct API-connected accounts
 * (none remain in Crux) and isolated Live Web sessions (LinkedIn, Instagram, X, Reddit).
 */
internal data class CrossPostPlan(
    val apiAccountKeys: List<MicroBlogKey>,
    val webSites: List<LiveWebSite>,
    val attachCarousel: Boolean,
) {
    val hasApiTargets: Boolean get() = apiAccountKeys.isNotEmpty()
    val hasWebTargets: Boolean get() = webSites.isNotEmpty()
    val totalTargetCount: Int get() = apiAccountKeys.size + webSites.size

    val actionButtonLabel: String
        get() =
            when {
                hasApiTargets && hasWebTargets -> "Broadcast & Cross-Post ($totalTargetCount)"
                hasWebTargets -> "Stage in ${webSites.size} Web Session${if (webSites.size > 1) "s" else ""}"
                hasApiTargets -> "Broadcast to ${apiAccountKeys.size} Account${if (apiAccountKeys.size > 1) "s" else ""}"
                else -> "Broadcast"
            }
}
