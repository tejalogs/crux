package org.crux.data.network

import java.net.IDN
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException

import io.ktor.http.Url

/**
 * Validates that an untrusted external URL targets a public HTTP(S) host rather than local
 * loopback, link-local, metadata, or RFC1918 private network endpoints (SSRF protection).
 */
public object PublicUrlValidator {
    private val ALLOWED_SCHEMES = setOf("http", "https")
    private val BLOCKED_HOST_SUFFIXES = setOf(".localhost", ".local", ".internal", ".home.arpa")

    public fun isSafePublicHttpUrl(rawUrl: String?): Boolean {
        val trimmed = rawUrl?.trim() ?: return false
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() || it.code < 0x20 || it == '\\' }) return false
        val parsed = runCatching { Url(trimmed) }.getOrNull() ?: return false
        val scheme = parsed.protocol.name.lowercase()
        if (scheme !in ALLOWED_SCHEMES) return false
        if (!parsed.user.isNullOrEmpty() || !parsed.password.isNullOrEmpty()) return false
        val authority = trimmed.substringAfter("://", "").substringBefore('/').substringBefore('?').substringBefore('#')
        if (authority.contains('@')) return false

        if (isNonPublicHostLiteral(parsed.host)) return false
        // The system resolver does no IDN conversion, so look up the ASCII name OkHttp will connect
        // to. A host whose ASCII form cannot be predicted here is refused rather than resolved.
        val asciiHost =
            toResolvableAsciiHost(parsed.host)
                ?: parsed.host.takeUnless { host -> host.any { it in IDNA_DEVIATION_CHARS } }?.let(::platformAsciiHost)
                ?: return false
        if (resolvesToNonPublicAddress(asciiHost)) return false
        return true
    }

    public fun requireSafePublicHttpUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        require(isSafePublicHttpUrl(trimmed)) { "Refused non-public or unsupported URL." }
        return trimmed
    }

    /**
     * True when [rawHost], as written in a URL, is or may become a non-public address, judged
     * from the text alone (no DNS lookup, so it is cheap enough for every image request).
     *
     * Ktor keeps the host exactly as written, but OkHttp percent-decodes it, maps Unicode
     * look-alikes (full-width digits, `。`, `ｌ`…) to ASCII, drops code points IDNA ignores
     * (variation selectors, soft hyphen…) and decodes `xn--` labels before it connects, so
     * `%31%32%37.0.0.1`, `１２７。０。０。１`, `127.0.0.\u180B1` or `127.0.0.xn--1-` would reach
     * 127.0.0.1. Any host that could be rewritten that way is refused, together with localhost
     * names, private and link-local IP literals, and the shorthand IPv4 forms (`2130706433`,
     * `127.1`, `0x7f.0.0.1`, `012.0.0.1`) that the system resolver turns into an address.
     */
    public fun isNonPublicHostLiteral(rawHost: String): Boolean {
        if (rawHost.contains('%')) return true
        val host = normalizeHost(rawHost)
        if (host.isEmpty()) return true
        if (host.contains(':')) {
            // IPv6 literal: only hex digits, ':' and an embedded dotted IPv4 tail are valid.
            if (!host.all { it in '0'..'9' || it in 'a'..'f' || it == ':' || it == '.' }) return true
            val bytes = parseIpv6(host) ?: return true
            return isNonPublicIpv6(bytes)
        }
        if (!host.all(::isAllowedHostChar)) return true
        // Judge the name OkHttp ends up with: an `xn--` label that decodes to ASCII becomes that ASCII.
        val decoded = decodeAsciiPunycodeLabels(host) ?: return true
        return isNonPublicName(host) || (decoded != host && isNonPublicName(decoded))
    }

    private fun isNonPublicName(host: String): Boolean {
        if (host.isEmpty() || !host.all(::isAllowedHostChar)) return true
        if (host == "localhost" || BLOCKED_HOST_SUFFIXES.any { host.endsWith(it) }) return true
        if (looksNumeric(host.substringAfterLast('.'))) {
            // A real top-level domain never starts with a digit, so this is an IP address in
            // some form. Only the canonical dotted quad is accepted, and only when public.
            val ipv4 = parseCanonicalIpv4(host) ?: return true
            return isNonPublicIpv4(ipv4[0], ipv4[1])
        }
        return false
    }

    internal fun isPrivateOrLocalIpLiteral(host: String): Boolean {
        val normalized = normalizeHost(host)
        val ipv4 = parseStrictIpv4(normalized)
        if (ipv4 != null) return isNonPublicIpv4(ipv4[0], ipv4[1])
        if (normalized.contains(':')) {
            val bytes = parseIpv6(normalized.substringBefore('%')) ?: return false
            return isNonPublicIpv6(bytes)
        }
        return false
    }

    public fun parseStrictIpv4(host: String): IntArray? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        val out = IntArray(4)
        for (i in 0 until 4) {
            val p = parts[i]
            if (p.isEmpty() || p.length > 3 || !p.all { it.isDigit() }) return null
            val v = p.toIntOrNull() ?: return null
            if (v !in 0..255) return null
            out[i] = v
        }
        return out
    }

    /**
     * The ASCII host OkHttp connects to for [rawHost], for the DNS check, or null when it cannot
     * be predicted without the platform's IDNA tables. Unicode labels are punycode-encoded when
     * every character is one IDNA leaves unchanged ([isIdnaStableChar]); `xn--` labels are
     * decoded and re-encoded the way OkHttp does.
     */
    internal fun toResolvableAsciiHost(rawHost: String): String? {
        val host = normalizeHost(rawHost)
        if (host.isEmpty()) return null
        if (host.contains(':')) return host
        // Lowercasing here is not the same as IDNA case folding for every script (final sigma,
        // Cherokee, ẞ…), so only accept Unicode that was already lowercase.
        if (rawHost.any { it.code >= 0x80 && (it.isUpperCase() || it.isTitleCase()) }) return null
        return host
            .split('.')
            .map { label ->
                when {
                    label.any { it.code >= 0x80 } -> {
                        if (!label.all { it.code < 0x80 || isIdnaStableChar(it) }) return null
                        PUNYCODE_PREFIX + (Punycode.encode(label.toCodePoints() ?: return null) ?: return null)
                    }
                    label.startsWith(PUNYCODE_PREFIX) -> {
                        val codePoints = Punycode.decode(label.removePrefix(PUNYCODE_PREFIX)) ?: return null
                        if (codePoints.isEmpty()) return null
                        if (codePoints.all { it < 0x80 }) {
                            codePoints.toAsciiString()
                        } else {
                            PUNYCODE_PREFIX + (Punycode.encode(codePoints) ?: return null)
                        }
                    }
                    else -> label
                }
            }.joinToString(".")
    }

    /** [host] with every `xn--` label that decodes to plain ASCII replaced by that ASCII, or null if one is invalid. */
    private fun decodeAsciiPunycodeLabels(host: String): String? =
        host
            .split('.')
            .map { label ->
                if (!label.startsWith(PUNYCODE_PREFIX)) return@map label
                val codePoints = Punycode.decode(label.removePrefix(PUNYCODE_PREFIX)) ?: return null
                if (codePoints.isEmpty()) return null
                if (codePoints.all { it < 0x80 }) codePoints.toAsciiString().lowercase() else label
            }.joinToString(".")

    private fun normalizeHost(rawHost: String): String =
        rawHost
            .trim()
            .trimStart('[')
            .trimEnd(']')
            .lowercase()
            .trimEnd('.')

    /** Dotted quad with no leading zeros, so it cannot be read as octal by the resolver. */
    private fun parseCanonicalIpv4(host: String): IntArray? {
        val parts = host.split('.')
        if (parts.any { it.length > 1 && it.startsWith('0') }) return null
        return parseStrictIpv4(host)
    }

    private fun looksNumeric(label: String): Boolean = label.isNotEmpty() && label[0] in '0'..'9'

    private fun isNonPublicIpv4(
        a: Int,
        b: Int,
    ): Boolean =
        a == 0 ||
            a == 10 ||
            a == 127 ||
            (a == 100 && b in 64..127) || // CGNAT 100.64.0.0/10
            (a == 169 && b == 254) || // Link-local / cloud metadata 169.254.0.0/16
            (a == 172 && b in 16..31) || // RFC1918 172.16.0.0/12
            (a == 192 && b == 168) || // RFC1918 192.168.0.0/16
            a >= 224 // Multicast / reserved

    private fun isNonPublicIpv6(bytes: IntArray): Boolean {
        val first = bytes[0]
        val second = bytes[1]
        if ((first and 0xFE) == 0xFC) return true // Unique local fc00::/7
        if (first == 0xFE && (second and 0xC0) == 0x80) return true // Link-local fe80::/10
        if (first == 0xFE && (second and 0xC0) == 0xC0) return true // Deprecated site-local fec0::/10
        if (first == 0xFF) return true // Multicast
        val zeroPrefix = (0 until 10).all { bytes[it] == 0 }
        // ::/96 (covers :: and ::1, IPv4-compatible) and ::ffff:0:0/96 (IPv4-mapped): OkHttp and the
        // resolver connect to the embedded IPv4 address.
        if (zeroPrefix && ((bytes[10] == 0 && bytes[11] == 0) || (bytes[10] == 0xFF && bytes[11] == 0xFF))) {
            return isNonPublicIpv4(bytes[12], bytes[13])
        }
        // NAT64 64:ff9b::/96 also carries an IPv4 address in its last 32 bits.
        val nat64Prefix = intArrayOf(0x00, 0x64, 0xFF, 0x9B, 0, 0, 0, 0, 0, 0, 0, 0)
        if (nat64Prefix.indices.all { bytes[it] == nat64Prefix[it] }) return isNonPublicIpv4(bytes[12], bytes[13])
        // 6to4 2002::/16 carries an IPv4 address in bytes 2-5.
        if (first == 0x20 && second == 0x02) return isNonPublicIpv4(bytes[2], bytes[3])
        return false
    }

    /** Parses an IPv6 literal (without brackets or zone) into 16 byte values, or null. */
    private fun parseIpv6(text: String): IntArray? {
        var body = text
        val tail = mutableListOf<Int>()
        if (body.contains('.')) {
            val ipv4Text = body.substringAfterLast(':')
            val ipv4 = parseCanonicalIpv4(ipv4Text) ?: return null
            ipv4.forEach { tail += it }
            body = body.removeSuffix(ipv4Text) + "0:0"
        }
        val halves = body.split("::")
        if (halves.size > 2) return null
        fun groups(part: String): List<Int>? =
            if (part.isEmpty()) {
                emptyList()
            } else {
                part.split(':').map { group ->
                    if (group.isEmpty() || group.length > 4) return null
                    group.toIntOrNull(16) ?: return null
                }
            }
        val head = groups(halves[0]) ?: return null
        val rest = if (halves.size == 2) groups(halves[1]) ?: return null else emptyList()
        val missing = 8 - head.size - rest.size
        if (halves.size == 2 && missing < 1) return null
        if (halves.size == 1 && missing != 0) return null
        val all = head + List(missing) { 0 } + rest
        val out = IntArray(16)
        all.forEachIndexed { i, group ->
            out[i * 2] = group shr 8
            out[i * 2 + 1] = group and 0xFF
        }
        if (tail.isNotEmpty()) tail.forEachIndexed { i, v -> out[12 + i] = v }
        return out
    }

    /**
     * ASCII letters, digits, '.', '-' and '_', plus letters of the common non-Latin scripts used
     * in internationalised domain names. Compatibility characters that IDNA maps to ASCII
     * (full-width forms, `ſ`, letter-like symbols, ideographic full stop, ligatures, modifier
     * letters, mathematical letters) are refused, so a host cannot turn into `localhost` or an
     * IP address after OkHttp's IDN conversion.
     */
    private fun isAllowedHostChar(c: Char): Boolean {
        if (c.code < 0x80) return c in 'a'..'z' || c in '0'..'9' || c == '.' || c == '-' || c == '_'
        val isMark = c.category == CharCategory.NON_SPACING_MARK || c.category == CharCategory.COMBINING_SPACING_MARK
        if (!c.isLetterOrDigit() && !isMark) return false
        val code = c.code
        return when {
            isIgnorableInHost(code) -> false // IDNA drops it, so "127.0.0.\u180B1" would become 127.0.0.1
            code == 0x017F -> false // ſ maps to 's'
            code == 0x0132 || code == 0x0133 -> false // Ĳ / ĳ map to "ij"
            code == 0x013F || code == 0x0140 || code == 0x0149 -> false // Ŀ / ŀ / ŉ map to "l·" / "ʼn"
            code == 0x037A || code == 0x1E9A -> false // ͺ maps to a space and a mark, ẚ to "aʾ"
            code in 0x01C4..0x01CC || code in 0x01F1..0x01F3 -> false // Dž / Lj / Nj / Dz digraphs map to ASCII
            code in 0xA720..0xA7FF || code in 0xAB30..0xAB6F -> false // Latin extended-D/E: modifier letters such as ꟲ map to ASCII
            code in 0x00C0..0x024F -> true // Latin-1 supplement and Latin extended letters
            code in 0x0370..0x1CFF -> true // Greek, Cyrillic, Armenian, Hebrew, Arabic, Indic, Thai, Georgian…
            code in 0x1E00..0x1FFF -> true // Latin extended additional (Vietnamese), Greek extended
            code in 0x3040..0x31FF -> true // Hiragana, Katakana, Bopomofo
            code in 0x3400..0x9FFF -> true // CJK ideographs
            code in 0xA000..0xD7FF -> true // Yi, Hangul and other scripts
            else -> false
        }
    }

    /**
     * Default-ignorable code points and those IDNA maps to nothing. Most are not letters and are
     * refused anyway; the ones listed by number are letters or marks inside the allowed ranges.
     */
    private fun isIgnorableInHost(code: Int): Boolean =
        code == 0x00AD || // Soft hyphen
            code == 0x034F || // Combining grapheme joiner
            code == 0x061C || // Arabic letter mark
            code == 0x115F || code == 0x1160 || // Hangul choseong / jungseong fillers
            code == 0x17B4 || code == 0x17B5 || // Khmer inherent vowels
            code in 0x180B..0x180F || // Mongolian free variation selectors and vowel separator
            code in 0x200B..0x200F || // Zero-width space, joiners, direction marks
            code in 0x202A..0x202E || // Bidi embedding controls
            code in 0x2060..0x206F || // Word joiner, invisible operators, deprecated format chars
            code == 0x3164 || // Hangul filler
            code in 0xFE00..0xFE0F || // Variation selectors
            code == 0xFEFF || // Zero-width no-break space
            code == 0xFFA0 // Halfwidth Hangul filler

    /**
     * Lowercase letters that IDNA (UTS 46 mapping and NFC) leaves exactly as they are, so their
     * punycode form is the name OkHttp looks up. Covers accented Latin, Greek, Cyrillic, Armenian,
     * Hebrew, Arabic letters, Georgian, Vietnamese, kana, CJK ideographs and Hangul syllables.
     * Combining marks are left out because NFC can compose or reorder them, so hosts in scripts
     * that need them (Indic, Thai) are refused by [isSafePublicHttpUrl] until the platform
     * resolver converts IDN names itself.
     */
    private fun isIdnaStableChar(c: Char): Boolean {
        if (!isAllowedHostChar(c) || c.isUpperCase() || c.isTitleCase()) return false
        if (c.category == CharCategory.NON_SPACING_MARK || c.category == CharCategory.COMBINING_SPACING_MARK) return false
        val code = c.code
        if (code in IDNA_CHANGED_LETTERS) return false
        return code in 0x00C0..0x024F ||
            code in 0x0370..0x03FF ||
            code in 0x0430..0x052F ||
            code in 0x0561..0x0586 ||
            code in 0x05D0..0x05EA ||
            code in 0x0620..0x064A ||
            code in 0x066E..0x06D3 ||
            code == 0x06D5 ||
            code in 0x10D0..0x10FA ||
            code in 0x1E00..0x1EFF ||
            code in 0x3041..0x3096 ||
            code in 0x309D..0x309E ||
            code in 0x30A1..0x30FA ||
            code in 0x30FC..0x30FE ||
            code in 0x3400..0x4DBF ||
            code in 0x4E00..0x9FFF ||
            code in 0xAC00..0xD7A3
    }

    /** Letters in the ranges above that IDNA maps, folds or treats as deviations. */
    private val IDNA_CHANGED_LETTERS: Set<Int> =
        setOf(
            0x00DF, // ß (deviation: kept by OkHttp, "ss" elsewhere)
            0x0140, // ŀ maps to "l·"
            0x0149, // ŉ maps to "ʼn"
            0x0374, // Greek numeral sign maps to ʹ
            0x0390, // ΐ (full case folding differs)
            0x03B0, // ΰ (full case folding differs)
            0x03C2, // ς (deviation: folds to σ)
            0x03D0, 0x03D1, 0x03D2, 0x03D5, 0x03D6, // Greek symbol letters map to β θ Υ φ π
            0x03F0, 0x03F1, 0x03F2, 0x03F4, 0x03F5, // Greek symbol letters map to κ ρ ς Θ ε
            0x03F9, // Greek capital lunate sigma
            0x0675, 0x0676, 0x0677, 0x0678, // Arabic high-hamza letters map to two letters
            0x1E96, 0x1E97, 0x1E98, 0x1E99, 0x1E9A, 0x1E9B, // Latin letters without a one-letter fold
        )

    private const val PUNYCODE_PREFIX = "xn--"

    private fun String.toCodePoints(): IntArray? {
        val out = ArrayList<Int>(length)
        var i = 0
        while (i < length) {
            val c = this[i]
            if (c.isHighSurrogate()) {
                val low = getOrNull(i + 1)?.takeIf { it.isLowSurrogate() } ?: return null
                out += ((c.code - 0xD800) shl 10) + (low.code - 0xDC00) + 0x10000
                i += 2
            } else {
                if (c.isLowSurrogate()) return null
                out += c.code
                i++
            }
        }
        return out.toIntArray()
    }

    private fun IntArray.toAsciiString(): String = buildString { this@toAsciiString.forEach { append(it.toChar()) } }
}

