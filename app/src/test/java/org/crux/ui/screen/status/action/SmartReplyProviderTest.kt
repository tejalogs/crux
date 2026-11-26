package org.crux.ui.screen.status.action

import org.crux.data.datastore.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartReplyProviderTest {
    @Test
    fun onDeviceProviderStaysLocalAndMayRefineOnOpen() {
        val type = AppSettings.AiConfig.Type.OnDevice()

        assertNull(smartReplyCloudHost(type))
        assertTrue(smartReplyAutoRefineAllowed(type))
    }

    @Test
    fun legacyCloudTypeRunsOnDeviceSoNoHostIsNamed() {
        // AiCompletionService runs a legacy OpenAI-compatible config on the device; telling the
        // user the post goes to its server would now be false.
        val type = AppSettings.AiConfig.Type.OpenAI(serverUrl = "https://api.example.com/v1", apiKey = "test")

        assertNull(smartReplyCloudHost(type))
        assertTrue(smartReplyAutoRefineAllowed(type))
    }

    @Test
    fun legacyCloudTypeWithoutSchemeOrAddressIsAlsoLocal() {
        assertNull(smartReplyCloudHost(AppSettings.AiConfig.Type.OpenAI(serverUrl = "api.example.com/v1", apiKey = "test")))
        assertNull(smartReplyCloudHost(AppSettings.AiConfig.Type.OpenAI(serverUrl = " ", apiKey = "test")))
        assertTrue(smartReplyAutoRefineAllowed(AppSettings.AiConfig.Type.OpenAI(serverUrl = "", apiKey = "")))
    }

    @Test
    fun resultBadgeNamesCloudHostOnlyWhenProviderAnswered() {
        assertEquals("api.example.com", smartReplyResultHost("api.example.com", producedByProvider = true))
        assertNull(smartReplyResultHost("api.example.com", producedByProvider = false))
        assertNull(smartReplyResultHost(null, producedByProvider = true))
        assertEquals("", smartReplyResultHost("", producedByProvider = true))
    }
}
