package org.crux.ui.screen.rss

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Lock
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.crux.R
import org.crux.data.privacy.PrivacySettingsRepository
import org.crux.ui.CruxApp
import org.crux.ui.component.FAIcon
import org.crux.ui.lock.AppLock
import org.crux.ui.lock.AppLockCover
import org.crux.ui.lock.deviceCredentialIntent
import org.crux.ui.screen.home.LiveWebPrivacyStore
import org.crux.ui.theme.CruxTheme
import org.koin.android.ext.android.inject

class AddRssSourceActivity : ComponentActivity() {
    private val privacySettingsRepository by inject<PrivacySettingsRepository>()
    private val lockLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            AppLock.onPromptResult(success = result.resultCode == Activity.RESULT_OK)
        }

    private fun launchUnlockPrompt() {
        val intent = deviceCredentialIntent()
        if (intent == null) {
            AppLock.onPromptUnavailable()
            return
        }
        runCatching { lockLauncher.launch(intent) }.onFailure { AppLock.onPromptResult(success = false) }
    }

    private fun observeSecureWindow() {
        val store = LiveWebPrivacyStore.get(applicationContext)
        applySecureWindow(store.settings.value.secureWindow)
        lifecycleScope.launch {
            store.settings
                .map { it.secureWindow }
                .distinctUntilChanged()
                .collect { applySecureWindow(it) }
        }
    }

    private fun applySecureWindow(secure: Boolean) {
        if (secure) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        observeSecureWindow()

        @Suppress("DEPRECATION")
        val rawStreamUri: Uri? =
            intent?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                ?: intent
                    ?.clipData
                    ?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)
                    ?.uri
                ?: intent?.data

        // Never open arbitrary file:// URIs supplied by external apps or browsers.
        val streamUri = rawStreamUri?.takeIf { it.scheme.equals("content", ignoreCase = true) }

        val mimeType = intent?.type.orEmpty()
        val isOpml =
            mimeType.contains("opml", ignoreCase = true) ||
                mimeType.contains("xml", ignoreCase = true) ||
                streamUri?.path?.endsWith(".opml", ignoreCase = true) == true ||
                streamUri?.toString()?.endsWith(".opml", ignoreCase = true) == true

        val initialOpmlUri = if (isOpml && streamUri != null) streamUri.toString() else null

        val initialText =
            when {
                initialOpmlUri != null -> {
                    null
                }

                intent?.action == Intent.ACTION_SEND -> {
                    intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                }

                else -> {
                    val raw = intent?.dataString.orEmpty()
                    when {
                        raw.startsWith("file:", ignoreCase = true) -> ""
                        raw.startsWith("feed://", ignoreCase = true) -> "https://${raw.removePrefix("feed://")}"
                        raw.startsWith("feed:https://", ignoreCase = true) -> raw.removePrefix("feed:")
                        raw.startsWith("feed:http://", ignoreCase = true) -> raw.removePrefix("feed:")
                        raw.startsWith("rss://", ignoreCase = true) -> "https://${raw.removePrefix("rss://")}"
                        raw.startsWith("rss:https://", ignoreCase = true) -> raw.removePrefix("rss:")
                        raw.startsWith("rss:http://", ignoreCase = true) -> raw.removePrefix("rss:")
                        else -> raw
                    }
                }
            }

        setContent {
            val privacySettings by privacySettingsRepository.settings().collectAsState()
            val isPrivacyLoaded by privacySettingsRepository.isLoaded.collectAsState()
            val requiresLock = privacySettings.requireAppLock
            val locked = isPrivacyLoaded && requiresLock && !AppLock.isUnlocked

            LaunchedEffect(isPrivacyLoaded, requiresLock) {
                if (isPrivacyLoaded && !requiresLock) AppLock.markUnlockedWithoutPrompt()
            }
            LaunchedEffect(locked, AppLock.autoPromptArmed) {
                if (locked && AppLock.beginAutoPrompt()) launchUnlockPrompt()
            }

            if (!isPrivacyLoaded) {
                CruxTheme {
                    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                }
            } else if (locked) {
                CruxTheme {
                    AppLockCover(
                        onUnlock = { if (AppLock.beginManualPrompt()) launchUnlockPrompt() },
                        onBack = { finish() },
                    )
                }
            } else {
                CruxApp {
                    var currentOpmlUri by remember { mutableStateOf(initialOpmlUri) }

                    if (currentOpmlUri != null) {
                        ModalBottomSheet(
                            onDismissRequest = {
                                finish()
                            },
                        ) {
                            OPMLImportSheet(
                                uri = currentOpmlUri!!.toUri(),
                                onBack = {
                                    finish()
                                },
                            )
                        }
                    } else {
                        ModalBottomSheet(
                            onDismissRequest = {
                                if (currentOpmlUri == null) {
                                    finish()
                                }
                            },
                        ) {
                            RssSourceEditSheet(
                                onDismissRequest = {
                                    if (currentOpmlUri == null) {
                                        finish()
                                    }
                                },
                                id = null,
                                initialUrl = initialText,
                                onImportOPML = { opmlUri ->
                                    currentOpmlUri = opmlUri
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppLock.onActivityStarted(lockEnabled = privacySettingsRepository.settings().value.requireAppLock)
    }

    override fun onStop() {
        super.onStop()
        AppLock.onActivityStopped(changingConfigurations = isChangingConfigurations)
    }
}