/** RFC 3492 punycode for a single label (without the `xn--` prefix), on code points. */
internal object Punycode {
    private const val BASE = 36
    private const val T_MIN = 1
    private const val T_MAX = 26
    private const val SKEW = 38
    private const val DAMP = 700
    private const val INITIAL_BIAS = 72
    private const val INITIAL_N = 0x80
    private const val MAX_LABEL_CODE_POINTS = 63

    fun encode(input: IntArray): String? {
        if (input.size > MAX_LABEL_CODE_POINTS) return null
        val out = StringBuilder()
        input.filter { it < INITIAL_N }.forEach { out.append(it.toChar()) }
        val basicCount = out.length
        if (basicCount > 0) out.append('-')
        var n = INITIAL_N
        var delta = 0L
        var bias = INITIAL_BIAS
        var handled = basicCount
        while (handled < input.size) {
            val m = input.filter { it >= n }.minOrNull() ?: return null
            delta += (m - n).toLong() * (handled + 1)
            n = m
            for (c in input) {
                if (c < n) delta++
                if (c == n) {
                    var q = delta
                    var k = BASE
                    while (true) {
                        val t = threshold(k, bias)
                        if (q < t) break
                        out.append(digitChar((t + (q - t) % (BASE - t)).toInt()))
                        q = (q - t) / (BASE - t)
                        k += BASE
                    }
                    out.append(digitChar(q.toInt()))
                    bias = adapt(delta, handled + 1, handled == basicCount)
                    delta = 0
                    handled++
                }
            }
            delta++
            n++
        }
        return out.toString()
    }

