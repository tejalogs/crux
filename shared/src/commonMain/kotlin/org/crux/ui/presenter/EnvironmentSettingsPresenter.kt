package org.crux.ui.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import org.crux.data.datastore.model.AppSettings
import org.crux.data.model.appearance.GlobalAppearance
import org.crux.data.model.appearance.TimelineAppearance
import org.crux.data.repository.SettingsRepository
import org.crux.di.koinInject
import org.crux.ui.model.UiState
import org.crux.ui.model.collectAsUiState
import org.crux.web.shared.WebPresenter

@WebPresenter("environmentSettings")
public class EnvironmentSettingsPresenter : PresenterBase<EnvironmentSettingsPresenter.State>() {
    private val repository: SettingsRepository by koinInject()

    @Composable
    override fun body(): State {
        val globalAppearance by repository.globalAppearance.collectAsUiState()
        val timelineAppearance by repository.timelineAppearance.collectAsUiState()
        val appSettings by repository.appSettings.collectAsUiState()
        return object : State {
            override val globalAppearance: UiState<GlobalAppearance> = globalAppearance
            override val timelineAppearance: UiState<TimelineAppearance> = timelineAppearance
            override val appSettings: UiState<AppSettings> = appSettings
        }
    }

    public interface State {
        public val globalAppearance: UiState<GlobalAppearance>
        public val timelineAppearance: UiState<TimelineAppearance>
        public val appSettings: UiState<AppSettings>
    }
}
