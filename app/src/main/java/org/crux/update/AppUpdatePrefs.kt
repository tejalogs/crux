package org.crux.update

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

internal const val AUTO_CHECK_INTERVAL_MS: Long = 6L * 60L * 60L * 1000L

private const val PREFS_NAME = "crux_app_updates"
private const val KEY_AUTO_CHECK_ENABLED = "auto_check_enabled"
private const val KEY_LAST_CHECK_AT_MS = "last_check_at_ms"
private const val KEY_SKIPPED_TAG = "skipped_tag"

internal fun shouldRunAutoUpdateCheck(
    autoCheckEnabled: Boolean,
    lastCheckAtMs: Long,
    nowMs: Long,
    intervalMs: Long = AUTO_CHECK_INTERVAL_MS,
): Boolean {
    if (!autoCheckEnabled) return false
    if (lastCheckAtMs <= 0L) return true
    if (nowMs < lastCheckAtMs) return false
    return (nowMs - lastCheckAtMs) >= intervalMs
}

internal fun shouldPromptRelease(
    releaseTag: String,
    skippedTag: String?,
    currentVersion: String,
): Boolean {
    if (!AppUpdateManager.isNewerVersion(releaseTag, currentVersion)) return false
    val normalizedSkipped = skippedTag?.trim()?.takeIf { it.isNotEmpty() } ?: return true
    return !releaseTag.trim().equals(normalizedSkipped, ignoreCase = true)
}

/**
 * Tracks auto-update preferences, cooldown timestamps, and skipped release tags across restarts,
 * while capping automatic checks to at most once per app process.
 */
internal class AppUpdatePrefs private constructor(
    private val prefs: SharedPreferences,
) {
    private val autoCheckMutable = MutableStateFlow(prefs.getBoolean(KEY_AUTO_CHECK_ENABLED, true))
    private val checkedThisProcess = AtomicBoolean(false)

    val autoCheckEnabled: StateFlow<Boolean> = autoCheckMutable.asStateFlow()

    fun setAutoCheckEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_CHECK_ENABLED, enabled) }
        autoCheckMutable.value = enabled
    }

    fun tryStartSessionCheck(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!checkedThisProcess.compareAndSet(false, true)) return false
        return shouldRunAutoUpdateCheck(
            autoCheckEnabled = autoCheckMutable.value,
            lastCheckAtMs = prefs.getLong(KEY_LAST_CHECK_AT_MS, 0L),
            nowMs = nowMs,
        )
    }

    fun recordCheckCompleted(nowMs: Long = System.currentTimeMillis()) {
        prefs.edit { putLong(KEY_LAST_CHECK_AT_MS, nowMs) }
    }

    fun skippedTag(): String? = prefs.getString(KEY_SKIPPED_TAG, null)

    fun skipVersion(tag: String) {
        prefs.edit { putString(KEY_SKIPPED_TAG, tag.trim()) }
    }

    fun clearSkippedVersion() {
        prefs.edit { remove(KEY_SKIPPED_TAG) }
    }

    companion object {
        @Volatile
        private var instance: AppUpdatePrefs? = null

        fun get(context: Context): AppUpdatePrefs =
            instance ?: synchronized(this) {
                instance ?: AppUpdatePrefs(
                    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
                ).also { instance = it }
            }
    }
}
