package org.crux.feature.agent.runtime

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.Prompt
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.crux.common.OnDeviceAI
import org.crux.data.datastore.model.AppSettings
import org.crux.data.network.ktorClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LocalModelPromptExecutorTest {
    private val clientFactory = KtorKoogHttpClient.Factory(baseClient = ktorClient(config = {}))

    private class FakeDownloadedOnDeviceAI(
        private val downloaded: Boolean,
        private val response: String?,
        private val chunks: List<String> = emptyList(),
    ) : OnDeviceAI {
        var lastPrompt: String? = null
        var lastSystemPrompt: String? = null

        override suspend fun isAvailable(): Boolean = true

        override suspend fun hasDownloadedModel(): Boolean = downloaded

        override suspend fun translate(
            source: String,
            targetLanguage: String,
            prompt: String,
        ): String? = null

        override suspend fun tldr(
            source: String,
            targetLanguage: String,
            prompt: String,
        ): String? = null

        override suspend fun generate(
            prompt: String,
            systemPrompt: String?,
        ): String? {
            lastPrompt = prompt
            lastSystemPrompt = systemPrompt
            return response
        }

        override fun generateStreaming(
            prompt: String,
            systemPrompt: String?,
        ): Flow<String> {
            lastPrompt = prompt
            lastSystemPrompt = systemPrompt
            return flowOf(*chunks.toTypedArray())
        }
    }

    @Test
    fun usesDownloadedOnDeviceModelBeforeFallback() =
        runTest {
            val fakeAi =
                FakeDownloadedOnDeviceAI(
                    downloaded = true,
                    response = "Real quantized model output",
                )
            val bridge = AiConfigKoogBridge()
            val runtime =
                bridge.createRuntime(
                    aiConfig = AppSettings.AiConfig(type = AppSettings.AiConfig.Type.OnDevice()),
                    httpClientFactory = clientFactory,
                    onDeviceAI = fakeAi,
                )
            assertNotNull(runtime)

            val prompt =
                Prompt.build("local-llm-test") {
                    system("Be helpful.")
                    user { text("Summarize this post.") }
                }
            val executor = runtime.promptExecutor as LocalModelPromptExecutor
            val assistant = executor.execute(prompt, runtime.model, emptyList())
            val text = assistant.parts.filterIsInstance<MessagePart.Text>().firstOrNull()?.text

            assertEquals("stop", assistant.finishReason)
            assertEquals("Real quantized model output", text)
            assertEquals("Be helpful.", fakeAi.lastSystemPrompt)
            assertEquals("Summarize this post.", fakeAi.lastPrompt)
        }

    @Test
    fun streamsTokensFromDownloadedModel() =
        runTest {
            val fakeAi =
                FakeDownloadedOnDeviceAI(
                    downloaded = true,
                    response = null,
                    chunks = listOf("Hello ", "from ", "Gemma 4"),
                )
            val executor =
                LocalModelPromptExecutor(
                    config = AppSettings.AiConfig.Type.OnDevice(),
                    delegate =
                        OnDevicePromptExecutor(
                            config = AppSettings.AiConfig.Type.OnDevice(),
                            httpClientFactory = clientFactory,
                        ),
                    onDeviceAI = fakeAi,
                )
            val model = LLModel(provider = LLMProvider.Ollama, id = "gemma-4-e2b")
            val prompt =
                Prompt.build("stream-test") {
                    user { text("Say hi") }
                }

            val frames = executor.executeStreaming(prompt, model, emptyList()).toList()
            val deltas = frames.filterIsInstance<StreamFrame.TextDelta>().joinToString("") { it.text }
            assertEquals("Hello from Gemma 4", deltas)
            assertTrue(frames.last() is StreamFrame.End)
        }

    @Test
    fun enforcesZeroEgressLoopbackPolicyEvenWithDownloadedModel() =
        runTest {
            val fakeAi = FakeDownloadedOnDeviceAI(downloaded = true, response = "unused")
            val executor =
                LocalModelPromptExecutor(
                    config =
                        AppSettings.AiConfig.Type.OnDevice(
                            localhostEndpoint = "https://api.openai.com/v1",
                        ),
                    delegate =
                        OnDevicePromptExecutor(
                            config =
                                AppSettings.AiConfig.Type.OnDevice(
                                    localhostEndpoint = "https://api.openai.com/v1",
                                ),
                            httpClientFactory = clientFactory,
                        ),
                    onDeviceAI = fakeAi,
                )
            val prompt = Prompt.build("sec-test") { user { text("Hi") } }
            val model = LLModel(provider = LLMProvider.Ollama, id = "gemma-4-e2b")

            assertFailsWith<SecurityException> {
                executor.execute(prompt, model, emptyList())
            }
        }
}
