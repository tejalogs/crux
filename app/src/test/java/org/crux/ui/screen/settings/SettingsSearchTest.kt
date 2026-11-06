package org.crux.ui.screen.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {
    private val sampleItems =
        listOf(
            SettingsHubItem(
                id = "accounts",
                category = SettingsCategory.SocialAndWeb,
                title = "Connected Accounts",
                subtitle = "Instagram, LinkedIn, X, Reddit & RSS",
                keywords = listOf("login", "profile", "fediverse", "sync"),
            ),
            SettingsHubItem(
                id = "live_web",
                category = SettingsCategory.SocialAndWeb,
                title = "Live Web Sessions",
                subtitle = "Isolated browser profiles for Instagram, LinkedIn, X & Reddit",
                keywords = listOf("cookies", "sandbox", "webview", "logout", "clear session"),
            ),
            SettingsHubItem(
                id = "appearance_theme",
                category = SettingsCategory.AppearanceAndReader,
                title = "Theme, Colors & Reader",
                subtitle = "Dark mode, OLED pure black, dynamic color & Kindle reader typography",
                keywords = listOf("sepia", "serif", "font size", "pages", "scroll"),
            ),
            SettingsHubItem(
                id = "ai_config",
                category = SettingsCategory.Intelligence,
                title = "On-Device AI & Gemma",
                subtitle = "Smart Reply, Omni-Scribe synthesis & TL;DR",
                keywords = listOf("llm", "carousel", "summary"),
            ),
        )

    @Test
    fun blankQueryReturnsAllItems() {
        assertEquals(sampleItems, filterSettingsItems(sampleItems, ""))
        assertEquals(sampleItems, filterSettingsItems(sampleItems, "   "))
    }

    @Test
    fun matchesTitleSubtitleAndKeywordsAcrossMultipleTerms() {
        val oledMatches = filterSettingsItems(sampleItems, "oled sepia")
        assertEquals(listOf("appearance_theme"), oledMatches.map { it.id })

        val cookieMatches = filterSettingsItems(sampleItems, "instagram cookies")
        assertEquals(listOf("live_web"), cookieMatches.map { it.id })

        val aiMatches = filterSettingsItems(sampleItems, "gemma carousel")
        assertEquals(listOf("ai_config"), aiMatches.map { it.id })
    }

    @Test
    fun unmatchedQueryReturnsEmptyList() {
        assertTrue(filterSettingsItems(sampleItems, "nonexistent_setting_xyz").isEmpty())
    }
}
