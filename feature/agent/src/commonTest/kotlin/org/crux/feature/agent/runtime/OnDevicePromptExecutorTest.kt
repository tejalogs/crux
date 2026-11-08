package org.crux.feature.agent.runtime

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.Prompt
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.MessagePart
import kotlinx.coroutines.test.runTest
import org.crux.data.datastore.model.AppSettings
import org.crux.data.network.ktorClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OnDevicePromptExecutorTest {
    private val clientFactory = KtorKoogHttpClient.Factory(baseClient = ktorClient(config = {}))

    @Test
    fun loopbackValidatorEnforcesZeroCloudEgressPolicy() {
        assertTrue(OnDevicePromptExecutor.isLoopback("http://localhost:11434"))
        assertTrue(OnDevicePromptExecutor.isLoopback("http://127.0.0.1:11434"))
        assertTrue(OnDevicePromptExecutor.isLoopback("http://127.0.0.1"))
        assertTrue(OnDevicePromptExecutor.isLoopback("http://[::1]:11434"))
        assertTrue(OnDevicePromptExecutor.isLoopback("localhost:11434"))

        assertFalse(OnDevicePromptExecutor.isLoopback("https://api.openai.com/v1"))
        assertFalse(OnDevicePromptExecutor.isLoopback("https://cloud.gemma.dev"))
        assertFalse(OnDevicePromptExecutor.isLoopback("http://192.168.1.100:11434"))
        assertFalse(OnDevicePromptExecutor.isLoopback("http://10.0.0.2:11434"))
        assertFalse(OnDevicePromptExecutor.isLoopback(""))
    }

    @Test
    fun throwsSecurityExceptionOnNonLoopbackEndpoint() =
        runTest {
            val executor =
                OnDevicePromptExecutor(
                    config =
                        AppSettings.AiConfig.Type.OnDevice(
                            localhostEndpoint = "https://api.openai.com/v1",
                        ),
                    httpClientFactory = clientFactory,
                )

            val prompt =
                Prompt.build("test-prompt-1") {
                    user { text("Hello on-device AI") }
                }
            val model = LLModel(provider = LLMProvider.Ollama, id = "gemma-4-e2b")

            assertFailsWith<SecurityException> {
                executor.execute(prompt, model, emptyList())
            }
        }

    @Test
    fun executesOfflineSynthesisWithZeroEgress() =
        runTest {
            val executor =
                OnDevicePromptExecutor(
                    config = AppSettings.AiConfig.Type.OnDevice(),
                    httpClientFactory = clientFactory,
                )

            val prompt =
                Prompt.build("test-prompt-2") {
                    user { text("What are the key insights on my timeline?") }
                }
            val model = LLModel(provider = LLMProvider.Ollama, id = "gemma-4-e2b")

            val response = executor.execute(prompt, model, emptyList())
            assertEquals("stop", response.finishReason)
            assertTrue(response.parts.isNotEmpty())
            val textPart = response.parts.filterIsInstance<MessagePart.Text>().firstOrNull()
            assertNotNull(textPart)
            assertTrue(textPart.text.contains("Gemma 4 Edge"))
        }

    @Test
    fun generatesToolCallWhenSubscriptionUrlProvided() =
        runTest {
            val executor =
                OnDevicePromptExecutor(
                    config = AppSettings.AiConfig.Type.OnDevice(),
                    httpClientFactory = clientFactory,
                )

            val prompt =
                Prompt.build("test-prompt-3") {
                    user { text("帮我添加订阅https://mastodon.social/") }
                }
            val model = LLModel(provider = LLMProvider.Ollama, id = "gemma-4-e2b")
            val tools =
                listOf(
                    ToolDescriptor(
                        name = "save_subscription_source",
                        description = "Save subscription source",
                        requiredParameters = emptyList(),
                        optionalParameters = emptyList(),
                    ),
                )

            val response = executor.execute(prompt, model, tools)
            assertEquals("tool_calls", response.finishReason)
            val toolCall = response.parts.filterIsInstance<MessagePart.Tool.Call>().firstOrNull()
            assertNotNull(toolCall)
            assertEquals("save_subscription_source", toolCall.tool)
            assertTrue(toolCall.args.contains("https://mastodon.social/"))
            assertTrue(toolCall.args.contains("\"confirmed\":false"))
        }

    @Test
    fun onDeviceBridgeReportsAvailableAndCreatesRuntime() {
        val bridge = AiConfigKoogBridge()
        val config = AppSettings.AiConfig(type = AppSettings.AiConfig.Type.OnDevice())

        val availability = bridge.availability(config)
        assertEquals(AgentAvailability.Available, availability)

        val runtime = bridge.createRuntime(config, clientFactory)
        assertNotNull(runtime)
        assertEquals("gemma-4-e2b", runtime.model.id)
    }
}
