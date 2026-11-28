package org.crux.data.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

    @Test
    fun blocksPercentEncodedHostsThatOkHttpWouldDecode() {
        // Ktor keeps these hosts encoded, OkHttp decodes them to 127.0.0.1, localhost and
        // 169.254.169.254 before connecting.
        val blocked =
            listOf(
                "http://%31%32%37.0.0.1:11434/api/tags",
                "http://%6c%6f%63%61%6c%68%6f%73%74:11434/x",
                "http://%6c%6fcalhost/x",
                "https://%31%36%39.254.169.254/latest/meta-data/",
                "http://169.254.169.%32%35%34/",
                "http://example%2ecom/feed",
            )
        blocked.forEach { url ->
            assertFalse(PublicUrlValidator.isSafePublicHttpUrl(url), "Expected blocked: $url")
        }
    }

    @Test
    fun blocksShorthandIpv4FormsTheResolverAccepts() {
        val blocked =
            listOf(
                "http://2130706433/",
                "http://127.1/",
                "http://0x7f.0.0.1/",
                "http://0x7f000001/",
                "http://0177.0.0.1/",
                "http://012.0.0.1/",
            )
        blocked.forEach { url ->
            assertFalse(PublicUrlValidator.isSafePublicHttpUrl(url), "Expected blocked: $url")
        }
    }

    @Test
    fun blocksIpv6FormsThatEmbedPrivateIpv4() {
        val blocked =
            listOf(
                "http://[::ffff:7f00:1]/",
                "http://[::127.0.0.1]/",
                "http://[0:0:0:0:0:ffff:a9fe:a9fe]/",
                "http://[64:ff9b::a00:1]/",
                "http://[2002:c0a8:101::1]/",
                "http://[fec0::1]/",
                "http://[ff02::1]/",
                "http://[fe80::1%25eth0]/",
            )
        blocked.forEach { url ->
            assertFalse(PublicUrlValidator.isSafePublicHttpUrl(url), "Expected blocked: $url")
        }
    }

    @Test
    fun blocksUnicodeLookalikesThatIdnMapsToAscii() {
        val blocked =
            listOf(
                "http://\uFF11\uFF12\uFF17.0.0.1/", // full-width digits
                "http://127\u30020\u30020\u30021/", // ideographic full stop
                "http://\uFF4C\uFF4F\uFF43\uFF41\uFF4C\uFF48\uFF4F\uFF53\uFF54/", // full-width localhost
                "http://localho\u017Ft/", // long s
                "http://lo\uA7F2alhost/", // modifier letter capital C
                "http://exa!mple.com/",
            )
        blocked.forEach { url ->
            assertFalse(PublicUrlValidator.isSafePublicHttpUrl(url), "Expected blocked: $url")
        }
    }

    @Test
    fun hostLiteralCheckNeedsNoDnsAndKeepsNormalHosts() {
        listOf(
            "pbs.twimg.com",
            "cdn.example.co.uk",
            "93.184.216.34",
            "2606:4700::1111",
            "[2606:4700::1111]",
            "my_blog.example.com",
            "b\u00FCcher.de",
            "\u043F\u0440\u0438\u043C\u0435\u0440.\u0440\u0444",
            "1password.com",
        ).forEach { host ->
            assertFalse(PublicUrlValidator.isNonPublicHostLiteral(host), "Expected public: $host")
        }
        listOf(
            "localhost",
            "LOCALHOST.",
            "printer.local",
            "%31%32%37.0.0.1",
            "127.0.0.1",
            "10.1.2.3",
            "192.168.0.1",
            "169.254.169.254",
            "[::1]",
            "::",
            "fd00::1",
            "127.1",
            "",
        ).forEach { host ->
            assertTrue(PublicUrlValidator.isNonPublicHostLiteral(host), "Expected non-public: $host")
        }
    }

    @Test
    fun blocksCodePointsIdnaDropsBeforeConnecting() {
        listOf(
            "127.0.0.\u180B1", // Mongolian free variation selector one
            "localhos\u180Bt",
            "169.254.169.\u180C254",
            "127.0.0.\u180F1", // Mongolian free variation selector four
            "loc\u180Dalhost",
            "local\u00ADhost", // soft hyphen
            "127.0.0.1\u200B", // zero-width space
            "local\uFE0Fhost", // variation selector
            "\u3164localhost", // Hangul filler
        ).forEach { host ->
            assertTrue(PublicUrlValidator.isNonPublicHostLiteral(host), "Expected non-public: $host")
            assertFalse(PublicUrlValidator.isSafePublicHttpUrl("http://$host/"), "Expected blocked: $host")
        }
    }

    @Test
    fun blocksPunycodeLabelsThatDecodeToLocalNames() {
        listOf(
            "xn--localhost-",
            "127.0.0.xn--1-",
            "xn--127-.0.0.1",
            "printer.xn--local-",
            "xn--zz", // invalid punycode
            "xn--",
        ).forEach { host ->
            assertTrue(PublicUrlValidator.isNonPublicHostLiteral(host), "Expected non-public: $host")
        }
        assertFalse(PublicUrlValidator.isNonPublicHostLiteral("xn--bcher-kva.de"))
        assertFalse(PublicUrlValidator.isNonPublicHostLiteral("xn--e1afmkfd.xn--p1ai"))
    }

    @Test
    fun resolvesInternationalisedHostsByTheirAsciiName() {
        assertEquals("xn--bcher-kva.de", PublicUrlValidator.toResolvableAsciiHost("b\u00FCcher.de"))
        assertEquals("xn--e1afmkfd.xn--p1ai", PublicUrlValidator.toResolvableAsciiHost("\u043F\u0440\u0438\u043C\u0435\u0440.\u0440\u0444"))
        assertEquals("xn--fsqu00a.xn--0zwm56d", PublicUrlValidator.toResolvableAsciiHost("\u4F8B\u5B50.\u6D4B\u8BD5"))
        assertEquals("example.com", PublicUrlValidator.toResolvableAsciiHost("Example.COM."))
        assertEquals("xn--bcher-kva.de", PublicUrlValidator.toResolvableAsciiHost("xn--bcher-kva.de"))
        assertEquals("evil.example", PublicUrlValidator.toResolvableAsciiHost("xn--evil-.example"))
        assertEquals("::1", PublicUrlValidator.toResolvableAsciiHost("[::1]"))
    }

    @Test
    fun refusesHostsWhoseAsciiNameCannotBePredicted() {
        // Uppercase Unicode: Kotlin lowercasing is not IDNA case folding for every script.
        assertNull(PublicUrlValidator.toResolvableAsciiHost("B\u00DCcher.de"))
        assertNull(PublicUrlValidator.toResolvableAsciiHost("\u0391\u03A3.gr"))
        // IDNA deviation characters map differently in IDNA2003 and the HTTP client, so they stay blocked.
        listOf("stra\u00DFe.de", "\u03B1\u03C2.gr").forEach { host ->
            assertNull(PublicUrlValidator.toResolvableAsciiHost(host), "Expected unpredictable: $host")
            assertFalse(PublicUrlValidator.isSafePublicHttpUrl("https://$host/feed"), "Expected blocked: $host")
        }
        // Other scripts the built-in encoder can't predict fall back to the platform's IDNA conversion.
        listOf("\u0915\u093F.in", "\u1100\u1161.kr").forEach { host ->
            assertNull(PublicUrlValidator.toResolvableAsciiHost(host), "Expected unpredictable: $host")
            assertNotNull(platformAsciiHost(host), "Expected platform conversion: $host")
        }
    }

    @Test
    fun punycodeMatchesRfc3492Samples() {
        val samples =
            mapOf(
                "b\u00FCcher" to "bcher-kva",
                "\u043F\u0440\u0438\u043C\u0435\u0440" to "e1afmkfd",
                "\u4F8B\u5B50" to "fsqu00a",
                "m\u00FCnchen" to "mnchen-3ya",
                "\uD83D\uDE00" to "e28h", // emoji outside the BMP
            )
        samples.forEach { (unicode, ascii) ->
            val codePoints = unicode.codePointsForTest()
            assertEquals(ascii, Punycode.encode(codePoints), "encode $unicode")
            assertEquals(codePoints.toList(), Punycode.decode(ascii)?.toList(), "decode $ascii")
        }
        assertEquals(listOf('1'.code), Punycode.decode("1-")?.toList())
        assertNull(Punycode.decode("-abc"))
        assertNull(Punycode.decode("\u00FC-abc"))
    }

    private fun String.codePointsForTest(): IntArray {
        val out = mutableListOf<Int>()
        var i = 0
        while (i < length) {
            val c = this[i]
            if (c.isHighSurrogate()) {
                out += ((c.code - 0xD800) shl 10) + (this[i + 1].code - 0xDC00) + 0x10000
                i += 2
            } else {
                out += c.code
                i++
            }
        }
        return out.toIntArray()
    }
}
