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
import androidx.compose.runtime.snapshotFlow
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.crux.common.OnDeviceAI
import org.crux.data.datastore.AppDataStore
import org.crux.data.datastore.model.AppSettings
import org.crux.data.network.ai.OpenAIService
import org.crux.di.koinInject
import org.crux.ui.model.UiState
import org.crux.ui.presenter.PresenterBase
import org.crux.web.shared.WebPresenter

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

@WebPresenter("aiConfig")
public class AiConfigPresenter : PresenterBase<AiConfigPresenter.State>() {
    private val appDataStore by koinInject<AppDataStore>()
    private val openAIService by koinInject<OpenAIService>()
    private val onDeviceAI by koinInject<OnDeviceAI>()

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

    @OptIn(FlowPreview::class)
    @Composable
    override fun body(): State {
        val scope = rememberCoroutineScope()
        val appSettings by remember { appDataStore.appSettingsStore.data }
            .collectAsState(AppSettings(version = ""))
        var openAIModels by remember {
            mutableStateOf<UiState<ImmutableList<String>>>(UiState.Success(persistentListOf()))
        }
        var supportedTypes by remember {
            mutableStateOf<ImmutableList<AiTypeOption>>(persistentListOf(AiTypeOption.OpenAI))
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
                            aiConfig =
                                block
                                    .invoke(current.aiConfig)
                                    .normalized(),
                        )
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            if (appSettings.aiConfig.type !is AppSettings.AiConfig.Type.OnDevice) {
                update { copy(type = AppSettings.AiConfig.Type.OnDevice()) }
            }
            supportedTypes = persistentListOf(AiTypeOption.OnDevice)
        }

