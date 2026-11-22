package org.crux.data.network

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiEndpointLoggingTest {
    @Test
    fun aiModelEndpointsAreExcludedFromBodyLogging() {
        listOf(
            "/v1/chat/completions",
            "/chat/completions",
            "/v1/completions",
            "/v1/responses",
            "/v1/embeddings",
            "/v1/moderations",
            "/api/chat",
            "/api/generate",
            "/api/embed",
            "/api/chat/",
            "/v1beta/models/gemini-2.0-flash:generateContent",
            "/v1beta/models/gemini-2.0-flash:streamGenerateContent",
        ).forEach { path ->
            assertTrue(isAiEndpointPath(path), path)
        }
    }

    @Test
    fun ordinaryApiPathsKeepBodyLogging() {
        listOf(
            "/api/v1/timelines/home",
            "/api/v1/statuses",
            "/1.1/statuses/home_timeline.json",
            "/feed.xml",
            "",
        ).forEach { path ->
            assertFalse(isAiEndpointPath(path), path)
        }
    }
}
