package org.crux.ui.screen.status.action

import org.crux.data.datastore.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun cloudProviderIsNamedAndNeedsATap() {
        val type = AppSettings.AiConfig.Type.OpenAI(serverUrl = "https://api.example.com/v1", apiKey = "test")

        assertEquals("api.example.com", smartReplyCloudHost(type))
        assertFalse(smartReplyAutoRefineAllowed(type))
    }

    @Test
    fun resultBadgeNamesCloudHostOnlyWhenProviderAnswered() {
        assertEquals("api.example.com", smartReplyResultHost("api.example.com", producedByProvider = true))
        assertNull(smartReplyResultHost("api.example.com", producedByProvider = false))
        assertNull(smartReplyResultHost(null, producedByProvider = true))
        assertEquals("", smartReplyResultHost("", producedByProvider = true))
    }

    @Test
    fun cloudProviderWithoutSchemeOrAddressIsStillTreatedAsCloud() {
        assertEquals(
            "api.example.com/v1",
            smartReplyCloudHost(AppSettings.AiConfig.Type.OpenAI(serverUrl = "api.example.com/v1", apiKey = "test")),
        )
        assertEquals("", smartReplyCloudHost(AppSettings.AiConfig.Type.OpenAI(serverUrl = " ", apiKey = "test")))
        assertFalse(smartReplyAutoRefineAllowed(AppSettings.AiConfig.Type.OpenAI(serverUrl = "", apiKey = "")))
    }
}
