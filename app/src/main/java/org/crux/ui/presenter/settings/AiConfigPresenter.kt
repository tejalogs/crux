package org.crux.ui.presenter.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.crux.data.datastore.AppDataStore
import org.crux.data.datastore.model.AppSettings
import org.crux.data.network.ai.hasLegacyCloudType
import org.crux.data.network.ai.withLocalAiOnly
import org.crux.di.koinInject
import org.crux.ui.model.UiState
import org.crux.ui.presenter.PresenterBase

public enum class AiTypeOption {
    OnDevice,
    OpenAI,
}

public enum class AiReasoningEffortOption(
    public val value: String,
) {
    Default(""),
    Low("low"),
    Medium("medium"),
    High("high"),
}

public class AiConfigPresenter : PresenterBase<AiConfigPresenter.State>() {
    private val appDataStore by koinInject<AppDataStore>()

    @Immutable
    public interface State {
        public val aiType: AiTypeOption
        public val openAIServerUrl: String
        public val openAIApiKey: String
        public val openAIModel: String
        public val openAIReasoningEffort: AiReasoningEffortOption
        public val openAIExtraBody: String
        public val openAIModels: UiState<ImmutableList<String>>
        public val supportedTypes: ImmutableList<AiTypeOption>
        public val supportedOpenAIReasoningEfforts: ImmutableList<AiReasoningEffortOption>
        public val serverSuggestions: ImmutableList<String>
        public val aiTldr: Boolean
        public val aiAgent: Boolean
        public val tldrPrompt: String
        public val onDeviceModel: String
        public val onDeviceHardwareBackend: String
        public val onDeviceMtpEnabled: Boolean
        public val onDeviceLocalhostEndpoint: String

        public fun selectType(type: AiTypeOption)

        public fun setAIType(value: AiTypeOption)

        public fun setOnDeviceModel(value: String)

        public fun setOnDeviceHardwareBackend(value: String)

        public fun setOnDeviceMtpEnabled(value: Boolean)

        public fun setOnDeviceLocalhostEndpoint(value: String)

        public fun setOpenAIServerUrl(value: String)

        public fun setOpenAIApiKey(value: String)

        public fun setOpenAIModel(value: String)

        public fun setOpenAIReasoningEffort(value: AiReasoningEffortOption)

        public fun setOpenAIExtraBody(value: String)

        public fun setAITldr(value: Boolean)

        public fun setAIAgent(value: Boolean)

        public fun setTldrPrompt(value: String)
    }

    @Composable
    override fun body(): State {
        val scope = rememberCoroutineScope()
        val appSettings by remember { appDataStore.appSettingsStore.data }
            .collectAsState(AppSettings(version = ""))
        var supportedTypes by remember {
            mutableStateOf<ImmutableList<AiTypeOption>>(persistentListOf(AiTypeOption.OnDevice))
        }
        val supportedOpenAIReasoningEfforts =
            remember {
                persistentListOf(
                    AiReasoningEffortOption.Default,
                    AiReasoningEffortOption.Low,
                    AiReasoningEffortOption.Medium,
                    AiReasoningEffortOption.High,
                )
            }

        fun update(block: AppSettings.AiConfig.() -> AppSettings.AiConfig) {
            scope.launch {
                withContext(Dispatchers.Main) {
                    appDataStore.appSettingsStore.updateData { current ->
                        current.copy(
                            aiConfig = block.invoke(current.aiConfig).withLocalAiOnly(),
                        )
                    }
                }
            }
        }

        LaunchedEffect(appSettings.aiConfig.hasLegacyCloudType) {
            if (appSettings.aiConfig.hasLegacyCloudType) {
                update { withLocalAiOnly() }
            }
            supportedTypes = persistentListOf(AiTypeOption.OnDevice)
        }

        return object : State {
            override val openAIModels: UiState<ImmutableList<String>> = UiState.Success(persistentListOf())
            override val supportedTypes: ImmutableList<AiTypeOption> = supportedTypes
            override val supportedOpenAIReasoningEfforts: ImmutableList<AiReasoningEffortOption> =
                supportedOpenAIReasoningEfforts
            override val serverSuggestions: ImmutableList<String> = persistentListOf()
            override val aiType: AiTypeOption = AiTypeOption.OnDevice
            override val onDeviceModel: String =
                (appSettings.aiConfig.type as? AppSettings.AiConfig.Type.OnDevice)?.model
                    ?: AppSettings.AiConfig.Type.OnDevice.DEFAULT_MODEL
            override val onDeviceHardwareBackend: String =
                (appSettings.aiConfig.type as? AppSettings.AiConfig.Type.OnDevice)?.hardwareBackend
                    ?: AppSettings.AiConfig.Type.OnDevice.DEFAULT_HARDWARE_BACKEND
            override val onDeviceMtpEnabled: Boolean =
                (appSettings.aiConfig.type as? AppSettings.AiConfig.Type.OnDevice)?.enableMtp ?: true
            override val onDeviceLocalhostEndpoint: String =
                (appSettings.aiConfig.type as? AppSettings.AiConfig.Type.OnDevice)?.localhostEndpoint
                    ?: AppSettings.AiConfig.Type.OnDevice.DEFAULT_LOCALHOST_ENDPOINT
            override val openAIServerUrl: String = ""
            override val openAIApiKey: String = ""
            override val openAIModel: String = ""
            override val openAIReasoningEffort: AiReasoningEffortOption = AiReasoningEffortOption.Default
            override val openAIExtraBody: String = ""
            override val aiTldr: Boolean = appSettings.aiConfig.tldr
            override val aiAgent: Boolean = appSettings.aiConfig.agent
            override val tldrPrompt: String = appSettings.aiConfig.tldrPrompt

            override fun setAITldr(value: Boolean) = update { copy(tldr = value) }

            override fun setAIAgent(value: Boolean) = update { copy(agent = value) }

            override fun setTldrPrompt(value: String) = update { copy(tldrPrompt = value) }

            override fun setAIType(value: AiTypeOption) =
                update { copy(type = (type as? AppSettings.AiConfig.Type.OnDevice) ?: AppSettings.AiConfig.Type.OnDevice()) }

            override fun setOnDeviceModel(value: String) =
                update {
                    val current = type as? AppSettings.AiConfig.Type.OnDevice ?: AppSettings.AiConfig.Type.OnDevice()
                    copy(type = current.copy(model = value.trim()))
                }

            override fun setOnDeviceHardwareBackend(value: String) =
                update {
                    val current = type as? AppSettings.AiConfig.Type.OnDevice ?: AppSettings.AiConfig.Type.OnDevice()
                    copy(type = current.copy(hardwareBackend = value.trim()))
                }

            override fun setOnDeviceMtpEnabled(value: Boolean) =
                update {
                    val current = type as? AppSettings.AiConfig.Type.OnDevice ?: AppSettings.AiConfig.Type.OnDevice()
                    copy(type = current.copy(enableMtp = value))
                }

            override fun setOnDeviceLocalhostEndpoint(value: String) =
                update {
                    val current = type as? AppSettings.AiConfig.Type.OnDevice ?: AppSettings.AiConfig.Type.OnDevice()
                    copy(type = current.copy(localhostEndpoint = value.trim()))
                }

            override fun setOpenAIServerUrl(value: String) = Unit

            override fun setOpenAIApiKey(value: String) = Unit

            override fun setOpenAIModel(value: String) = Unit

            override fun setOpenAIReasoningEffort(value: AiReasoningEffortOption) = Unit

            override fun setOpenAIExtraBody(value: String) = Unit

            override fun selectType(type: AiTypeOption) = setAIType(type)
        }
    }
}
