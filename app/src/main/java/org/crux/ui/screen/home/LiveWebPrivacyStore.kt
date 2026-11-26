package org.crux.ui.screen.home

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val PREFS_NAME = "crux_web_privacy"
private const val KEY_CLEAN_LINKS = "clean_outgoing_links"
private const val KEY_OPEN_IN_READER = "open_links_in_reader_v2"
private const val KEY_BLOCK_THIRD_PARTY_COOKIES = "block_third_party_cookies"
private const val KEY_REMEMBER_TAB_POSITION = "remember_tab_position"
private const val KEY_HIDE_ENGAGEMENT_COUNTS = "hide_engagement_counts"
private const val KEY_SECURE_WINDOW = "secure_window"
private const val KEY_GPC = "send_gpc"

/**
 * Version of what is stored here. Missing (0) means 1.0.12 or earlier. Stamped on every write so
 * later releases can tell which defaults the saved values were written under.
 */
internal const val KEY_WEB_PRIVACY_SCHEMA = "web_privacy_schema"

/**
 * 2 (1.0.13): Reddit NSFW unblur became opt-in and Reddit/LinkedIn became tap-to-play.
 */
internal const val WEB_PRIVACY_SCHEMA_VERSION = 2

private fun protectionKey(site: LiveWebSite) = "protection.${site.name}"

private fun autoplayKey(site: LiveWebSite) = "block_autoplay_v2.${site.name}"

private fun tweakKey(tweak: SiteTweak) = "tweak_v2.${tweak.name}"

/** Sites whose saved autoplay value is reset once by the schema 2 migration. */
private val SCHEMA_2_AUTOPLAY_RESET = listOf(LiveWebSite.Reddit, LiveWebSite.LinkedIn)

/**
 * Moves values saved under an older schema onto this release's defaults, once.
 *
 * Up to 1.0.12, any change wrote every key with the defaults of the time, so a saved value does not
 * show what the person chose. Schema 2 therefore removes the Reddit NSFW unblur and the Reddit and
 * LinkedIn autoplay keys for everyone, which lets the new defaults (unblur off, tap to play) apply.
 * Choices made after this run are kept, because the version is already stamped.
 */
internal fun migrateWebPrivacyPrefs(prefs: SharedPreferences) {
    if (prefs.getInt(KEY_WEB_PRIVACY_SCHEMA, 0) >= WEB_PRIVACY_SCHEMA_VERSION) return
    prefs.edit(commit = true) {
        remove(tweakKey(SiteTweak.RedditUnblurNsfw))
        SCHEMA_2_AUTOPLAY_RESET.forEach { remove(autoplayKey(it)) }
        putInt(KEY_WEB_PRIVACY_SCHEMA, WEB_PRIVACY_SCHEMA_VERSION)
    }
}

/**
 * Persists [WebPrivacySettings] in SharedPreferences. Any change writes the full state, so a key
 * that is missing means "use the current default"; a default changed later reaches saved installs
 * only through [migrateWebPrivacyPrefs]. Reads are synchronous because a WebView's scripts and
 * blocking are fixed when it is created.
 */
internal class LiveWebPrivacyStore internal constructor(
    private val prefs: SharedPreferences,
) {
    private val mutable = MutableStateFlow(migrateThenRead())
    val settings: StateFlow<WebPrivacySettings> = mutable.asStateFlow()

    fun update(transform: (WebPrivacySettings) -> WebPrivacySettings) {
        val next = transform(mutable.value)
        if (next == mutable.value) return
        write(next)
        mutable.update { next }
    }

    fun configFor(site: LiveWebSite): LiveWebConfig = mutable.value.configFor(site)

    private fun migrateThenRead(): WebPrivacySettings {
        runCatching { migrateWebPrivacyPrefs(prefs) }
            .onFailure { Log.w(TAG, "Web privacy migration failed; reading saved values", it) }
        return read()
    }

    private fun read(): WebPrivacySettings {
        val sites =
            LiveWebSite.entries.associateWith { site ->
                val defaults = SiteWebSettings.defaultsFor(site)
                SiteWebSettings(
                    protection =
                        prefs
                            .getString(protectionKey(site), null)
                            ?.let { name -> ProtectionLevel.entries.firstOrNull { it.name == name } }
                            ?: defaults.protection,
                    tweaks =
                        SiteTweak
                            .forSite(site)
                            .filter { prefs.getBoolean(tweakKey(it), it.defaultOn) }
                            .toSet(),
                    blockAutoplay = prefs.getBoolean(autoplayKey(site), defaults.blockAutoplay),
                )
            }
        val defaults = WebPrivacySettings.Default
        return WebPrivacySettings(
            sites = sites,
            cleanOutgoingLinks = prefs.getBoolean(KEY_CLEAN_LINKS, defaults.cleanOutgoingLinks),
            openLinksInReader = prefs.getBoolean(KEY_OPEN_IN_READER, defaults.openLinksInReader),
            blockThirdPartyCookies = prefs.getBoolean(KEY_BLOCK_THIRD_PARTY_COOKIES, defaults.blockThirdPartyCookies),
            rememberTabPosition = prefs.getBoolean(KEY_REMEMBER_TAB_POSITION, defaults.rememberTabPosition),
            hideEngagementCounts = prefs.getBoolean(KEY_HIDE_ENGAGEMENT_COUNTS, defaults.hideEngagementCounts),
            secureWindow = prefs.getBoolean(KEY_SECURE_WINDOW, defaults.secureWindow),
            sendGlobalPrivacyControl = prefs.getBoolean(KEY_GPC, defaults.sendGlobalPrivacyControl),
        )
    }

    private fun write(settings: WebPrivacySettings) {
        prefs.edit {
            LiveWebSite.entries.forEach { site ->
                val value = settings.site(site)
                putString(protectionKey(site), value.protection.name)
                putBoolean(autoplayKey(site), value.blockAutoplay)
                SiteTweak.forSite(site).forEach { putBoolean(tweakKey(it), value.isOn(it)) }
            }
            putBoolean(KEY_CLEAN_LINKS, settings.cleanOutgoingLinks)
            putBoolean(KEY_OPEN_IN_READER, settings.openLinksInReader)
            putBoolean(KEY_BLOCK_THIRD_PARTY_COOKIES, settings.blockThirdPartyCookies)
            putBoolean(KEY_REMEMBER_TAB_POSITION, settings.rememberTabPosition)
            putBoolean(KEY_HIDE_ENGAGEMENT_COUNTS, settings.hideEngagementCounts)
            putBoolean(KEY_SECURE_WINDOW, settings.secureWindow)
            putBoolean(KEY_GPC, settings.sendGlobalPrivacyControl)
            putInt(KEY_WEB_PRIVACY_SCHEMA, WEB_PRIVACY_SCHEMA_VERSION)
        }
    }

    fun resetToDefaults() {
        update { WebPrivacySettings.Default }
    }

    companion object {
        private const val TAG = "LiveWebPrivacyStore"

        @Volatile
        private var instance: LiveWebPrivacyStore? = null

        fun get(context: Context): LiveWebPrivacyStore =
            instance ?: synchronized(this) {
                instance ?: LiveWebPrivacyStore(
                    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
                ).also { instance = it }
            }
    }
}