    fun decode(input: String): IntArray? {
        if (input.length > MAX_LABEL_CODE_POINTS * 4) return null
        val output = ArrayList<Int>()
        val delimiter = input.lastIndexOf('-')
        var pos = 0
        if (delimiter > 0) {
            for (j in 0 until delimiter) {
                val c = input[j]
                if (c.code >= INITIAL_N) return null
                output += c.code
            }
            pos = delimiter + 1
        }
        var n = INITIAL_N
        var i = 0L
        var bias = INITIAL_BIAS
        while (pos < input.length) {
            val oldI = i
            var w = 1L
            var k = BASE
            while (true) {
                if (pos >= input.length) return null
                val digit = digitValue(input[pos++])
                if (digit < 0) return null
                i += digit * w
                if (i > Int.MAX_VALUE) return null
                val t = threshold(k, bias)
                if (digit < t) break
                w *= (BASE - t)
                if (w > Int.MAX_VALUE) return null
                k += BASE
            }
            val size = output.size + 1
            bias = adapt(i - oldI, size, oldI == 0L)
            val next = n + i / size
            if (next > 0x10FFFF || next in 0xD800..0xDFFF) return null
            n = next.toInt()
            i %= size
            output.add(i.toInt(), n)
            i++
            if (output.size > MAX_LABEL_CODE_POINTS) return null
        }
        return output.toIntArray()
    }