        return object : State {
            override val openAIModels: UiState<ImmutableList<String>> = openAIModels
            override val supportedTypes: ImmutableList<AiTypeOption> = supportedTypes
            override val supportedOpenAIReasoningEfforts: ImmutableList<AiReasoningEffortOption> =
                supportedOpenAIReasoningEfforts
            override val serverSuggestions: ImmutableList<String> = SERVER_SUGGESTIONS
            override val aiType: AiTypeOption =
                when (appSettings.aiConfig.type) {
                    is AppSettings.AiConfig.Type.OnDevice -> AiTypeOption.OnDevice
                    is AppSettings.AiConfig.Type.OpenAI -> AiTypeOption.OnDevice
                }
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

            override val openAIServerUrl: String =
                (appSettings.aiConfig.type as? AppSettings.AiConfig.Type.OpenAI)?.serverUrl ?: ""

            override val openAIApiKey: String =
                (appSettings.aiConfig.type as? AppSettings.AiConfig.Type.OpenAI)?.apiKey ?: ""

            override val openAIModel: String =
                (appSettings.aiConfig.type as? AppSettings.AiConfig.Type.OpenAI)?.model ?: ""

            override val openAIReasoningEffort: AiReasoningEffortOption =
                AiReasoningEffortOption.entries.firstOrNull {
                    it.value == (appSettings.aiConfig.type as? AppSettings.AiConfig.Type.OpenAI)?.reasoningEffort.orEmpty()
                } ?: AiReasoningEffortOption.Default

            override val openAIExtraBody: String =
                (appSettings.aiConfig.type as? AppSettings.AiConfig.Type.OpenAI)?.extraBody ?: ""

            override val aiTldr: Boolean = appSettings.aiConfig.tldr
            override val aiAgent: Boolean = appSettings.aiConfig.agent
            override val tldrPrompt: String = appSettings.aiConfig.tldrPrompt

            override fun setAITldr(value: Boolean) {
                update {
                    copy(
                        tldr = value,
                    )
                }
            }

            override fun setAIAgent(value: Boolean) {
                update {
                    copy(
                        agent = value,
                    )
                }
            }

            override fun setTldrPrompt(value: String) {
                update {
                    copy(
                        tldrPrompt = value,
                    )
                }
            }

            override fun setAIType(value: AiTypeOption) {
                update {
                    copy(
                        type = (type as? AppSettings.AiConfig.Type.OnDevice) ?: AppSettings.AiConfig.Type.OnDevice(),
                    )
                }
            }

            override fun setOnDeviceModel(value: String) {
                update {
                    val current = type as? AppSettings.AiConfig.Type.OnDevice ?: AppSettings.AiConfig.Type.OnDevice()
                    copy(type = current.copy(model = value.trim()))
                }
            }

            override fun setOnDeviceHardwareBackend(value: String) {
                update {
                    val current = type as? AppSettings.AiConfig.Type.OnDevice ?: AppSettings.AiConfig.Type.OnDevice()
                    copy(type = current.copy(hardwareBackend = value.trim()))
                }
            }

            override fun setOnDeviceMtpEnabled(value: Boolean) {
                update {
                    val current = type as? AppSettings.AiConfig.Type.OnDevice ?: AppSettings.AiConfig.Type.OnDevice()
                    copy(type = current.copy(enableMtp = value))
                }
            }

            override fun setOnDeviceLocalhostEndpoint(value: String) {
                update {
                    val current = type as? AppSettings.AiConfig.Type.OnDevice ?: AppSettings.AiConfig.Type.OnDevice()
                    copy(type = current.copy(localhostEndpoint = value.trim()))
                }
            }

            override fun setOpenAIServerUrl(value: String) {
                update {
                    copy(
                        type =
                            AppSettings.AiConfig.Type.OpenAI(
                                serverUrl = value,
                                apiKey = openAIApiKey,
                                model = openAIModel,
                                reasoningEffort = openAIReasoningEffort.value,
                                extraBody = openAIExtraBody,
                            ),
                    )
                }
            }

            override fun setOpenAIApiKey(value: String) {
                update {
                    copy(
                        type =
                            AppSettings.AiConfig.Type.OpenAI(
                                serverUrl = openAIServerUrl,
                                apiKey = value,
                                model = openAIModel,
                                reasoningEffort = openAIReasoningEffort.value,
                                extraBody = openAIExtraBody,
                            ),
                    )
                }
            }

            override fun setOpenAIModel(value: String) {
                update {
                    copy(
                        type =
                            AppSettings.AiConfig.Type.OpenAI(
                                serverUrl = openAIServerUrl,
                                apiKey = openAIApiKey,
                                model = value,
                                reasoningEffort = openAIReasoningEffort.value,
                                extraBody = openAIExtraBody,
                            ),
                    )
                }
            }

            override fun setOpenAIReasoningEffort(value: AiReasoningEffortOption) {
                update {
                    copy(
                        type =
                            AppSettings.AiConfig.Type.OpenAI(
                                serverUrl = openAIServerUrl,
                                apiKey = openAIApiKey,
                                model = openAIModel,
                                reasoningEffort = value.value,
                                extraBody = openAIExtraBody,
                            ),
                    )
                }
            }

            override fun setOpenAIExtraBody(value: String) {
                update {
                    copy(
                        type =
                            AppSettings.AiConfig.Type.OpenAI(
                                serverUrl = openAIServerUrl,
                                apiKey = openAIApiKey,
                                model = openAIModel,
                                reasoningEffort = openAIReasoningEffort.value,
                                extraBody = value,
                            ),
                    )
                }
            }


            override fun selectType(type: AiTypeOption) {
                update {
                    copy(type = (this.type as? AppSettings.AiConfig.Type.OnDevice) ?: AppSettings.AiConfig.Type.OnDevice())
                }
            }

        }
    }
}

private fun AppSettings.AiConfig.normalized(): AppSettings.AiConfig = this

private val SERVER_SUGGESTIONS =
    persistentListOf(
        "https://api.openai.com/v1/",
        "https://generativelanguage.googleapis.com/v1beta/openai/",
        "https://openrouter.ai/api/v1/",
        "https://api.x.ai/v1/",
        "https://dashscope.aliyuncs.com/compatible-mode/v1/",
        "https://open.bigmodel.cn/api/paas/v4/",
        "https://api.moonshot.cn/v1/",
        "https://api.siliconflow.cn/v1/",
        "https://api.minimaxi.com/v1/",
        "https://api.groq.com/openai/v1/",
        "https://api.together.xyz/v1/",
        "https://api.deepseek.com/v1/",
        "https://api.fireworks.ai/inference/v1/",
    )
