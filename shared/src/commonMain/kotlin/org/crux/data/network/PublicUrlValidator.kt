package org.crux.data.network

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

        val host = parsed.host.trim().trimStart('[').trimEnd(']').lowercase().trimEnd('.')
        if (host.isEmpty()) return false
        if (host == "localhost" || BLOCKED_HOST_SUFFIXES.any { host.endsWith(it) }) return false
        if (isPrivateOrLocalIpLiteral(host)) return false
        if (resolvesToNonPublicAddress(host)) return false
        return true
    }

    public fun requireSafePublicHttpUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        require(isSafePublicHttpUrl(trimmed)) { "Refused non-public or unsupported URL." }
        return trimmed
    }

    internal fun isPrivateOrLocalIpLiteral(host: String): Boolean {
        val ipv4 = parseStrictIpv4(host)
        if (ipv4 != null) {
            val a = ipv4[0]
            val b = ipv4[1]
            return a == 0 ||
                a == 10 ||
                a == 127 ||
                (a == 100 && b in 64..127) || // CGNAT 100.64.0.0/10
                (a == 169 && b == 254) || // Link-local / cloud metadata 169.254.0.0/16
                (a == 172 && b in 16..31) || // RFC1918 172.16.0.0/12
                (a == 192 && b == 168) || // RFC1918 192.168.0.0/16
                a >= 224 // Multicast / reserved
        }
        // IPv6 literals contain ':'
        if (host.contains(':')) {
            val normalized = host.substringBefore('%').lowercase()
            if (normalized == "::" || normalized == "::1" || normalized == "0:0:0:0:0:0:0:1") return true
            if (normalized.startsWith("fc") || normalized.startsWith("fd")) return true // Unique Local fc00::/7
            if (normalized.startsWith("fe8") || normalized.startsWith("fe9") || normalized.startsWith("fea") || normalized.startsWith("feb")) {
                return true // Link-local fe80::/10
            }
            if (normalized.startsWith("ff")) return true // Multicast
            if (normalized.startsWith("::ffff:")) {
                val mapped = normalized.removePrefix("::ffff:")
                return isPrivateOrLocalIpLiteral(mapped)
            }
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
}

internal expect fun resolvesToNonPublicAddress(host: String): Boolean
