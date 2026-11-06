package org.crux.ui.presenter.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.crux.data.repository.AccountRepository
import org.crux.data.repository.MxgaRepository
import org.crux.data.repository.SettingsRepository
import org.crux.di.koinInject
import org.crux.model.PlatformCapability
import org.crux.model.PlatformRegistry
import org.crux.ui.model.collectAsUiState
import org.crux.ui.model.takeSuccess
import org.crux.ui.presenter.PresenterBase

public class MxgaSettingsPresenter : PresenterBase<MxgaSettingsState>() {
    private val accountRepository: AccountRepository by koinInject()
    private val settingsRepository: SettingsRepository by koinInject()
    private val mxgaRepository: MxgaRepository by koinInject()
    private val platformRegistry: PlatformRegistry by koinInject()

    @Composable
    override fun body(): MxgaSettingsState {
        val scope = rememberCoroutineScope()
        val hasXQtAccount by
            accountRepository.allAccounts
                .map { accounts ->
                    accounts.any {
                        platformRegistry.supports(it.platformId, PlatformCapability.MxgaFiltering)
                    }
                }.collectAsState(false)
        val appSettings by settingsRepository.appSettings.collectAsUiState()
        val isRefreshing by mxgaRepository.isRefreshing.collectAsState()
        val snapshot by mxgaRepository.snapshot.collectAsState()
        return object : MxgaSettingsState {
            override val hasXQtAccount: Boolean = hasXQtAccount
            override val isEnabled: Boolean = appSettings.takeSuccess()?.mxgaEnabled == true
            override val isRefreshing: Boolean = isRefreshing
            override val lastCheckedAt: Long = snapshot.checkedAt

            override fun setEnabled(value: Boolean) {
                scope.launch {
                    settingsRepository.updateAppSettings { copy(mxgaEnabled = value) }
                }
            }

            override fun refresh() {
                mxgaRepository.refresh()
            }
        }
    }
}

@Immutable
public interface MxgaSettingsState {
    public val hasXQtAccount: Boolean
    public val isEnabled: Boolean
    public val isRefreshing: Boolean
    public val lastCheckedAt: Long

    public fun setEnabled(value: Boolean)

    public fun refresh()
}
