package org.crux.ui.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.crux.data.repository.SettingsRepository
import org.crux.di.koinInject
import org.crux.ui.model.UiState
import org.crux.ui.model.collectAsUiState
import org.crux.ui.model.map

public class ChangeLogPresenter(
    private val currentVersion: String,
) : PresenterBase<ChangeLogPresenter.State>() {
    private val repository: SettingsRepository by koinInject()

    @Composable
    override fun body(): State {
        val scope = rememberCoroutineScope()
        val appSettings by repository.appSettings.collectAsUiState()
        val shouldShowChangeLog =
            remember(appSettings, currentVersion) {
                appSettings.map {
                    currentVersion.isNotBlank() && it.version != currentVersion
                }
            }

        return object : State {
            override val shouldShowChangeLog: UiState<Boolean> = shouldShowChangeLog

            override fun dismissChangeLog() {
                if (currentVersion.isBlank()) return
                scope.launch {
                    repository.updateAppSettings {
                        copy(version = currentVersion)
                    }
                }
            }
        }
    }

    public interface State {
        public val shouldShowChangeLog: UiState<Boolean>

        public fun dismissChangeLog()
    }
}
