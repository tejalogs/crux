package org.crux

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.ComposeRuntimeFlags
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ComposeUiFlags
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.crux.common.AndroidDownloadManager
import org.crux.common.ProvideAndroidMediaSavePicker
import org.crux.data.privacy.PrivacySettingsRepository
import org.crux.ui.AppContainer
import org.crux.ui.component.platform.LocalWifiState
import org.crux.ui.lock.AppLock
import org.crux.ui.lock.AppLockCoverWindow
import org.crux.ui.lock.deviceCredentialIntent
import org.crux.ui.component.status.ProvideAndroidTimelineMediaActions
import org.crux.ui.screen.home.LiveWebPrivacyStore
import org.crux.ui.screen.home.LiveWebPrivateScreens
import org.crux.ui.screen.home.recentsPreviewHidden
import org.crux.ui.screen.home.secureWindowWanted
import org.crux.ui.screen.home.restoreUiModeNightMaskIfNeeded
import org.crux.ui.theme.CruxTheme
import org.crux.ui.theme.LocalSystemDarkTheme
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private var keepSplashOnScreen = true
    private val downloadManager by inject<AndroidDownloadManager>()
    private val privacySettingsRepository by inject<PrivacySettingsRepository>()
    private val wifiStateFlow by lazy {
        observeWifiStateAsFlow()
    }

    private var osNightModeMask: Int = Configuration.UI_MODE_NIGHT_UNDEFINED
    private var isOsDarkTheme by mutableStateOf(false)

    /** Mirrors FLAG_SECURE on the activity window so the App Lock cover window copies it. */
    private var isWindowSecure by mutableStateOf(false)

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

    @OptIn(ExperimentalComposeUiApi::class, ExperimentalComposeApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        osNightModeMask = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        isOsDarkTheme = osNightModeMask == Configuration.UI_MODE_NIGHT_YES
        installSplashScreen().setKeepOnScreenCondition { keepSplashOnScreen }
        enableEdgeToEdge()
        ComposeUiFlags.isMediaQueryIntegrationEnabled = true
        ComposeRuntimeFlags.isLinkBufferComposerEnabled = true
        super.onCreate(savedInstanceState)
        observeSecureWindow()
        observeRecentsPreview()
        setContent {
            val wifiState by wifiStateFlow.collectAsState(false)
            val privacySettings by privacySettingsRepository.settings().collectAsState()
            val isPrivacyLoaded by privacySettingsRepository.isLoaded.collectAsState()
            val requiresLock = privacySettings.requireAppLock
            val locked = isPrivacyLoaded && requiresLock && !AppLock.isUnlocked
            // Content is composed only after the first unlock, then kept under the cover so
            // re-locking never throws away open tabs, scroll positions or a pending file upload.
            var contentStarted by remember { mutableStateOf(false) }
            val showContent = contentStarted || (isPrivacyLoaded && !locked)
            SideEffect { if (showContent) contentStarted = true }

            LaunchedEffect(isPrivacyLoaded, requiresLock) {
                if (isPrivacyLoaded && !requiresLock) AppLock.markUnlockedWithoutPrompt()
            }
            LaunchedEffect(locked, AppLock.autoPromptArmed) {
                if (locked) {
                    keepSplashOnScreen = false
                    if (AppLock.beginAutoPrompt()) launchUnlockPrompt()
                }
            }

            CompositionLocalProvider(
                LocalWifiState provides wifiState,
                LocalSystemDarkTheme provides isOsDarkTheme,
            ) {
                ProvideAndroidMediaSavePicker {
                    ProvideAndroidTimelineMediaActions {
                        if (!isPrivacyLoaded) {
                            // Hold splash screen & neutral surface until persisted privacy/lock policy loads.
                            CruxTheme {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.background),
                                )
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxSize()) {
                                if (showContent) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxSize()
                                                .then(if (locked) Modifier.clearAndSetSemantics {} else Modifier),
                                    ) {
                                        AppContainer(
                                            afterInit = {
                                                keepSplashOnScreen = false
                                            },
                                        )
                                    }
                                }
                                if (locked) {
                                    CruxTheme {
                                        // Opaque in the main window at once, so no frame of the
                                        // hidden screen shows while the cover window appears.
                                        Box(
                                            modifier =
                                                Modifier
                                                    .fillMaxSize()
                                                    .background(MaterialTheme.colorScheme.background),
                                        )
                                        AppLockCoverWindow(
                                            secure = isWindowSecure,
                                            onUnlock = { if (AppLock.beginManualPrompt()) launchUnlockPrompt() },
                                            onBack = { moveTaskToBack(true) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Android 13+ shows a blank card in Recents instead of the last screen while the app lock is
     * on, and from the moment any site page (a feed, DMs, a sign-in page) was first on screen in
     * this process. Recents captures whatever screen is up when the app is left, so there is no later
     * point at which a feed or a DM could be told apart; the RSS-only screens keep their preview
     * until a site page is opened.
     */
    private fun observeRecentsPreview() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        lifecycleScope.launch {
            combine(
                privacySettingsRepository.settings().map { it.requireAppLock },
                LiveWebPrivateScreens.webContentShown,
            ) { requireLock, webShown -> recentsPreviewHidden(requireLock, webShown) }
                .distinctUntilChanged()
                .collect { setRecentsScreenshotEnabled(!it) }
        }
    }

    /**
     * Screenshot and recents protection. FLAG_SECURE follows the Privacy & Tweaks toggle, on
     * Android 12 and below also App Lock (there it is the only way to keep the last screen out of
     * the Recents thumbnail), and turns on by itself while a visible site page (a tab, the
     * sign-in screen, the share composer, the Instagram or LinkedIn screen) shows messages or a
     * sign-in page. Set before first draw.
     */
    private fun observeSecureWindow() {
        val store = LiveWebPrivacyStore.get(applicationContext)
        val legacyRecents = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        applySecureWindow(
            secureWindowWanted(
                userSecureWindow = store.settings.value.secureWindow,
                requireAppLock = privacySettingsRepository.settings().value.requireAppLock,
                privateScreenShown = LiveWebPrivateScreens.isActive,
                legacyRecents = legacyRecents,
            ),
        )
        lifecycleScope.launch {
            combine(
                store.settings.map { it.secureWindow },
                privacySettingsRepository.settings().map { it.requireAppLock },
                LiveWebPrivateScreens.active,
            ) { secureWindow, requireLock, privateScreen ->
                secureWindowWanted(secureWindow, requireLock, privateScreen, legacyRecents)
            }.distinctUntilChanged()
                .collect { applySecureWindow(it) }
        }
    }

    private fun applySecureWindow(secure: Boolean) {
        isWindowSecure = secure
        if (secure) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onNewIntent(intent: Intent) {
        setIntent(intent)
        super.onNewIntent(intent)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val newNightMask = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (newNightMask != Configuration.UI_MODE_NIGHT_UNDEFINED) {
            osNightModeMask = newNightMask
            isOsDarkTheme = newNightMask == Configuration.UI_MODE_NIGHT_YES
        }
    }

    override fun onStart() {
        super.onStart()
        AppLock.onActivityStarted(lockEnabled = privacySettingsRepository.settings().value.requireAppLock)
    }

    override fun onResume() {
        super.onResume()
        restoreUiModeNightMaskIfNeeded(this, osNightModeMask)
        downloadManager.onResume()
    }

    override fun onStop() {
        super.onStop()
        AppLock.onActivityStopped(changingConfigurations = isChangingConfigurations)
    }

    override fun onPause() {
        super.onPause()
        downloadManager.onPause()
    }
}

private fun Context.observeWifiStateAsFlow() =
    callbackFlow {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkRequest =
            NetworkRequest
                .Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
        val networkCallback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities,
                ) {
                    trySend(networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
                }
            }
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        val currentNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(currentNetwork)
        trySend(caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true)
        awaitClose {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }
