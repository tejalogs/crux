package org.crux.ui.common

import android.content.ActivityNotFoundException
import androidx.compose.ui.platform.UriHandler
import org.crux.ui.screen.home.LinkTarget
import org.crux.ui.screen.home.cleanOutgoingLink
import org.crux.ui.screen.home.resolveLinkTarget

/**
 * Opens every link in the phone's default browser app (a plain ACTION_VIEW through [fallback]),
 * after tracking parameters are removed. Links to Instagram, X, LinkedIn and Reddit are routed to
 * their live web screens before they reach this handler; reaching it from outside the home
 * navigation they still open in the browser.
 */
internal class AppUriHandler(
    private val fallback: UriHandler,
    private val onLinkCleaned: (original: String, cleaned: String) -> Unit = { _, _ -> },
    private val onFailure: () -> Unit,
) : UriHandler {
    override fun openUri(uri: String) {
        val destination =
            when (val target = resolveLinkTarget(uri)) {
                LinkTarget.Invalid -> null
                is LinkTarget.InternalDeepLink -> target.uri
                is LinkTarget.SystemBrowser -> target.url
                is LinkTarget.LiveWebScreen -> cleanOutgoingLink(uri.trim())
            }
        if (destination == null || !tryOpen { fallback.openUri(destination) }) {
            onFailure()
        } else {
            onLinkCleaned(uri.trim(), destination)
        }
    }

    private inline fun tryOpen(block: () -> Unit): Boolean =
        try {
            block()
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: IllegalArgumentException) {
            // Compose wraps ActivityNotFoundException in IllegalArgumentException.
            false
        } catch (_: SecurityException) {
            false
        }
}
