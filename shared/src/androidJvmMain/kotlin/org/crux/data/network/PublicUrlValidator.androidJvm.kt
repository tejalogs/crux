package org.crux.data.network

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException

internal actual fun resolvesToNonPublicAddress(host: String): Boolean =
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
