package org.crux.ui.screen.home

import java.net.URI
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Message pages on the supported sites: Instagram /direct, X /messages and /i/chat, LinkedIn
 * /messaging, Reddit /chat and /message (old inbox), Mastodon /conversations. Anchored at the
 * first segment, so a community or profile named "chat" (/r/chat, /in/chat) is not one.
 */
internal const val MESSAGING_PATH_PATTERN: String =
    "^/(?:direct|messages|messaging|message|chat|conversations|i/chat|i/messages)(?:/|$)"

private val MESSAGING_PATH = Regex(MESSAGING_PATH_PATTERN, RegexOption.IGNORE_CASE)

/** Sites that keep messages on their own host (chat.reddit.com). */
private const val MESSAGING_HOST_PREFIX = "chat."

/** True for a DM thread or inbox on any supported site. */
internal fun isMessagingUrl(url: String?): Boolean {
    val uri = url?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { URI(it) }.getOrNull() } ?: return false
    val scheme = uri.scheme?.lowercase()
    if (scheme != "https" && scheme != "http") return false
    val host = uri.host?.lowercase()?.trimEnd('.').orEmpty()
    if (host.startsWith(MESSAGING_HOST_PREFIX)) return true
    return MESSAGING_PATH.containsMatchIn(uri.path.orEmpty())
}

/**
 * Pages that are never reopened on a cold start: full-screen video and Stories
 * ([isImmersivePath]) and every message page [isMessagingUrl] knows, including X's newer Chat
 * inbox under /i/chat, which the first-segment immersive list does not reach. Meant to be the
 * tab's pull-to-refresh gate too; that call site still uses [isImmersivePath] for now.
 */
internal fun isPrivateOrImmersiveUrl(url: String?): Boolean = isImmersivePath(url) || isMessagingUrl(url)

/**
 * Pages whose content must not reach screenshots, screen recording, casting, Recents or screen
 * assistants: messages, and sign-in pages (where an email, a password or a one-time code is
 * typed or shown), including Google and Apple sign-in inside a site tab.
 */
internal fun isPrivateScreenUrl(url: String?): Boolean =
    isMessagingUrl(url) || LiveWebSessionState.isAuthOrLoginUrl(url) || isExternalOAuthUrl(url)

/** Whether a tab asks for a secure window: it is on screen and showing a private page. */
internal fun tabNeedsSecureWindow(
    isVisible: Boolean,
    url: String?,
): Boolean = isVisible && isPrivateScreenUrl(url)

/**
 * FLAG_SECURE for the main window: the user's "secure window" setting, App Lock where Android
 * has no other way to blank the Recents card (12 and below, [legacyRecents]), or a visible tab on
 * a private page. Any one of them is enough; none of them can turn another off.
 */
internal fun secureWindowWanted(
    userSecureWindow: Boolean,
    requireAppLock: Boolean,
    privateScreenShown: Boolean,
    legacyRecents: Boolean,
): Boolean = userSecureWindow || (requireAppLock && legacyRecents) || privateScreenShown

/** Android 13+: Recents shows a blank card under App Lock and once site pages were opened. */
internal fun recentsPreviewHidden(
    requireAppLock: Boolean,
    webContentShown: Boolean,
): Boolean = requireAppLock || webContentShown

/**
 * Process-wide record of which site tabs are on a private page right now, read by MainActivity
 * to turn FLAG_SECURE on while any of them is. Each tab is its own owner, so a tab leaving a DM
 * never turns protection off while another visible tab still needs it.
 */
internal object LiveWebPrivateScreens {
    private val owners = MutableStateFlow<Set<Any>>(emptySet())
    private val webShown = MutableStateFlow(false)

    /** True while at least one visible tab is on a private page. */
    val active: Flow<Boolean> = owners.map { it.isNotEmpty() }.distinctUntilChanged()

    val isActive: Boolean
        get() = owners.value.isNotEmpty()

    /**
     * True once a site page was actually on screen in this process (not merely created, so
     * preloaded pages and RSS-only use keep the Recents preview). Recents thumbnails are taken
     * when the app is left, from whatever screen is up, so from then on Android 13+ is asked for
     * a blank card instead.
     */
    val webContentShown: StateFlow<Boolean> = webShown.asStateFlow()

    fun update(
        owner: Any,
        needsSecure: Boolean,
    ) {
        owners.update { if (needsSecure) it + owner else it - owner }
    }

    fun markWebContentShown() {
        if (!webShown.value) webShown.value = true
    }

    /** For tests: forgets every owner. */
    internal fun resetForTest() {
        owners.value = emptySet()
        webShown.value = false
    }
}
