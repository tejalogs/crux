package org.crux.feature.agent.presenter

import kotlinx.collections.immutable.persistentListOf
import org.crux.common.decodeJson
import org.crux.common.encodeJson
import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.model.ClickEvent
import org.crux.ui.model.UiTimelineV2
import org.crux.ui.model.UiTranslatableText
import org.crux.ui.render.toUi
import org.crux.ui.render.toUiPlainText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AgentMessagePlatformCompatibilityTest {
    @Test
    fun legacyPlatformFieldRoundTripsUnknownLegalId() {
        val message: AgentMessagePart = AgentMessagePart.PostCard(testPost("TestNet"))

        val legacyJson = message.encodeJson(AgentMessagePart.serializer())
        assertTrue(legacyJson.contains("\"platformType\":\"TestNet\""))
        assertFalse(legacyJson.contains("\"platformId\""))

        val decoded: AgentMessagePart = legacyJson.decodeJson()
        assertEquals("TestNet", assertIs<AgentMessagePart.PostCard>(decoded).post.platformId)
        assertEquals(legacyJson, decoded.encodeJson(AgentMessagePart.serializer()))
    }

    private fun testPost(platformId: String): UiTimelineV2.Post =
        UiTimelineV2.Post(
            platformId = platformId,
            images = persistentListOf(),
            sensitive = false,
            contentWarning = null,
            user = null,
            content = UiTranslatableText("fixture".toUiPlainText()),
            actions = persistentListOf(),
            poll = null,
            statusKey = MicroBlogKey("status", "testnet.example"),
            card = null,
            createdAt =
                kotlin.time.Clock.System
                    .now()
                    .toUi(),
            emojiReactions = persistentListOf(),
            sourceChannel = null,
            visibility = null,
            replyToHandle = null,
            clickEvent = ClickEvent.Noop,
            accountType = AccountType.Specific(MicroBlogKey("viewer", "testnet.example")),
        )
}
