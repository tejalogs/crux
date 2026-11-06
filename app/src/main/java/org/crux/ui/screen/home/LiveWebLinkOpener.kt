package org.crux.ui.screen.home

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import org.crux.R
import org.crux.ui.route.DeeplinkRoute
import org.crux.ui.route.Route
import org.crux.ui.route.toUri

/**
 * Opens a link that a live web page does not keep for itself. A link to another social site goes to
 * Crux's own screen for it, through the app's crux:// link so the home navigation shows it. Every
 * other link goes to the phone's default browser with tracking removed when [clean] is set.
 */
internal fun openLinkOutsideWebView(
    context: Context,
    url: String,
    clean: Boolean,
) {
    val intent =
        when (val target = resolveLinkTarget(url, clean)) {
            is LinkTarget.LiveWebScreen -> {
                val link = target.route.initialLink() ?: url
                Intent(Intent.ACTION_VIEW, DeeplinkRoute.OpenLinkDirectly(link).toUri().toUri()).setPackage(context.packageName)
            }

            is LinkTarget.SystemBrowser -> {
                Intent(Intent.ACTION_VIEW, target.url.toUri())
            }

            is LinkTarget.InternalDeepLink, LinkTarget.Invalid -> {
                return
            }
        }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.unable_to_open_link, Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, R.string.unable_to_open_link, Toast.LENGTH_SHORT).show()
    }
}

private fun Route.initialLink(): String? =
    when (this) {
        is Route.LiveWeb -> initialUrl
        is Route.InstagramWeb -> initialUrl
        is Route.LinkedInWeb -> initialUrl
        else -> null
    }

/** The explicit "open in browser" buttons: the page's own URL goes to the default browser, never back into Crux. */
internal fun openInSystemBrowser(
    context: Context,
    url: String,
) {
    val link = cleanOutgoingLink(url.trim())
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, link.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.unable_to_open_link, Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, R.string.unable_to_open_link, Toast.LENGTH_SHORT).show()
    }
}
