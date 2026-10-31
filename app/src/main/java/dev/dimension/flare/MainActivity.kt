package dev.dimension.flare

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.ComposeRuntimeFlags
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ComposeUiFlags
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Lock
import dev.dimension.flare.common.AndroidDownloadManager
import dev.dimension.flare.common.ProvideAndroidMediaSavePicker
import dev.dimension.flare.data.privacy.PrivacySettingsRepository
import dev.dimension.flare.ui.AppContainer
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.platform.LocalWifiState
import dev.dimension.flare.ui.component.status.ProvideAndroidTimelineMediaActions
import dev.dimension.flare.ui.theme.FlareTheme
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private var keepSplashOnScreen = true
    private val downloadManager by inject<AndroidDownloadManager>()
    private val privacySettingsRepository by inject<PrivacySettingsRepository>()
    private val wifiStateFlow by lazy {
        observeWifiStateAsFlow()
    }

    private var isAppUnlocked by mutableStateOf(false)

    private val lockLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                isAppUnlocked = true
            }
        }

    fun promptDeviceUnlock() {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguardManager != null && keyguardManager.isDeviceSecure) {
            @Suppress("DEPRECATION")
            val intent =
                keyguardManager.createConfirmDeviceCredentialIntent(
                    getString(R.string.settings_privacy_app_lock),
                    getString(R.string.settings_privacy_app_lock_description),
                )
            if (intent != null) {
                lockLauncher.launch(intent)
                return
            }
        }
        isAppUnlocked = true
    }

    @OptIn(ExperimentalComposeUiApi::class, ExperimentalComposeApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { keepSplashOnScreen }
        enableEdgeToEdge()
        ComposeUiFlags.isMediaQueryIntegrationEnabled = true
        ComposeRuntimeFlags.isLinkBufferComposerEnabled = true
        super.onCreate(savedInstanceState)
        setContent {
            val wifiState by wifiStateFlow.collectAsState(false)
            val privacySettings by privacySettingsRepository.settings().collectAsState()
            val requiresLock = privacySettings.requireAppLock

            LaunchedEffect(requiresLock) {
                if (requiresLock && !isAppUnlocked) {
                    keepSplashOnScreen = false
                    promptDeviceUnlock()
                }
            }

            CompositionLocalProvider(
                LocalWifiState provides wifiState,
            ) {
                ProvideAndroidMediaSavePicker {
                    ProvideAndroidTimelineMediaActions {
                        if (requiresLock && !isAppUnlocked) {
                            FlareTheme {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.background)
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
                                        )
                                        Text(
                                            text = stringResource(R.string.settings_privacy_app_lock_description),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Button(
                                            onClick = { promptDeviceUnlock() },
                                        ) {
                                            Text(stringResource(android.R.string.ok))
                                        }
                                    }
                                }
                            }
                        } else {
                            AppContainer(
                                afterInit = {
                                    keepSplashOnScreen = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        setIntent(intent)
        super.onNewIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        downloadManager.onResume()
        val privacySettings = privacySettingsRepository.settings().value
        if (privacySettings.requireAppLock && !isAppUnlocked) {
            promptDeviceUnlock()
        }
    }

    override fun onStop() {
        super.onStop()
        val privacySettings = privacySettingsRepository.settings().value
        if (privacySettings.requireAppLock) {
            isAppUnlocked = false
        }
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