    private fun threshold(
        k: Int,
        bias: Int,
    ): Int =
        when {
            k <= bias -> T_MIN
            k >= bias + T_MAX -> T_MAX
            else -> k - bias
        }

    private fun adapt(
        delta: Long,
        numPoints: Int,
        firstTime: Boolean,
    ): Int {
        var d = if (firstTime) delta / DAMP else delta / 2
        d += d / numPoints
        var k = 0
        while (d > ((BASE - T_MIN) * T_MAX) / 2) {
            d /= BASE - T_MIN
            k += BASE
        }
        return (k + (BASE - T_MIN + 1) * d / (d + SKEW)).toInt()
    }

    private fun digitChar(d: Int): Char = if (d < 26) 'a' + d else '0' + (d - 26)

    private fun digitValue(c: Char): Int =
        when (c) {
            in 'a'..'z' -> c - 'a'
            in 'A'..'Z' -> c - 'A'
            in '0'..'9' -> c - '0' + 26
            else -> -1
        }
}


/**
 * The platform's IDNA ASCII form of a Unicode host, or null when it cannot be converted. Used only
 * for hosts [PublicUrlValidator.toResolvableAsciiHost] cannot predict on its own; hosts with IDNA
 * deviation characters (ß, ς, ZWJ, ZWNJ) never reach it, because IDNA2003 and the HTTP client's
 * UTS #46 processing map those differently and the check would vet a different host than the one
 * actually contacted.
 */

