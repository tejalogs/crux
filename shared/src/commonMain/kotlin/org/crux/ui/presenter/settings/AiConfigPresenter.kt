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
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.crux.common.OnDeviceAI
import org.crux.data.datastore.AppDataStore
import org.crux.data.datastore.model.AppSettings
import org.crux.data.network.ai.OpenAIService
import org.crux.data.translation.canonicalTranslationLanguage
import org.crux.di.koinInject
import org.crux.ui.model.UiState
import org.crux.ui.presenter.PresenterBase
import org.crux.web.shared.WebIgnore
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

public enum class TranslateProviderOption {
    AI,
    GoogleWeb,
    DeepL,
    GoogleCloud,
    LibreTranslate,
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
        public val translateProvider: TranslateProviderOption
        public val deepLApiKey: String
        public val deepLUsePro: Boolean
        public val googleCloudApiKey: String
        public val libreTranslateBaseUrl: String
        public val libreTranslateApiKey: String
        public val openAIModels: UiState<ImmutableList<String>>
        public val supportedTypes: ImmutableList<AiTypeOption>
        public val supportedOpenAIReasoningEfforts: ImmutableList<AiReasoningEffortOption>
        public val supportedTranslateProviders: ImmutableList<TranslateProviderOption>
        public val serverSuggestions: ImmutableList<String>
        public val aiTldr: Boolean
        public val aiAgent: Boolean
        public val translatePrompt: String
        public val tldrPrompt: String
        public val preTranslate: Boolean
        public val preferPlatformTranslation: Boolean
        public val showOriginalWithTranslation: Boolean
        public val autoTranslateExcludedLanguages: ImmutableList<String>
        public val onDeviceModel: String
        public val onDeviceHardwareBackend: String
        public val onDeviceMtpEnabled: Boolean
        public val onDeviceLocalhostEndpoint: String

        public fun selectType(type: AiTypeOption)

        public fun selectTranslateProvider(type: TranslateProviderOption)

        public fun setAIType(value: AiTypeOption)

        public fun setOnDeviceModel(value: String)

        public fun setOnDeviceHardwareBackend(value: String)

        public fun setOnDeviceMtpEnabled(value: Boolean)

        public fun setOnDeviceLocalhostEndpoint(value: String)

        public fun setTranslateProvider(value: TranslateProviderOption)

        public fun setOpenAIServerUrl(value: String)

        public fun setOpenAIApiKey(value: String)

        public fun setOpenAIModel(value: String)

        public fun setOpenAIReasoningEffort(value: AiReasoningEffortOption)

        public fun setOpenAIExtraBody(value: String)

        public fun setDeepLApiKey(value: String)

        public fun setDeepLUsePro(value: Boolean)

        public fun setGoogleCloudApiKey(value: String)

        public fun setLibreTranslateBaseUrl(value: String)

        public fun setLibreTranslateApiKey(value: String)

        public fun setAITldr(value: Boolean)

        public fun setAIAgent(value: Boolean)

        public fun setTranslatePrompt(value: String)

        public fun setTldrPrompt(value: String)

        public fun setPreTranslate(value: Boolean)

        public fun setPreferPlatformTranslation(value: Boolean)

        public fun setShowOriginalWithTranslation(value: Boolean)

        @WebIgnore
        public fun setAutoTranslateExcludedLanguages(value: List<String>)

