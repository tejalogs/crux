package org.crux.update

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
}
