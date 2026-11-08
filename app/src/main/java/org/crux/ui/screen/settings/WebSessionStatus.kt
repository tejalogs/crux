package org.crux.ui.screen.settings

/** What the Privacy screen says about one site's stored web data. */
internal data class WebSessionStatus(
    val signedIn: Boolean,
    /** Clear is only offered when the site has stored something to clear. */
    val canClear: Boolean,
)

/**
 * [signedIn] is the site's own sign-in check (a real session cookie). [hasStoredData] is any cookie
 * at all, which a signed-out site can still have, and is the only reason to offer Clear then.
 */
internal fun webSessionStatus(
    signedIn: Boolean,
    hasStoredData: Boolean,
): WebSessionStatus = WebSessionStatus(signedIn = signedIn, canClear = signedIn || hasStoredData)
