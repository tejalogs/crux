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

// The agent only runs on the device (see AiConfigKoogBridge and localOnly()): a legacy cloud type is
// read as the on-device engine, so only the agent toggle decides whether it is on.
internal fun AppSettings.AiConfig.isAiAgentEnabled(): Boolean = agent
