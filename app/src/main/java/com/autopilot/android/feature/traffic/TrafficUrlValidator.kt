package com.autopilot.android.feature.traffic

import okhttp3.Dns
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException
import java.util.Locale

/**
 * HTTPS-only validation for the Traffic test target. Blocks hostnames and address
 * literals that resolve to loopback, private, link-local, reserved or metadata ranges.
 */
object TrafficUrlValidator {

    fun normalize(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null

        val candidate = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        val uri = try {
            URI(candidate)
        } catch (e: Exception) {
            return null
        }

        if (!uri.scheme.equals("https", ignoreCase = true) && !uri.scheme.equals("http", ignoreCase = true)) return null
        if (!uri.userInfo.isNullOrBlank()) return null
        if (uri.fragment != null) return null

        val host = uri.host?.lowercase(Locale.US) ?: return null
        if (isBlockedHost(host)) return null
        if (!host.contains('.')) return null
        val port = if (uri.port == -1) {
            if (uri.scheme.equals("http", ignoreCase = true)) 80 else 443
        } else uri.port
        if (port !in 1..65535) return null

        return candidate
    }

    fun isValid(input: String): Boolean = normalize(input) != null

    fun hostOf(url: String): String? = try {
        URI(url).host?.lowercase(Locale.US)
    } catch (e: Exception) {
        null
    }

    /** Returns parsed paths plus an error message for the first invalid entry. */
    fun parsePagePaths(input: String): Pair<List<String>, String?> {
        if (input.isBlank()) return Pair(emptyList(), null)
        val paths = input.split(",", "\n").map { it.trim() }.filter { it.isNotEmpty() }
        val invalid = paths.firstOrNull { path ->
            !path.startsWith("/") || path.startsWith("//") || path.any { it.isWhitespace() || it == '\\' }
        }
        if (invalid != null) return Pair(emptyList(), "Invalid page path: $invalid (use /path only)")
        return Pair(paths, null)
    }

    internal fun isBlockedHost(host: String): Boolean {
        val lower = host.lowercase(Locale.US).removeSuffix(".")
        if (lower == "localhost" || lower.endsWith(".localhost") ||
            lower == "metadata.google.internal" ||
            lower.endsWith(".local") || lower.endsWith(".internal") || lower.endsWith(".localdomain")
        ) return true

        val literal = lower.removePrefix("[").removeSuffix("]")
        if (literal.matches(Regex("[0-9a-fA-F:.]+"))) {
            val address = try {
                InetAddress.getByName(literal)
            } catch (e: Exception) {
                return true
            }
            return isBlockedAddress(address)
        }
        return false
    }

    internal fun isBlockedAddress(address: InetAddress): Boolean {
        if (address.isLoopbackAddress || address.isAnyLocalAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress
        ) return true

        // 169.254.x.x link-local is covered above; 100.64/10 CGNAT and 192.0.0/24 are reserved.
        val bytes = address.address
        if (bytes.size == 4) {
            val a = bytes[0].toInt() and 0xFF
            val b = bytes[1].toInt() and 0xFF
            if (a == 0 || a >= 224 || (a == 100 && b in 64..127) ||
                (a == 192 && b == 0) || (a == 198 && (b == 18 || b == 19))
            ) return true
        }
        return address.isIPv6Reserved()
    }

    private fun InetAddress.isIPv6Reserved(): Boolean {
        val bytes = address
        if (bytes.size != 16) return false
        // fc00::/7 unique-local, fe80::/10 link-local, ::/128 unspecified (already handled).
        val first = bytes[0].toInt() and 0xFF
        return (first and 0xFE) == 0xFC || (first and 0xC0) == 0x80
    }
}

/** Resolves every address OkHttp connects to, ensuring valid resolution. */
class PublicOnlyDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val resolved = Dns.SYSTEM.lookup(hostname).filterNot { TrafficUrlValidator.isBlockedAddress(it) }
        if (resolved.isEmpty()) {
            throw UnknownHostException("Unable to resolve public address for hostname: $hostname")
        }
        return resolved
    }
}
