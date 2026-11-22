package com.autopilot.android.domain.validation

import com.autopilot.android.domain.model.proxy.ProxyConfiguration
import com.autopilot.android.domain.model.proxy.ProxyProtocol
import java.net.InetAddress
import java.util.regex.Pattern

sealed class ProxyValidationResult {
    data object Valid : ProxyValidationResult()
    data class Error(val message: String) : ProxyValidationResult()

    val isValid: Boolean get() = this is Valid
    val errors: List<String> get() = when (this) {
        is Valid -> emptyList()
        is Error -> listOf(message)
    }
}

object ProxyValidator {

    private val IPV4_PATTERN = Pattern.compile(
        "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    )

    private val DOMAIN_PATTERN = Pattern.compile(
        "^(?!-)[A-Za-z0-9-]{1,63}(?<!-)(\\.[A-Za-z0-9-]{1,63})*\\.[A-Za-z]{2,}$"
    )

    fun validateConfiguration(proxy: ProxyConfiguration): ProxyValidationResult = validate(proxy)

    fun isValidPort(port: Int): Boolean = port in 1..65535

    fun validate(proxy: ProxyConfiguration): ProxyValidationResult {
        if (proxy.name.isBlank()) {
            return ProxyValidationResult.Error("Proxy name cannot be blank.")
        }
        if (proxy.name.length > 100) {
            return ProxyValidationResult.Error("Proxy name cannot exceed 100 characters.")
        }

        val host = proxy.host.trim()
        if (host.isBlank()) {
            return ProxyValidationResult.Error("Hostname or IP address cannot be blank.")
        }

        // Host cannot contain protocol prefixes or path slashes
        if (host.startsWith("http://", ignoreCase = true) ||
            host.startsWith("https://", ignoreCase = true) ||
            host.startsWith("socks://", ignoreCase = true) ||
            host.startsWith("socks5://", ignoreCase = true)
        ) {
            return ProxyValidationResult.Error("Host should not include protocol scheme (e.g. use '1.2.3.4' or 'example.com').")
        }

        if (host.contains("/") || host.contains(" ") || (host.contains(":") && !host.startsWith("["))) {
            return ProxyValidationResult.Error("Invalid host format. Specify host without port or path.")
        }

        if (!isValidHost(host)) {
            return ProxyValidationResult.Error("Invalid hostname or IP address format: '$host'.")
        }

        if (!isValidPort(proxy.port)) {
            return ProxyValidationResult.Error("Port must be between 1 and 65535. Given: ${proxy.port}")
        }

        val user = proxy.credentials?.username
        if (user != null && user.length > 128) {
            return ProxyValidationResult.Error("Username exceeds 128 characters.")
        }

        val pass = proxy.credentials?.password
        if (pass != null && pass.length > 128) {
            return ProxyValidationResult.Error("Password exceeds 128 characters.")
        }

        return ProxyValidationResult.Valid
    }

    fun isValidHost(host: String): Boolean {
        if (host.isBlank()) return false
        if (host.contains("/") || host.contains(" ") || host.contains("://")) return false
        val cleanHost = host.removePrefix("[").removeSuffix("]").trim()
        if (cleanHost.startsWith("-") || cleanHost.endsWith("-")) return false
        if (cleanHost.contains("..")) return false
        if (cleanHost.equals("localhost", ignoreCase = true)) return true
        if (IPV4_PATTERN.matcher(cleanHost).matches()) return true
        if (DOMAIN_PATTERN.matcher(cleanHost).matches()) return true

        // Check IPv6 via format check (e.g. 2001:db8::1 or ::1)
        if (cleanHost.contains(":")) {
            return try {
                val addr = java.net.Inet6Address.getByName(cleanHost)
                addr is java.net.Inet6Address
            } catch (e: Exception) {
                false
            }
        }
        return false
    }
}
