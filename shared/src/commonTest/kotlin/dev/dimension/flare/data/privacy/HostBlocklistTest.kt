package dev.dimension.flare.data.privacy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HostBlocklistTest {
    @Test
    fun bareHostnamePassesThrough() {
        assertEquals("tracker.example.com", normaliseBlockedHost("tracker.example.com"))
    }

    @Test
    fun schemeAndPathAreStripped() {
        assertEquals("tracker.example.com", normaliseBlockedHost("https://tracker.example.com/x?y=1"))
        assertEquals("tracker.example.com", normaliseBlockedHost("http://tracker.example.com/a/b/c"))
    }

    @Test
    fun queryAndFragmentAreStripped() {
        assertEquals("tracker.example.com", normaliseBlockedHost("tracker.example.com?a=b#frag"))
    }

    @Test
    fun portIsStripped() {
        assertEquals("tracker.example.com", normaliseBlockedHost("tracker.example.com:443"))
        assertEquals("tracker.example.com", normaliseBlockedHost("https://tracker.example.com:8443/x"))
    }

    @Test
    fun credentialsAreStripped() {
        assertEquals("tracker.example.com", normaliseBlockedHost("user:pass@tracker.example.com"))
    }

    @Test
    fun caseAndWhitespaceAreNormalised() {
        assertEquals("tracker.example.com", normaliseBlockedHost("  TRACKER.Example.COM  "))
    }

    @Test
    fun leadingDotAndTrailingDotAreRemoved() {
        assertEquals("tracker.example.com", normaliseBlockedHost(".tracker.example.com"))
        assertEquals("tracker.example.com", normaliseBlockedHost("tracker.example.com."))
    }

    @Test
    fun ipv6LiteralSurvivesIntact() {
        val bracketed = "[2001:db8::1]"
        assertEquals(bracketed, normaliseBlockedHost("https://$bracketed:8443/path"))
        assertEquals(bracketed, normaliseBlockedHost(bracketed))
    }

    @Test
    fun subdomainIsBlockedByParentRule() {
        assertTrue(wouldBlock("metrics.tracker.example.com", setOf("tracker.example.com")))
    }

    @Test
    fun siblingDomainIsNotBlocked() {
        assertFalse(wouldBlock("other.example.com", setOf("tracker.example.com")))
    }

    @Test
    fun suffixAttackIsNotBlocked() {
        assertFalse(wouldBlock("tracker.example.com.evil.net", setOf("tracker.example.com")))
    }

    @Test
    fun normalisedRuleMatchesItsOwnHost() {
        val rule = normaliseBlockedHost("https://Metrics.Example.com:443/x")
        assertTrue(wouldBlock("metrics.example.com", setOf(rule)))
    }

    @Test
    fun emptyInputYieldsEmptyRule() {
        assertEquals("", normaliseBlockedHost("   "))
    }
}
