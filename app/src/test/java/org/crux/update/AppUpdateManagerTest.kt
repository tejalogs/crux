package org.crux.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {
    @Test
    fun testIsNewerVersion_greaterMajor() {
        assertTrue(AppUpdateManager.isNewerVersion("v2.0.0", "1.0.0"))
        assertTrue(AppUpdateManager.isNewerVersion("2.0.0", "1.9.9"))
    }

    @Test
    fun testIsNewerVersion_greaterMinor() {
        assertTrue(AppUpdateManager.isNewerVersion("v1.1.0", "1.0.0"))
        assertTrue(AppUpdateManager.isNewerVersion("1.10.0", "1.9.0"))
    }

    @Test
    fun testIsNewerVersion_greaterPatch() {
        assertTrue(AppUpdateManager.isNewerVersion("v1.0.1", "1.0.0"))
        assertTrue(AppUpdateManager.isNewerVersion("1.0.10", "1.0.9"))
    }

    @Test
    fun testIsNewerVersion_sameVersion() {
        assertFalse(AppUpdateManager.isNewerVersion("v1.0.0", "1.0.0"))
        assertFalse(AppUpdateManager.isNewerVersion("1.0.0", "v1.0.0"))
        assertFalse(AppUpdateManager.isNewerVersion("v1.0.0", "1.0.0-rc1"))
    }

    @Test
    fun testIsNewerVersion_olderVersion() {
        assertFalse(AppUpdateManager.isNewerVersion("v0.9.9", "1.0.0"))
        assertFalse(AppUpdateManager.isNewerVersion("1.0.0", "1.0.1"))
    }

    @Test
    fun testShouldRunAutoUpdateCheck_respectsToggleAndCooldown() {
        val now = 100_000_000L
        assertFalse(shouldRunAutoUpdateCheck(autoCheckEnabled = false, lastCheckAtMs = 0L, nowMs = now))
        assertTrue(shouldRunAutoUpdateCheck(autoCheckEnabled = true, lastCheckAtMs = 0L, nowMs = now))
        assertFalse(
            shouldRunAutoUpdateCheck(
                autoCheckEnabled = true,
                lastCheckAtMs = now - AUTO_CHECK_INTERVAL_MS + 1L,
                nowMs = now,
            ),
        )
        assertTrue(
            shouldRunAutoUpdateCheck(
                autoCheckEnabled = true,
                lastCheckAtMs = now - AUTO_CHECK_INTERVAL_MS,
                nowMs = now,
            ),
        )
        assertFalse(shouldRunAutoUpdateCheck(autoCheckEnabled = true, lastCheckAtMs = now + 10_000L, nowMs = now))
    }

    @Test
    fun testShouldPromptRelease_respectsVersionAndSkippedTag() {
        assertTrue(shouldPromptRelease("v2.0.0", skippedTag = null, currentVersion = "1.0.0"))
        assertFalse(shouldPromptRelease("v2.0.0", skippedTag = "v2.0.0", currentVersion = "1.0.0"))
        assertFalse(shouldPromptRelease(" v2.0.0 ", skippedTag = "V2.0.0", currentVersion = "1.0.0"))
        assertTrue(shouldPromptRelease("v2.1.0", skippedTag = "v2.0.0", currentVersion = "1.0.0"))
        assertFalse(shouldPromptRelease("v1.0.0", skippedTag = null, currentVersion = "1.0.0"))
        assertFalse(shouldPromptRelease("v0.9.0", skippedTag = null, currentVersion = "1.0.0"))
    }

    @Test
    fun trustedDownloadUrl_acceptsOnlyHttpsGithubHosts() {
        assertTrue(AppUpdateManager.isTrustedDownloadUrl("https://github.com/tejalogs/crux/releases/download/v1.0.2/crux.apk"))
        assertTrue(AppUpdateManager.isTrustedDownloadUrl("https://objects.githubusercontent.com/x/crux.apk"))
        assertFalse(AppUpdateManager.isTrustedDownloadUrl("https://github.com/attacker/repo/releases/download/v1.0.2/evil.apk"))
        assertFalse(AppUpdateManager.isTrustedDownloadUrl("http://github.com/tejalogs/crux/crux.apk"))
        assertFalse(AppUpdateManager.isTrustedDownloadUrl("https://github.com.evil.example/crux.apk"))
        assertFalse(AppUpdateManager.isTrustedDownloadUrl("https://evil.example/crux.apk"))
        assertFalse(AppUpdateManager.isTrustedDownloadUrl("not a url"))
        assertFalse(AppUpdateManager.isTrustedDownloadUrl(null))
    }

    @Test
    fun sanitizeReleaseUrl_allowsOnlyCruxRepoAndFallsBackOtherwise() {
        assertEquals(
            "https://github.com/tejalogs/crux/releases/tag/v1.0.8",
            AppUpdateManager.sanitizeReleaseUrl("https://github.com/tejalogs/crux/releases/tag/v1.0.8"),
        )
        assertEquals(
            org.crux.CruxLinks.RELEASES,
            AppUpdateManager.sanitizeReleaseUrl("https://evil.example/phish"),
        )
        assertEquals(
            org.crux.CruxLinks.RELEASES,
            AppUpdateManager.sanitizeReleaseUrl("https://github.com/attacker/crux/releases/tag/v9.9.9"),
        )
        assertEquals(
            org.crux.CruxLinks.RELEASES,
            AppUpdateManager.sanitizeReleaseUrl("javascript:alert(1)"),
        )
    }

    @Test
    fun pendingPromptUpdate_storesAndClearsState() {
        val info =
            AppReleaseInfo(
                tagName = "v1.0.3",
                versionName = "1.0.3",
                title = "Release 1.0.3",
                changelog = "Changes",
                releaseUrl = "https://github.com/tejalogs/crux/releases/tag/v1.0.3",
                apkUrl = null,
                apkSizeBytes = 0L,
            )
        AppUpdateManager.pendingPromptUpdate = info
        org.junit.Assert.assertEquals("v1.0.3", AppUpdateManager.pendingPromptUpdate?.tagName)
        AppUpdateManager.pendingPromptUpdate = null
        org.junit.Assert.assertNull(AppUpdateManager.pendingPromptUpdate)
    }

    @Test
    fun signerMismatchMessage_distinguishesDebugFromRelease() {
        val debugMsg = AppUpdateManager.signerMismatchMessage(isDebugBuild = true)
        val releaseMsg = AppUpdateManager.signerMismatchMessage(isDebugBuild = false)
        assertTrue(debugMsg.contains("debug", ignoreCase = true))
        assertTrue(releaseMsg.contains("not signed with the same key", ignoreCase = true))
    }

    @Test
    fun ownReleaseAssetUrl_acceptsOnlyThisRepositoryReleases() {
        assertTrue(AppUpdateManager.isOwnReleaseAssetUrl("https://github.com/tejalogs/crux/releases/download/v1.0.10/app-release.apk"))
        assertFalse(AppUpdateManager.isOwnReleaseAssetUrl("https://github.com/someone/crux/releases/download/v9/app-release.apk"))
        assertFalse(AppUpdateManager.isOwnReleaseAssetUrl("https://objects.githubusercontent.com/x/app-release.apk"))
        assertFalse(AppUpdateManager.isOwnReleaseAssetUrl("http://github.com/tejalogs/crux/releases/download/v1/app.apk"))
    }
}
