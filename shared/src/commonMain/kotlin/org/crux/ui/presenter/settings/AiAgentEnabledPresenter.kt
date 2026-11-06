package org.crux.ui.presenter.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.crux.data.datastore.AppDataStore
import org.crux.data.datastore.model.AppSettings
import org.crux.di.koinInject
import org.crux.ui.presenter.PresenterBase

public class AiAgentEnabledPresenter : PresenterBase<AiAgentEnabledPresenter.State>() {
    private val appDataStore: AppDataStore by koinInject()

    public interface State {
        public val enabled: Boolean
    }

    @Composable
    override fun body(): State {
        val appSettings by appDataStore.appSettingsStore.data.collectAsState(AppSettings(version = ""))
        return StateImpl(
            enabled = appSettings.aiConfig.isAiAgentEnabled(),
        )
    }

    private data class StateImpl(
        override val enabled: Boolean,
    ) : State
}

internal fun AppSettings.AiConfig.isAiAgentEnabled(): Boolean =
    agent && (type is AppSettings.AiConfig.Type.OnDevice || (type as? AppSettings.AiConfig.Type.OpenAI)?.model?.isNotBlank() == true)
