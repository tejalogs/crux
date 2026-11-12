package org.crux.ui.screen.home

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URI

private const val COUNTERS_PREFS = "crux_web_privacy_counters"
private const val KEY_BLOCKED_REQUESTS = "blocked_requests"
private const val KEY_LINK_PARAMETERS = "link_parameters_removed"

private fun blockedBySiteKey(site: LiveWebSite): String = "blocked_requests.${site.name}"

/**
 * Running totals shown on the Privacy screen. They only count what the web tabs actually did:
 * requests refused on sites set to Strict, and tracking redirects and parameters removed from
 * links that left a site. Nothing here records which request or which link.
 */
internal class LiveWebPrivacyCounters private constructor(
    private val prefs: SharedPreferences,
) {
    private val blocked = MutableStateFlow(prefs.getLong(KEY_BLOCKED_REQUESTS, 0L))
    private val blockedPerSite =
        MutableStateFlow(
            LiveWebSite.entries.associateWith { site ->
                prefs.getLong(blockedBySiteKey(site), 0L)
            },
        )
    private val removed = MutableStateFlow(prefs.getLong(KEY_LINK_PARAMETERS, 0L))

    val blockedRequests: StateFlow<Long> = blocked.asStateFlow()
    val blockedRequestsBySite: StateFlow<Map<LiveWebSite, Long>> = blockedPerSite.asStateFlow()
    val parametersRemoved: StateFlow<Long> = removed.asStateFlow()

    fun recordBlockedRequest(site: LiveWebSite? = null) {
        val next = blocked.value + 1
        blocked.value = next
        val editor = prefs.edit().putLong(KEY_BLOCKED_REQUESTS, next)
        if (site != null) {
            val current = blockedPerSite.value
            val siteNext = (current[site] ?: 0L) + 1L
            blockedPerSite.value = current + (site to siteNext)
            editor.putLong(blockedBySiteKey(site), siteNext)
        }
        editor.apply()
    }

    /** Counts what cleaning took out of [original] to make [cleaned]; does nothing when they match. */
    fun recordLinkCleaned(
        original: String,
        cleaned: String,
    ) {
        val count = linkCleaningCount(original, cleaned)
        if (count <= 0) return
        val next = removed.value + count
        removed.value = next
        prefs.edit().putLong(KEY_LINK_PARAMETERS, next).apply()
    }

    companion object {
        @Volatile
        private var instance: LiveWebPrivacyCounters? = null

        fun get(context: Context): LiveWebPrivacyCounters =
            instance ?: synchronized(this) {
                instance ?: LiveWebPrivacyCounters(
                    context.applicationContext.getSharedPreferences(COUNTERS_PREFS, Context.MODE_PRIVATE),
                ).also { instance = it }
            }
    }
}

private fun queryNames(url: String): List<String> =
    runCatching { URI(url).rawQuery }
        .getOrNull()
        ?.split("&")
        ?.map { it.substringBefore("=") }
        ?.filter { it.isNotEmpty() }
        .orEmpty()

/**
 * How many things cleaning removed: one when a tracking redirect was replaced by the real address,
 * otherwise the query parameters that are gone. Pure, so it is tested without a device.
 */
internal fun linkCleaningCount(
    original: String,
    cleaned: String,
): Int {
    if (original == cleaned) return 0
    val originalHost = runCatching { URI(original).host }.getOrNull()
    val cleanedHost = runCatching { URI(cleaned).host }.getOrNull()
    // A tracking redirect counts once: its own wrapper parameters are not separate removals.
    if (originalHost != null && cleanedHost != null && originalHost != cleanedHost) return 1
    return (queryNames(original).size - queryNames(cleaned).size).coerceAtLeast(0)
}
