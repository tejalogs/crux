package org.crux.ui.screen.home

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val PREFS_NAME = "crux_web_privacy"
private const val KEY_CLEAN_LINKS = "clean_outgoing_links"
private const val KEY_SECURE_WINDOW = "secure_window"
private const val KEY_GPC = "send_gpc"

private fun protectionKey(site: LiveWebSite) = "protection.${site.name}"

private fun autoplayKey(site: LiveWebSite) = "block_autoplay.${site.name}"

private fun tweakKey(tweak: SiteTweak) = "tweak.${tweak.name}"

/**
 * Persists [WebPrivacySettings] in SharedPreferences. Only values the person changed are written,
 * so a default added later reaches existing installs. Reads are synchronous because a WebView's
 * scripts and blocking are fixed when it is created.
 */
internal class LiveWebPrivacyStore private constructor(
    private val prefs: SharedPreferences,
) {
    private val mutable = MutableStateFlow(read())
    val settings: StateFlow<WebPrivacySettings> = mutable.asStateFlow()

    fun update(transform: (WebPrivacySettings) -> WebPrivacySettings) {
        val next = transform(mutable.value)
        if (next == mutable.value) return
        write(next)
        mutable.update { next }
    }

    fun configFor(site: LiveWebSite): LiveWebConfig = mutable.value.configFor(site)

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
            putBoolean(KEY_SECURE_WINDOW, settings.secureWindow)
            putBoolean(KEY_GPC, settings.sendGlobalPrivacyControl)
        }
    }

    companion object {
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
