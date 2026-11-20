package org.crux.ui.common

import android.content.Intent
import org.crux.ui.route.DeeplinkRoute
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Distinguishes Crux's own in-process `crux://` navigation intents from untrusted external
 * `BROWSABLE` intents launched by browsers or third-party apps.
 */
internal object InternalDeeplinkToken {
    private const val EXTRA_INTERNAL_TOKEN = "org.crux.extra.INTERNAL_DEEPLINK_TOKEN"

    private val processToken: ByteArray by lazy {
        ByteArray(32).also { SecureRandom().nextBytes(it) }
    }

    fun stamp(intent: Intent): Intent = intent.putExtra(EXTRA_INTERNAL_TOKEN, processToken.copyOf())

    fun isTrustedInternalIntent(intent: Intent?): Boolean {
        val supplied = intent?.getByteArrayExtra(EXTRA_INTERNAL_TOKEN) ?: return false
        return supplied.size == processToken.size && MessageDigest.isEqual(supplied, processToken)
    }

    /**
     * External apps and browsers may only invoke safe, non-privileged entry routes.
     * Privileged internal routes (Media.Image with arbitrary URIs/headers, Rss.Detail with raw HTML,
     * OpenLinkDirectly, AI prompt routes, and internal flare-event payloads) require a trusted
     * in-process intent stamp.
     */
    fun isAllowedForIntent(
        url: String,
        intent: Intent?,
    ): Boolean = isAllowedDeeplink(url = url, isTrustedInternal = isTrustedInternalIntent(intent))

    internal fun isAllowedDeeplink(
        url: String,
        isTrustedInternal: Boolean,
    ): Boolean {
        if (isTrustedInternal) return true
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return false
        if (!DeeplinkRoute.isDeeplink(trimmed)) return false
        return when (DeeplinkRoute.parse(trimmed)) {
            is DeeplinkRoute.Compose.Crux,
            is DeeplinkRoute.Compose.New,
            is DeeplinkRoute.Login,
            -> true

            else -> false
        }
    }
}
