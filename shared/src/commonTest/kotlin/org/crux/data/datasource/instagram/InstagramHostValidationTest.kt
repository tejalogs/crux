package org.crux.data.datasource.instagram

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

public class InstagramHostValidationTest {
    @Test
    public fun testValidInstagramHosts() {
        assertTrue(InstagramFeedService.isValidInstagramHost("instagram.com"))
        assertTrue(InstagramFeedService.isValidInstagramHost("www.instagram.com"))
        assertTrue(InstagramFeedService.isValidInstagramHost("i.instagram.com"))
        assertTrue(InstagramFeedService.isValidInstagramHost("threads.net"))
        assertTrue(InstagramFeedService.isValidInstagramHost("www.facebook.com"))
    }

    @Test
    public fun testInvalidInstagramHosts() {
        assertFalse(InstagramFeedService.isValidInstagramHost(null))
        assertFalse(InstagramFeedService.isValidInstagramHost(""))
        assertFalse(InstagramFeedService.isValidInstagramHost("   "))
        assertFalse(InstagramFeedService.isValidInstagramHost("attacker.com"))
        assertFalse(InstagramFeedService.isValidInstagramHost("notinstagram.com"))
        assertFalse(InstagramFeedService.isValidInstagramHost("instagram.com.evil.com"))
        assertFalse(InstagramFeedService.isValidInstagramHost("about:blank"))
    }
}
