package org.crux.data.model

import org.crux.data.model.LiteMediaPolicy.MediaDecision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiteModeTest {
    @Test
    fun offLoadsEverything() {
        assertTrue(LiteMode.OFF.loadsMedia)
        assertTrue(LiteMode.OFF.allowsVideoAutoplay)
    }

    @Test
    fun textOnlyBlocksMedia() {
        assertFalse(LiteMode.TEXT_ONLY.loadsMedia)
        assertFalse(LiteMode.TEXT_ONLY.allowsVideoAutoplay)
    }

    @Test
    fun balancedLoadsMediaButNotVideo() {
        assertTrue(LiteMode.BALANCED.loadsMedia)
        assertFalse(LiteMode.BALANCED.allowsVideoAutoplay)
    }
}

class LiteMediaPolicyTest {
    @Test
    fun offAlwaysLoadsFull() {
        assertEquals(
            MediaDecision.Full,
            LiteMediaPolicy.decide(
                liteMode = LiteMode.OFF,
                isVideo = true,
                hasPreviewAlternative = true,
                isOnUnmeteredConnection = false,
            ),
        )
    }

    @Test
    fun textOnlyHidesEverything() {
        assertEquals(
            MediaDecision.Hidden,
            LiteMediaPolicy.decide(
                liteMode = LiteMode.TEXT_ONLY,
                isVideo = false,
                hasPreviewAlternative = false,
                isOnUnmeteredConnection = true,
            ),
        )
        assertEquals(
            MediaDecision.Hidden,
            LiteMediaPolicy.decide(
                liteMode = LiteMode.TEXT_ONLY,
                isVideo = true,
                hasPreviewAlternative = true,
                isOnUnmeteredConnection = true,
            ),
        )
    }

    @Test
    fun balancedDowngradesVideoToPreview() {
        assertEquals(
            MediaDecision.PreviewOnly,
            LiteMediaPolicy.decide(
                liteMode = LiteMode.BALANCED,
                isVideo = true,
                hasPreviewAlternative = true,
                isOnUnmeteredConnection = true,
            ),
        )
    }

    @Test
    fun balancedStillLoadsImages() {
        assertEquals(
            MediaDecision.Full,
            LiteMediaPolicy.decide(
                liteMode = LiteMode.BALANCED,
                isVideo = false,
                hasPreviewAlternative = false,
                isOnUnmeteredConnection = false,
            ),
        )
    }

    @Test
    fun videoWithoutPreviewStillStaysPreviewOnly() {
        // The point of lite mode is not fetching the video bytes; the affordance stays, the
        // transfer does not.
        assertEquals(
            MediaDecision.PreviewOnly,
            LiteMediaPolicy.decide(
                liteMode = LiteMode.BALANCED,
                isVideo = true,
                hasPreviewAlternative = false,
                isOnUnmeteredConnection = true,
            ),
        )
    }
}