private val IDNA_DEVIATION_CHARS = setOf('\u00DF', '\u03C2', '\u200C', '\u200D')



internal fun resolvesToNonPublicAddress(host: String): Boolean =
    try {
        InetAddress.getAllByName(host).any { addr ->
            addr.isLoopbackAddress ||
                addr.isAnyLocalAddress ||
                addr.isLinkLocalAddress ||
                addr.isSiteLocalAddress ||
                addr.isMulticastAddress ||
                isCgnatOrUniqueLocal(addr)
        }
    } catch (_: UnknownHostException) {
        // Offline/mocked hostnames fall through to the HTTP client so unit tests and error paths behave normally.
        false
    } catch (_: Throwable) {
        true
    }

private fun isCgnatOrUniqueLocal(addr: InetAddress): Boolean =
    when (addr) {
        is Inet4Address -> {
            val bytes = addr.address
            val a = bytes[0].toInt() and 0xFF
            val b = bytes[1].toInt() and 0xFF
            (a == 100 && b in 64..127) || a == 0 || a >= 224
        }

        is Inet6Address -> {
            val bytes = addr.address
            val first = bytes[0].toInt() and 0xFF
            (first and 0xFE) == 0xFC // fc00::/7 Unique Local Address
        }

        else -> {
            false
        }
    }

internal fun platformAsciiHost(host: String): String? =
    runCatching { IDN.toASCII(host, IDN.ALLOW_UNASSIGNED).lowercase().trimEnd('.') }
        .getOrNull()
        ?.takeIf { ascii -> ascii.isNotEmpty() && ascii.all { it.code < 0x80 } }

