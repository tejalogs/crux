package org.crux.data.network

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PublicUrlValidatorTest {
    @Test
    fun allowsPublicHttpAndHttpsUrls() {
        assertTrue(PublicUrlValidator.isSafePublicHttpUrl("https://example.com/feed.xml"))
        assertTrue(PublicUrlValidator.isSafePublicHttpUrl("http://feeds.arstechnica.com/arstechnica/index"))
        assertTrue(PublicUrlValidator.isSafePublicHttpUrl("https://93.184.216.34/rss"))
    }

    @Test
    fun blocksLocalLoopbackPrivateMetadataAndNonHttpSchemes() {
        val blocked =
            listOf(
                "",
                "file:///data/data/org.crux.social/files/.master_vault.key",
                "content://org.crux.social.provider/cache/updates/crux.apk",
                "javascript:alert(1)",
                "http://localhost/admin",
                "http://127.0.0.1:8080/internal",
                "http://127.255.255.254/",
                "http://[::1]:11434/api",
                "http://0.0.0.0/",
                "http://169.254.169.254/latest/meta-data/",
                "http://10.0.0.1/router",
                "http://172.16.0.5/internal",
                "http://192.168.1.1/admin",
                "http://100.64.0.1/cgnat",
                "http://[fe80::1]/",
                "http://[fc00::1]/",
                "http://[::ffff:127.0.0.1]/",
                "https://user:pass@example.com/feed",
                "http://myhost.local/rss",
            )
        blocked.forEach { url ->
            assertFalse(PublicUrlValidator.isSafePublicHttpUrl(url), "Expected blocked: $url")
        }
    }
}
