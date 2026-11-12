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
import java.net.URI

private val NON_READER_HOSTS =
    setOf(
        "play.google.com",
        "apps.apple.com",
        "youtube.com",
        "www.youtube.com",
        "m.youtube.com",
        "youtu.be",
        "accounts.google.com",
        "appleid.apple.com",
    )

private val BINARY_PATH_SUFFIX =
    Regex("\\.(?:pdf|apk|zip|tar|gz|mp4|webm|mp3|wav|jpg|jpeg|png|gif|webp)$", RegexOption.IGNORE_CASE)

internal fun isReaderEligibleUrl(url: String): Boolean {
    val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase() ?: return false
    if (scheme != "http" && scheme != "https") return false
    val host = uri.host?.lowercase() ?: return false
    if (host in NON_READER_HOSTS) return false
    val path = uri.path.orEmpty()
    if (BINARY_PATH_SUFFIX.containsMatchIn(path)) return false
    return true
}

/**
 * Opens a link that a live web page does not keep for itself. A link to another social site goes to
 * Crux's own screen for it, through the app's crux:// link so the home navigation shows it. When
 * [openInReader] is true, eligible HTTP(S) article links open in Crux's distraction-free reader;
 * otherwise links go to the phone's default browser with tracking removed when [clean] is set.
 */
internal fun openLinkOutsideWebView(
    context: Context,
    url: String,
    clean: Boolean,
    openInReader: Boolean = LiveWebPrivacyStore.get(context).settings.value.openLinksInReader,
) {
    val intent =
        when (val target = resolveLinkTarget(url, clean)) {
            is LinkTarget.LiveWebScreen -> {
                val link = target.route.initialLink() ?: url
                Intent(Intent.ACTION_VIEW, DeeplinkRoute.OpenLinkDirectly(link).toUri().toUri()).setPackage(context.packageName)
            }

            is LinkTarget.SystemBrowser -> {
                if (openInReader && isReaderEligibleUrl(target.url)) {
                    Intent(
                        Intent.ACTION_VIEW,
                        DeeplinkRoute.Rss.Detail(url = target.url).toUri().toUri(),
                    ).setPackage(context.packageName)
                } else {
                    Intent(Intent.ACTION_VIEW, target.url.toUri())
                }
            }

            is LinkTarget.InternalDeepLink, LinkTarget.Invalid -> {
                return
            }
        }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
        if (clean) LiveWebPrivacyCounters.get(context).recordLinkCleaned(url.trim(), cleanOutgoingLink(url.trim()))
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
        LiveWebPrivacyCounters.get(context).recordLinkCleaned(url.trim(), link)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.unable_to_open_link, Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, R.string.unable_to_open_link, Toast.LENGTH_SHORT).show()
    }
}
