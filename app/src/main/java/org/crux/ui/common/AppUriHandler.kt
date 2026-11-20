package org.crux.ui.common

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.ui.platform.UriHandler
import androidx.core.net.toUri
import org.crux.ui.screen.home.LinkTarget
import org.crux.ui.screen.home.cleanOutgoingLink
import org.crux.ui.screen.home.resolveLinkTarget

/**
 * Opens every link in the phone's default browser app (a plain ACTION_VIEW through [fallback]),
 * after tracking parameters are removed. Internal `crux://` deep links are stamped with the
 * process-private [InternalDeeplinkToken] so `MainActivity` can distinguish in-app navigation from
 * untrusted external `BROWSABLE` intents.
 */
internal class AppUriHandler(
    private val fallback: UriHandler,
    private val context: Context? = null,
    private val onLinkCleaned: (original: String, cleaned: String) -> Unit = { _, _ -> },
    private val onFailure: () -> Unit,
) : UriHandler {
    override fun openUri(uri: String) {
        val target = resolveLinkTarget(uri)
        val opened =
            when (target) {
                LinkTarget.Invalid -> false

                is LinkTarget.InternalDeepLink -> {
                    val hostContext = context
                    if (hostContext != null) {
                        tryOpen {
                            val intent =
                                InternalDeeplinkToken.stamp(
                                    Intent(Intent.ACTION_VIEW, target.uri.toUri())
                                        .setPackage(hostContext.packageName),
                                )
                            if (hostContext !is Activity) {
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            hostContext.startActivity(intent)
                        }
                    } else {
                        tryOpen { fallback.openUri(target.uri) }
                    }
                }

                is LinkTarget.SystemBrowser -> tryOpen { fallback.openUri(target.url) }

                is LinkTarget.LiveWebScreen -> {
                    val cleaned = cleanOutgoingLink(uri.trim())
                    tryOpen { fallback.openUri(cleaned) }
                }
            }
        if (!opened) {
            onFailure()
        } else {
            val destination =
                when (target) {
                    is LinkTarget.InternalDeepLink -> target.uri
                    is LinkTarget.SystemBrowser -> target.url
                    is LinkTarget.LiveWebScreen -> cleanOutgoingLink(uri.trim())
                    LinkTarget.Invalid -> uri.trim()
                }
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
