package org.crux.ui.lock

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Lock
import org.crux.R
import org.crux.ui.component.FAIcon

/** How long Crux can sit in the background before it asks to unlock again. */
internal const val APP_LOCK_GRACE_MS: Long = 10_000L

/**
 * Process-wide app lock state, shared by every Crux activity.
 *
 * Living in the process (not in an activity) means rotation, dark-mode switches and moving between
 * Crux's own screens never re-lock, while process death always starts locked. "Backgrounded" means
 * no Crux activity is started, counted across activities.
 */
internal class AppLockSession(
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val graceMs: Long = APP_LOCK_GRACE_MS,
) {
    var isUnlocked by mutableStateOf(false)
        private set

    /** True when the next locked frame should open the system prompt by itself. */
    var autoPromptArmed by mutableStateOf(true)
        private set

    /** The system unlock screen is showing; its own activity must not count as leaving Crux. */
    var promptInFlight: Boolean = false
        private set

    private var startedActivities = 0
    private var backgroundedAt = 0L

    fun onActivityStarted(lockEnabled: Boolean) {
        if (startedActivities == 0) {
            val since = backgroundedAt
            backgroundedAt = 0L
            if (lockEnabled && since > 0L && clock() - since >= graceMs) relock()
        }
        startedActivities++
    }

    fun onActivityStopped(changingConfigurations: Boolean) {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
        if (startedActivities == 0 && !changingConfigurations && !promptInFlight) {
            backgroundedAt = clock()
        }
    }

    /** Lock turned off, or the device has no screen lock: nothing to ask for. */
    fun markUnlockedWithoutPrompt() {
        isUnlocked = true
    }

    /** Returns false when the prompt should not open now (already open, or the user cancelled). */
    fun beginAutoPrompt(): Boolean {
        if (!autoPromptArmed || promptInFlight || isUnlocked) return false
        autoPromptArmed = false
        promptInFlight = true
        return true
    }

    fun beginManualPrompt(): Boolean {
        if (promptInFlight || isUnlocked) return false
        promptInFlight = true
        return true
    }

    /** Result of the system prompt. A cancel leaves the lock screen up without reopening the prompt. */
    fun onPromptResult(success: Boolean) {
        promptInFlight = false
        if (success) isUnlocked = true
    }

    /** The prompt could not be shown (device has no secure lock). */
    fun onPromptUnavailable() {
        promptInFlight = false
        isUnlocked = true
    }

    private fun relock() {
        isUnlocked = false
        autoPromptArmed = true
    }
}

internal val AppLock = AppLockSession()

/** System PIN / pattern / biometric confirmation, or null when the device has no secure lock. */
@Suppress("DEPRECATION")
internal fun Activity.deviceCredentialIntent(): Intent? {
    val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager ?: return null
    if (!keyguard.isDeviceSecure) return null
    return keyguard.createConfirmDeviceCredentialIntent(
        getString(R.string.settings_privacy_app_lock),
        getString(R.string.settings_privacy_app_lock_description),
    )
}

/**
 * Opaque cover shown above the app while locked. It swallows touches and Back (Back leaves Crux
 * instead of navigating the hidden screen underneath).
 */
@Composable
internal fun AppLockCover(
    onUnlock: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .clickable(interactionSource = null, indication = null, onClick = {})
                .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FAIcon(
                FontAwesomeIcons.Solid.Lock,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.settings_privacy_app_lock),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.settings_privacy_app_lock_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onUnlock) {
                Text(stringResource(R.string.app_lock_unlock))
            }
        }
    }
}
