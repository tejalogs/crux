package org.crux.ui.lock

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.WindowManager
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.SecureFlagPolicy
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
    private var confirmStartedAt = 0L

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

    /**
     * A credential check while already unlocked (for example before turning App Lock off). Like
     * the unlock prompt, its screen must not count as leaving Crux, or a slow PIN would re-lock.
     */
    fun beginConfirmPrompt(): Boolean {
        if (promptInFlight) return false
        promptInFlight = true
        confirmStartedAt = clock()
        return true
    }

    /**
     * End of [beginConfirmPrompt]. A success keeps the app unlocked however long it took, since the
     * owner just proved who they are. A cancel or failure after the grace period re-locks: the
     * prompt may have been left open while Crux sat in the background, and whoever cancels it now
     * must not get an unlocked app.
     */
    fun onConfirmPromptFinished(success: Boolean) {
        val startedAt = confirmStartedAt
        promptInFlight = false
        confirmStartedAt = 0L
        if (!success && startedAt > 0L && clock() - startedAt >= graceMs) relock()
    }

    private fun relock() {
        isUnlocked = false
        autoPromptArmed = true
    }
}

internal val AppLock = AppLockSession()

/**
 * Holds the latest request (a deep link, share or shortcut) that arrived while the app was locked,
 * so it cannot open a screen or sheet under or over the lock cover. Only the newest one is kept.
 */
internal class DeferredWhileLocked<T : Any> {
    private var pending: T? = null

    /** Returns [value] when it can run now; otherwise keeps it for [release] and returns null. */
    fun offer(
        value: T,
        unlocked: Boolean,
    ): T? {
        if (unlocked) {
            pending = null
            return value
        }
        pending = value
        return null
    }

    /** After unlock, hands back the kept request once; null while still locked or nothing kept. */
    fun release(unlocked: Boolean): T? {
        if (!unlocked) return null
        return pending.also { pending = null }
    }
}

/** What flipping the App Lock switch should do. */
internal enum class AppLockToggleAction {
    /** Save the new value straight away. */
    Save,

    /** Turning off: the owner must pass the device credential check first. */
    ConfirmFirst,

    /** Turning on with no screen lock on the device: it would protect nothing, so stay off. */
    NeedsScreenLock,
}

internal fun appLockToggleAction(
    enable: Boolean,
    deviceSecure: Boolean,
): AppLockToggleAction =
    when {
        enable && !deviceSecure -> AppLockToggleAction.NeedsScreenLock
        enable -> AppLockToggleAction.Save
        // With no screen lock left there is nothing to check (the lock already lets anyone in).
        deviceSecure -> AppLockToggleAction.ConfirmFirst
        else -> AppLockToggleAction.Save
    }

/** Turning App Lock on is only meaningful when the device has a PIN, pattern or password. */
internal fun Context.isDeviceSecure(): Boolean =
    (getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isDeviceSecure == true

/** System PIN / pattern / biometric confirmation, or null when the device has no secure lock. */
@Suppress("DEPRECATION")
internal fun Context.deviceCredentialIntent(): Intent? {
    val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager ?: return null
    if (!keyguard.isDeviceSecure) return null
    return keyguard.createConfirmDeviceCredentialIntent(
        getString(R.string.settings_privacy_app_lock),
        getString(R.string.settings_privacy_app_lock_description),
    )
}

/**
 * [AppLockCover] in its own full-screen window, added after (so stacked above) any dialog, bottom
 * sheet or popup already open in the activity. A cover drawn in the main window alone would leave
 * those windows on top and usable. [secure] copies the activity's FLAG_SECURE to this window.
 */
@Composable
internal fun AppLockCoverWindow(
    secure: Boolean,
    onUnlock: () -> Unit,
    onBack: () -> Unit,
) {
    Dialog(
        onDismissRequest = {},
        properties =
            DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                securePolicy = if (secure) SecureFlagPolicy.SecureOn else SecureFlagPolicy.SecureOff,
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            // Appear at once (no fade that would show the screen underneath) and with no dim.
            window?.setWindowAnimations(0)
            window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        AppLockCover(onUnlock = onUnlock, onBack = onBack)
    }
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
