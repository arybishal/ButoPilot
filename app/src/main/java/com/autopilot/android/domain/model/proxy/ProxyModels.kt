package com.autopilot.android.domain.model.proxy

import java.util.UUID

enum class ProxyProtocol(val scheme: String, val displayName: String) {
    HTTP("http", "HTTP"),
    HTTPS("https", "HTTPS"),
    SOCKS5("socks5", "SOCKS5");

    companion object {
        fun fromString(value: String): ProxyProtocol {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) || it.scheme.equals(value, ignoreCase = true) }
                ?: HTTP
        }
    }
}

enum class ProxyHealthStatus(val displayName: String) {
    UNTESTED("Untested"),
    TESTING("Testing"),
    HEALTHY("Healthy"),
    UNHEALTHY("Unhealthy"),
    TIMEOUT("Timeout"),
    UNSUPPORTED("Unsupported"),
    AUTH_FAILURE("Auth Failure"),
    UNKNOWN("Unknown");

    companion object {
        fun fromString(value: String): ProxyHealthStatus {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
        }
    }
}

data class ProxyCredentials(
    val username: String? = null,
    val password: String? = null
)

data class ProxyConfiguration(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val protocol: ProxyProtocol = ProxyProtocol.HTTP,
    val host: String,
    val port: Int, // 1..65535
    val credentials: ProxyCredentials? = null,
    val region: String? = null, // Verified region metadata or null ("Unknown")
    val notes: String = "",
    val sourceId: String? = null,
    val enabled: Boolean = true,
    val lastHealthStatus: ProxyHealthStatus = ProxyHealthStatus.UNTESTED,
    val lastLatencyMs: Long? = null,
    val lastTestedAt: Long? = null,
    val lastErrorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toProxyUrl(): String {
        val scheme = when (protocol) {
            ProxyProtocol.HTTP -> "http"
            ProxyProtocol.HTTPS -> "https"
            ProxyProtocol.SOCKS5 -> "socks5"
        }
        return "$scheme://$host:$port"
    }

    fun getMaskedCredentials(): String {
        val user = credentials?.username
        val pass = credentials?.password
        if (user.isNullOrBlank() && pass.isNullOrBlank()) {
            return "None"
        }
        return if (!user.isNullOrBlank()) "$user:••••••" else "••••••"
    }

    fun getDisplayRegion(): String {
        return if (!region.isNullOrBlank()) region else "Unknown"
    }
}

enum class ProxySourceType(val displayName: String) {
    MANUAL("Manual Entry"),
    IMPORT_LIST("Imported List")
}

data class ProxySource(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val sourceType: ProxySourceType = ProxySourceType.MANUAL,
    val urlOrPath: String? = null,
    val lastRefreshedAt: Long? = null,
    val proxyCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

data class ProxyTestResult(
    val proxyId: String,
    val status: ProxyHealthStatus,
    val latencyMs: Long?,
    val timestamp: Long = System.currentTimeMillis(),
    val errorMessage: String? = null
)
