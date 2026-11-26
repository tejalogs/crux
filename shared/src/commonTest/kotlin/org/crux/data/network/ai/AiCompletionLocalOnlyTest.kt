package org.crux.data.network.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.crux.common.OnDeviceAI
import org.crux.data.datastore.model.AppSettings
import org.crux.ui.presenter.settings.AiTypeOption
import org.crux.ui.presenter.settings.aiTypeLabelFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A legacy or imported OpenAI-compatible config must never send prompts to its server while the
 * AI screen labels it "On-device". It runs on the device instead, and is migrated to OnDevice.
 */
class AiCompletionLocalOnlyTest {
    private val legacyCloud =
        AppSettings.AiConfig(
            tldr = true,
            agent = true,
            tldrPrompt = "custom prompt",
            type =
                AppSettings.AiConfig.Type.OpenAI(
                    serverUrl = "https://api.example.com/v1/",
                    apiKey = "sk-legacy",
                    model = "gpt-test",
                ),
        )

    @Test
    fun legacyCloudConfigNeverReachesItsServer() =
        runTest {
            var serverCalls = 0
            val engine =
                MockEngine {
                    serverCalls++
                    respond(
                        """{"choices":[{"message":{"content":"from the cloud"}}]}""",
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val service =
                AiCompletionService(
                    openAIService = OpenAIService { config -> HttpClient(engine, config) },
                    onDeviceAI = FakeOnDeviceAI(available = true, reply = "from the device"),
                )

            assertEquals("from the device", service.completePrompt(legacyCloud, "Hello"))
            assertEquals("from the device", service.tldr(legacyCloud, "source", "en", "Summarize"))
            assertEquals(0, serverCalls)
            engine.close()
        }

    @Test
    fun legacyCloudConfigReturnsNullWhenNoOnDeviceModel() =
        runTest {
            val service =
                AiCompletionService(
                    openAIService = OpenAIService(),
                    onDeviceAI = FakeOnDeviceAI(available = false, reply = "unused"),
                )

            assertNull(service.completePrompt(legacyCloud, "Hello"))
        }

    @Test
    fun blankOnDeviceReplyIsNull() =
        runTest {
            val service =
                AiCompletionService(
                    openAIService = OpenAIService(),
                    onDeviceAI = FakeOnDeviceAI(available = true, reply = "  "),
                )

            assertNull(service.completePrompt(AppSettings.AiConfig(), "Hello"))
        }

    @Test
    fun migrationOnlyChangesTheTypeValue() {
        val migrated = legacyCloud.withLocalAiOnly()

        assertEquals(AppSettings.AiConfig.Type.OnDevice(), migrated.type)
        assertEquals(legacyCloud.tldr, migrated.tldr)
        assertEquals(legacyCloud.agent, migrated.agent)
        assertEquals(legacyCloud.tldrPrompt, migrated.tldrPrompt)
        assertFalse(migrated.hasLegacyCloudType)
        assertTrue(legacyCloud.hasLegacyCloudType)
    }

    @Test
    fun onDeviceConfigIsLeftUntouched() {
        val custom =
            AppSettings.AiConfig(
                type = AppSettings.AiConfig.Type.OnDevice(model = "qwen3-0.6b", enableMtp = false),
            )

        assertSame(custom, custom.withLocalAiOnly())
        assertSame(custom.type, custom.type.localOnly())
    }

    @Test
    fun labelIsOnDeviceOnlyBecauseEveryTypeRunsOnDevice() {
        assertEquals(AiTypeOption.OnDevice, aiTypeLabelFor(legacyCloud.type))
        assertEquals(AiTypeOption.OnDevice, aiTypeLabelFor(AppSettings.AiConfig.Type.OnDevice()))
    }
}

private class FakeOnDeviceAI(
    private val available: Boolean,
    private val reply: String,
) : OnDeviceAI {
    override suspend fun isAvailable(): Boolean = available

    override suspend fun generate(prompt: String): String? = reply

    override suspend fun tldr(
        source: String,
        targetLanguage: String,
        prompt: String,
    ): String? = reply
}