        public fun setAutoTranslateExcludedLanguagesText(value: String)
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
        val supportedTranslateProviders =
            remember {
                persistentListOf(
                    TranslateProviderOption.AI,
                    TranslateProviderOption.GoogleWeb,
                    TranslateProviderOption.DeepL,
                    TranslateProviderOption.GoogleCloud,
                    TranslateProviderOption.LibreTranslate,
                )
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

        LaunchedEffect(Unit) {
            supportedTypes =
                buildList {
                    if (onDeviceAI.isAvailable()) {
                        add(AiTypeOption.OnDevice)
                    }
                    add(AiTypeOption.OpenAI)
                }.toImmutableList()
        }

        LaunchedEffect(Unit) {
            snapshotFlow { appSettings.aiConfig.type as? AppSettings.AiConfig.Type.OpenAI }
                .map { (it?.serverUrl ?: "") to (it?.apiKey ?: "") }
                .distinctUntilChanged()
                .debounce(666L)
                .collectLatest { (serverUrl, apiKey) ->
                    if (serverUrl.isBlank()) {
                        openAIModels = UiState.Success(persistentListOf())
                    } else {
                        openAIModels = UiState.Loading()
                        openAIModels =
                            runCatching {
                                UiState.Success(
                                    openAIService
                                        .models(
                                            serverUrl = serverUrl,
                                            apiKey = apiKey,
                                        ).toImmutableList(),
                                )
                            }.getOrElse {
                                UiState.Error(it)
                            }
                    }
                }
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

        fun updateTranslateConfig(block: AppSettings.TranslateConfig.() -> AppSettings.TranslateConfig) {
            scope.launch {
                withContext(Dispatchers.Main) {
                    appDataStore.appSettingsStore.updateData { current ->
                        current.copy(
                            translateConfig =
                                block
                                    .invoke(current.translateConfig)
                                    .normalized(),
                        )
                    }
                }
            }
        }

        return object : State {
            override val openAIModels: UiState<ImmutableList<String>> = openAIModels
            override val supportedTypes: ImmutableList<AiTypeOption> = supportedTypes
            override val supportedOpenAIReasoningEfforts: ImmutableList<AiReasoningEffortOption> =
                supportedOpenAIReasoningEfforts
            override val supportedTranslateProviders: ImmutableList<TranslateProviderOption> =
                supportedTranslateProviders
            override val serverSuggestions: ImmutableList<String> = SERVER_SUGGESTIONS
            override val aiType: AiTypeOption =
                when (appSettings.aiConfig.type) {
                    is AppSettings.AiConfig.Type.OnDevice -> AiTypeOption.OnDevice
                    is AppSettings.AiConfig.Type.OpenAI -> AiTypeOption.OpenAI
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
            override val translateProvider: TranslateProviderOption =
                when (appSettings.translateConfig.provider) {
                    AppSettings.TranslateConfig.Provider.AI -> TranslateProviderOption.AI
                    AppSettings.TranslateConfig.Provider.GoogleWeb -> TranslateProviderOption.GoogleWeb
                    is AppSettings.TranslateConfig.Provider.DeepL -> TranslateProviderOption.DeepL
                    is AppSettings.TranslateConfig.Provider.GoogleCloud -> TranslateProviderOption.GoogleCloud
                    is AppSettings.TranslateConfig.Provider.LibreTranslate -> TranslateProviderOption.LibreTranslate
                }
            override val deepLApiKey: String =
                (appSettings.translateConfig.provider as? AppSettings.TranslateConfig.Provider.DeepL)?.apiKey ?: ""
            override val deepLUsePro: Boolean =
                (appSettings.translateConfig.provider as? AppSettings.TranslateConfig.Provider.DeepL)?.usePro ?: false
            override val googleCloudApiKey: String =
                (appSettings.translateConfig.provider as? AppSettings.TranslateConfig.Provider.GoogleCloud)?.apiKey ?: ""
            override val libreTranslateBaseUrl: String =
                (appSettings.translateConfig.provider as? AppSettings.TranslateConfig.Provider.LibreTranslate)?.baseUrl ?: ""
            override val libreTranslateApiKey: String =
                (appSettings.translateConfig.provider as? AppSettings.TranslateConfig.Provider.LibreTranslate)?.apiKey ?: ""

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
            override val translatePrompt: String = appSettings.aiConfig.translatePrompt
            override val tldrPrompt: String = appSettings.aiConfig.tldrPrompt
            override val preTranslate: Boolean = appSettings.translateConfig.preTranslate
            override val preferPlatformTranslation: Boolean = appSettings.translateConfig.preferPlatformTranslation
            override val showOriginalWithTranslation: Boolean = appSettings.translateConfig.showOriginalWithTranslation
            override val autoTranslateExcludedLanguages: ImmutableList<String> =
                appSettings.translateConfig.autoTranslateExcludedLanguages.toImmutableList()

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

            override fun setTranslatePrompt(value: String) {
                update {
                    copy(
                        translatePrompt = value,
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

            override fun setPreTranslate(value: Boolean) {
                updateTranslateConfig {
                    copy(
                        preTranslate = value,
                    )
                }
            }

            override fun setPreferPlatformTranslation(value: Boolean) {
                updateTranslateConfig {
                    copy(
                        preferPlatformTranslation = value,
                    )
                }
            }

            override fun setShowOriginalWithTranslation(value: Boolean) {
                updateTranslateConfig {
                    copy(
                        showOriginalWithTranslation = value,
                    )
                }
            }

            override fun setAutoTranslateExcludedLanguages(value: List<String>) {
                updateTranslateConfig {
                    copy(
                        autoTranslateExcludedLanguages = value,
                    )
                }
            }

            override fun setAutoTranslateExcludedLanguagesText(value: String) {
                updateTranslateConfig {
                    copy(
                        autoTranslateExcludedLanguages = normalizeExcludedLanguages(listOf(value)),
                    )
                }
            }

            override fun setAIType(value: AiTypeOption) {
                update {
                    copy(
                        type =
                            when (value) {
                                AiTypeOption.OnDevice -> {
                                    AppSettings.AiConfig.Type.OnDevice()
                                }

                                AiTypeOption.OpenAI -> {
                                    AppSettings.AiConfig.Type.OpenAI(
                                        serverUrl = "",
                                        apiKey = "",
                                        model = "",
                                        reasoningEffort = "",
                                        extraBody = "",
                                    )
                                }
                            },
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

            override fun setDeepLApiKey(value: String) {
                updateTranslateConfig {
                    copy(
                        provider =
                            (provider as? AppSettings.TranslateConfig.Provider.DeepL)?.copy(apiKey = value)
                                ?: AppSettings.TranslateConfig.Provider.DeepL(apiKey = value),
                    )
                }
            }

            override fun setDeepLUsePro(value: Boolean) {
                updateTranslateConfig {
                    copy(
                        provider =
                            (provider as? AppSettings.TranslateConfig.Provider.DeepL)?.copy(usePro = value)
                                ?: AppSettings.TranslateConfig.Provider.DeepL(usePro = value),
                    )
                }
            }

            override fun setGoogleCloudApiKey(value: String) {
                updateTranslateConfig {
                    copy(
                        provider =
                            (provider as? AppSettings.TranslateConfig.Provider.GoogleCloud)?.copy(apiKey = value)
                                ?: AppSettings.TranslateConfig.Provider.GoogleCloud(apiKey = value),
                    )
                }
            }

            override fun setLibreTranslateBaseUrl(value: String) {
                updateTranslateConfig {
                    copy(
                        provider =
                            (provider as? AppSettings.TranslateConfig.Provider.LibreTranslate)?.copy(baseUrl = value)
                                ?: AppSettings.TranslateConfig.Provider.LibreTranslate(baseUrl = value),
                    )
                }
            }

            override fun setLibreTranslateApiKey(value: String) {
                updateTranslateConfig {
                    copy(
                        provider =
                            (provider as? AppSettings.TranslateConfig.Provider.LibreTranslate)?.copy(apiKey = value)
                                ?: AppSettings.TranslateConfig.Provider.LibreTranslate(apiKey = value),
                    )
                }
            }

            override fun setTranslateProvider(value: TranslateProviderOption) {
                updateTranslateConfig {
                    copy(
                        provider =
                            when (value) {
                                TranslateProviderOption.AI -> {
                                    AppSettings.TranslateConfig.Provider.AI
                                }

                                TranslateProviderOption.GoogleWeb -> {
                                    AppSettings.TranslateConfig.Provider.GoogleWeb
                                }

                                TranslateProviderOption.DeepL -> {
                                    (provider as? AppSettings.TranslateConfig.Provider.DeepL)
                                        ?: AppSettings.TranslateConfig.Provider.DeepL()
                                }

                                TranslateProviderOption.GoogleCloud -> {
                                    (provider as? AppSettings.TranslateConfig.Provider.GoogleCloud)
                                        ?: AppSettings.TranslateConfig.Provider.GoogleCloud()
                                }

                                TranslateProviderOption.LibreTranslate -> {
                                    (provider as? AppSettings.TranslateConfig.Provider.LibreTranslate)
                                        ?: AppSettings.TranslateConfig.Provider.LibreTranslate()
                                }
                            },
                    )
                }
            }

            override fun selectType(type: AiTypeOption) {
                update {
                    when (type) {
                        AiTypeOption.OnDevice -> {
                            copy(type = AppSettings.AiConfig.Type.OnDevice())
                        }

                        AiTypeOption.OpenAI -> {
                            val currentType = this.type as? AppSettings.AiConfig.Type.OpenAI
                            copy(
                                type =
                                    currentType
                                        ?: AppSettings.AiConfig.Type.OpenAI(
                                            serverUrl = "",
                                            apiKey = "",
                                            model = "",
                                            reasoningEffort = "",
                                            extraBody = "",
                                        ),
                            )
                        }
                    }
                }
            }

            override fun selectTranslateProvider(type: TranslateProviderOption) {
                updateTranslateConfig {
                    copy(
                        provider =
                            when (type) {
                                TranslateProviderOption.AI -> {
                                    AppSettings.TranslateConfig.Provider.AI
                                }

                                TranslateProviderOption.GoogleWeb -> {
                                    AppSettings.TranslateConfig.Provider.GoogleWeb
                                }

                                TranslateProviderOption.DeepL -> {
                                    (provider as? AppSettings.TranslateConfig.Provider.DeepL)
                                        ?: AppSettings.TranslateConfig.Provider.DeepL()
                                }

                                TranslateProviderOption.GoogleCloud -> {
                                    (provider as? AppSettings.TranslateConfig.Provider.GoogleCloud)
                                        ?: AppSettings.TranslateConfig.Provider.GoogleCloud()
                                }

                                TranslateProviderOption.LibreTranslate -> {
                                    (provider as? AppSettings.TranslateConfig.Provider.LibreTranslate)
                                        ?: AppSettings.TranslateConfig.Provider.LibreTranslate()
                                }
                            },
                    )
                }
            }
        }
    }
}

private fun AppSettings.AiConfig.normalized(): AppSettings.AiConfig = this

private fun AppSettings.TranslateConfig.normalized(): AppSettings.TranslateConfig =
    copy(
        autoTranslateExcludedLanguages = normalizeExcludedLanguages(autoTranslateExcludedLanguages),
        provider =
            when (val provider = provider) {
                is AppSettings.TranslateConfig.Provider.LibreTranslate -> {
                    provider.copy(
                        baseUrl = provider.baseUrl.trim(),
                    )
                }

                else -> {
                    provider
                }
            },
    )

internal fun normalizeExcludedLanguages(languages: List<String>): List<String> =
    languages
        .asSequence()
        .flatMap { entry ->
            entry
                .split(',', '\n')
                .asSequence()
        }.map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { canonicalTranslationLanguage(it) ?: it.lowercase() }
        .toList()

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
