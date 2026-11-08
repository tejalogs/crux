package org.crux.ui.screen.status.action

import org.crux.model.MicroBlogKey
import org.crux.ui.screen.home.LiveWebSite

/** What the primary button of the post sheet does for the chosen targets. */
internal enum class CrossPostAction {
    /** Nothing is selected. */
    None,

    /** Post directly through connected accounts. */
    Accounts,

    /** Copy the text and open the chosen sites (Instagram, LinkedIn, X, Reddit) to paste it. */
    Sites,

    /** Both of the above. */
    Both,
}

/**
 * The chosen targets for one post: connected accounts (none remain in Crux) and the live web
 * sessions of Instagram, LinkedIn, X and Reddit.
 */
internal data class CrossPostPlan(
    val apiAccountKeys: List<MicroBlogKey>,
    val webSites: List<LiveWebSite>,
    val attachCarousel: Boolean,
) {
    val hasApiTargets: Boolean get() = apiAccountKeys.isNotEmpty()
    val hasWebTargets: Boolean get() = webSites.isNotEmpty()
    val totalTargetCount: Int get() = apiAccountKeys.size + webSites.size

    val action: CrossPostAction
        get() =
            when {
                hasApiTargets && hasWebTargets -> CrossPostAction.Both
                hasWebTargets -> CrossPostAction.Sites
                hasApiTargets -> CrossPostAction.Accounts
                else -> CrossPostAction.None
            }
}
