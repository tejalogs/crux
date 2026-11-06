package org.crux.data.network.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.crux.common.JSON
import org.crux.data.datastore.model.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenAIServiceTest {
    private val service = OpenAIService()

    @Test
    fun models_preservesBasePathAndReturnsSortedIds() =
        runTest {
            val urls =
                mapOf(
                    "https://example.com/custom/v1" to "https://example.com/custom/v1/models",
                    "https://example.com/custom/v1/" to "https://example.com/custom/v1/models",
                    "https://example.com/custom/v1/?api-version=test" to "https://example.com/custom/v1/models?api-version=test",
                )
            for ((baseUrl, expectedUrl) in urls) {
                withService(
                    handler = { request ->
                        assertEquals(HttpMethod.Get, request.method)
                        assertEquals(expectedUrl, request.url.toString())
                        assertEquals("Bearer test-key", request.headers[HttpHeaders.Authorization])
                        respond(
                            """{"object":"list","data":[{"id":"z-model","owned_by":"provider"},{"id":"a-model"}]}""",
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    },
                ) { service ->
                    assertEquals(listOf("a-model", "z-model"), service.models(baseUrl, "test-key"))
                }
            }
        }

    @Test
    fun models_retriesRateLimitsUpToThreeTimes() =
        runTest {
            var requests = 0
            withService(
                handler = {
                    requests++
                    if (requests <= 3) {
                        respond("""{"error":{"message":"Rate limited"}}""", HttpStatusCode.TooManyRequests)
                    } else {
                        respond("""{"data":[{"id":"model"}]}""")
                    }
                },
            ) { service ->
                assertEquals(listOf("model"), service.models("https://example.com/v1/", "test-key"))
                assertEquals(4, requests)
            }
        }

    @Test
    fun models_failsAfterRateLimitRetriesAreExhausted() =
        runTest {
            var requests = 0
            withService(
                handler = {
                    requests++
                    respond("""{"error":{"message":"Rate limited"}}""", HttpStatusCode.TooManyRequests)
                },
            ) { service ->
                val error =
                    assertFailsWith<ClientRequestException> {
                        service.models("https://example.com/v1/", "test-key")
                    }
                assertEquals(HttpStatusCode.TooManyRequests, error.response.status)
                assertEquals(4, requests)
            }
        }

    @Test
    fun models_reportsAuthenticationErrors() =
        runTest {
            withService(
                handler = {
                    respond("""{"error":{"message":"Invalid API key"}}""", HttpStatusCode.Unauthorized)
                },
            ) { service ->
                val error =
                    assertFailsWith<ClientRequestException> {
                        service.models("https://example.com/v1/", "test-key")
                    }
                assertEquals(HttpStatusCode.Unauthorized, error.response.status)
                assertTrue(error.message.contains("Invalid API key"))
            }
        }

    @Test
    fun models_rejectsMalformedResponses() =
        runTest {
            for (body in listOf("{}", """{"data":[{}]}""", """{"error":{"message":"Failure"}}""")) {
                withService(handler = { respond(body) }) { service ->
                    assertFailsWith<SerializationException> {
                        service.models("https://example.com/v1/", "test-key")
                    }
                }
            }
        }

    @Test
    fun chatCompletion_preservesRequestAndReadsOnlyFirstTextResponse() =
        runTest {
            withService(
                handler = { request ->
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals("https://example.com/custom/v1/chat/completions", request.url.toString())
                    assertEquals("Bearer test-key", request.headers[HttpHeaders.Authorization])
                    val content = request.body as TextContent
                    assertEquals(ContentType.Application.Json, content.contentType)
                    val body = JSON.parseToJsonElement(content.text).jsonObject
                    assertEquals("test-model", body["model"]?.jsonPrimitive?.content)
                    assertEquals("high", body["reasoning_effort"]?.jsonPrimitive?.content)
                    assertEquals("0.2", body["temperature"]?.jsonPrimitive?.content)
                    val message =
                        body
                            .getValue("messages")
                            .jsonArray
                            .single()
                            .jsonObject
                    assertEquals("user", message["role"]?.jsonPrimitive?.content)
                    assertEquals("Translate this", message["content"]?.jsonPrimitive?.content)
                    respond(
                        """{"id":"response","choices":[{"message":{"role":"assistant","content":"  translated  ","reasoning_content":"ignored"}},{"message":{"content":"second"}}],"usage":{"total_tokens":12}}""",
                    )
                },
            ) { service ->
                assertEquals(
                    "translated",
                    service.chatCompletion(
                        openAIConfig(reasoningEffort = "high", extraBody = """{"temperature":0.2}"""),
                        "Translate this",
                    ),
                )
            }
        }

    @Test
    fun chatCompletion_preservesEmptyAndNullContent() =
        runTest {
            val responses =
                listOf(
                    """{"choices":[]}""",
                    """{"choices":[{"message":{"content":null}}]}""",
                    """{"choices":[{"message":{}}]}""",
                )
            for (response in responses) {
                withService(handler = { respond(response) }) { service ->
                    assertEquals("", service.chatCompletion(openAIConfig(), "Hello"))
                }
            }
        }

    @Test
    fun chatCompletion_rejectsMalformedOrNonTextResponses() =
        runTest {
            val responses =
                listOf(
                    """{"error":{"message":"Failure"}}""",
                    """{"choices":[{}]}""",
                    """{"choices":[{"message":{"content":[{"type":"text","text":"unsupported"}]}}]}""",
                )
            for (response in responses) {
                withService(handler = { respond(response) }) { service ->
                    assertFailsWith<SerializationException> {
                        service.chatCompletion(openAIConfig(), "Hello")
                    }
                }
            }
        }

    @Test
    fun chatCompletion_doesNotRetryRateLimitsOrServerErrors() =
        runTest {
            for (status in listOf(HttpStatusCode.TooManyRequests, HttpStatusCode.InternalServerError)) {
                var requests = 0
                withService(
                    handler = {
                        requests++
                        respond("""{"error":{"message":"Try later"}}""", status)
                    },
                ) { service ->
                    when (status) {
                        HttpStatusCode.TooManyRequests -> {
                            assertFailsWith<ClientRequestException> { service.chatCompletion(openAIConfig(), "Hello") }
                        }

                        else -> {
                            assertFailsWith<ServerResponseException> { service.chatCompletion(openAIConfig(), "Hello") }
                        }
                    }
                    assertEquals(1, requests)
                }
            }
        }

    @Test
    fun models_propagatesCancellationWithoutRetrying() =
        runTest {
            var requests = 0
            withService(
                handler = {
                    requests++
                    throw CancellationException("Cancelled")
                },
            ) { service ->
                assertFailsWith<CancellationException> {
                    service.models("https://example.com/v1/", "test-key")
                }
                assertEquals(1, requests)
            }
        }

    @Test
    fun chatCompletionOrNull_skipsIncompleteConfiguration() =
        runTest {
            withService(handler = { error("Incomplete configuration must not send a request") }) { service ->
                for (config in listOf(openAIConfig().copy(serverUrl = ""), openAIConfig(model = ""))) {
                    assertNull(service.chatCompletionOrNull(config, "Hello"))
                }
            }
        }

    @Test
    fun chatCompletion_supportsLocalEndpointWithoutApiKey() =
        runTest {
            withService(
                handler = { request ->
                    assertNull(request.headers[HttpHeaders.Authorization])
                    respond(
                        """{"choices":[{"message":{"content":"Hello from local LLM"}}]}""",
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                },
            ) { service ->
                val result =
                    service.chatCompletion(
                        config = openAIConfig(apiKey = ""),
                        prompt = "Hello",
                    )
                assertEquals("Hello from local LLM", result)
            }
        }

    @Test
    fun buildChatCompletionBody_flattensExtraBodyIntoRoot() {
        val body =
            service.buildChatCompletionBody(
                config =
                    openAIConfig(
                        extraBody =
                            """
                            {"thinking":{"type":"enabled"},"stream":false}
                            """.trimIndent(),
                    ),
                prompt = "Hello from Crux",
            )

        assertEquals("test-model", body["model"]?.jsonPrimitive?.content)
        assertEquals(
            "Hello from Crux",
            body["messages"]
                ?.jsonArray
                ?.single()
                ?.jsonObject
                ?.get("content")
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals(
            "enabled",
            body["thinking"]
                ?.jsonObject
                ?.get("type")
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals(false, body["stream"]?.jsonPrimitive?.boolean)
        assertFalse(body.containsKey("extra_body"))
    }

    @Test
    fun buildChatCompletionBody_preservesBuiltInFieldsWhenExtraBodyCollides() {
        val body =
            service.buildChatCompletionBody(
                config =
                    openAIConfig(
                        reasoningEffort = "high",
                        extraBody =
                            """
                            {"model":"other-model","messages":[{"role":"assistant","content":"ignored"}],"reasoning_effort":"low","temperature":0.2}
                            """.trimIndent(),
                    ),
                prompt = "Keep built-in fields",
            )

        assertEquals("test-model", body["model"]?.jsonPrimitive?.content)
        assertEquals(
            "Keep built-in fields",
            body["messages"]
                ?.jsonArray
                ?.single()
                ?.jsonObject
                ?.get("content")
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals("high", body["reasoning_effort"]?.jsonPrimitive?.content)
        assertEquals("0.2", body["temperature"]?.jsonPrimitive?.content)
    }

    private fun openAIConfig(
        serverUrl: String = "https://example.com/custom/v1/",
        apiKey: String = "test-key",
        model: String = "test-model",
        reasoningEffort: String = "",
        extraBody: String = "",
    ) = AppSettings.AiConfig.Type.OpenAI(
        serverUrl = serverUrl,
        apiKey = apiKey,
        model = model,
        reasoningEffort = reasoningEffort,
        extraBody = extraBody,
    )

    private suspend fun TestScope.withService(
        handler: MockRequestHandler,
        block: suspend (OpenAIService) -> Unit,
    ) {
        val engine =
            MockEngine(
                MockEngineConfig().apply {
                    dispatcher = StandardTestDispatcher(testScheduler)
                    addHandler(handler)
                },
            )
        try {
            block(OpenAIService { config -> HttpClient(engine, config) })
        } finally {
            engine.close()
        }
    }
}
