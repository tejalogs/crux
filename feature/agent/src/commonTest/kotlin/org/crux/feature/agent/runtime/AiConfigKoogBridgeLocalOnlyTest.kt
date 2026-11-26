package org.crux.feature.agent.runtime

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.llm.LLMProvider
import org.crux.data.datastore.model.AppSettings
import org.crux.data.network.ktorClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class AiConfigKoogBridgeLocalOnlyTest {
    private val clientFactory = KtorKoogHttpClient.Factory(baseClient = ktorClient(config = {}))

    private val legacyCloudConfig =
        AppSettings.AiConfig(
            agent = true,
            type =
                AppSettings.AiConfig.Type.OpenAI(
                    serverUrl = "https://api.example.com/v1",
                    apiKey = "sk-legacy",
                    model = "gpt-legacy",
                ),
        )

    @Test
    fun legacyCloudConfigNeverBuildsACloudClient() {
        val runtime = assertNotNull(AiConfigKoogBridge().createRuntime(legacyCloudConfig, clientFactory))

        assertIs<OnDevicePromptExecutor>(runtime.promptExecutor)
        assertEquals(LLMProvider.Ollama, runtime.model.provider)
        assertEquals(AppSettings.AiConfig.Type.OnDevice.DEFAULT_MODEL, runtime.model.id)
    }

    @Test
    fun runtimeCarriesTheOnDeviceConfigItRuns() {
        val runtime = assertNotNull(AiConfigKoogBridge().createRuntime(legacyCloudConfig, clientFactory))

        assertEquals(AppSettings.AiConfig.Type.OnDevice(), runtime.aiConfig.type)
        assertEquals(true, runtime.aiConfig.agent)
    }

    @Test
    fun legacyCloudConfigIsAvailableAsOnDevice() {
        assertEquals(AgentAvailability.Available, AiConfigKoogBridge().availability(legacyCloudConfig))
    }

    @Test
    fun onDeviceSettingsAreKept() {
        val config =
            AppSettings.AiConfig(
                type = AppSettings.AiConfig.Type.OnDevice(model = "gemma-custom", contextLength = 4096L),
            )

        val runtime = assertNotNull(AiConfigKoogBridge().createRuntime(config, clientFactory))

        assertEquals("gemma-custom", runtime.model.id)
        assertEquals(4096L, runtime.model.contextLength)
        assertEquals(config, runtime.aiConfig)
    }
}
