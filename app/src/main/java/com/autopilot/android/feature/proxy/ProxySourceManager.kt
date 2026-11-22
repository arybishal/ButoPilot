package com.autopilot.android.feature.proxy

import com.autopilot.android.domain.model.proxy.ProxyConfiguration
import com.autopilot.android.domain.model.proxy.ProxyCredentials
import com.autopilot.android.domain.model.proxy.ProxyHealthStatus
import com.autopilot.android.domain.model.proxy.ProxyProtocol
import com.autopilot.android.domain.validation.ProxyValidator
import java.util.UUID

data class ProxyImportSummary(
    val totalParsed: Int,
    val validCount: Int,
    val duplicateCount: Int,
    val invalidCount: Int,
    val importedProxies: List<ProxyConfiguration>,
    val errors: List<String>
)

object ProxySourceManager {

    /**
     * Parses a raw multi-line string containing proxies in various common formats:
     * - host:port
     * - host:port:user:pass
     * - protocol://host:port
     * - protocol://user:pass@host:port
     *
     * Deduplicates against existing proxies by (host, port).
     */
    fun parseAndValidateList(
        rawContent: String,
        sourceId: String? = null,
        existingProxies: List<ProxyConfiguration> = emptyList()
    ): ProxyImportSummary {
        val existingKeys = existingProxies.map { "${it.host.lowercase()}:${it.port}" }.toMutableSet()
        val lines = rawContent.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }

        val imported = mutableListOf<ProxyConfiguration>()
        val errors = mutableListOf<String>()
        var duplicates = 0
        var invalid = 0

        for ((index, line) in lines.withIndex()) {
            val lineNumber = index + 1
            try {
                val parsed = parseLine(line, sourceId)
                if (parsed == null) {
                    invalid++
                    errors.add("Line $lineNumber: Unrecognized format '$line'")
                    continue
                }

                val validation = ProxyValidator.validateConfiguration(parsed)
                if (!validation.isValid) {
                    invalid++
                    errors.add("Line $lineNumber: ${validation.errors.firstOrNull()}")
                    continue
                }

                val key = "${parsed.host.lowercase()}:${parsed.port}"
                if (existingKeys.contains(key)) {
                    duplicates++
                    continue
                }

                existingKeys.add(key)
                imported.add(parsed)
            } catch (e: Exception) {
                invalid++
                errors.add("Line $lineNumber: Exception: ${e.message}")
            }
        }

        return ProxyImportSummary(
            totalParsed = lines.size,
            validCount = imported.size,
            duplicateCount = duplicates,
            invalidCount = invalid,
            importedProxies = imported,
            errors = errors
        )
    }

    private fun parseLine(rawLine: String, sourceId: String?): ProxyConfiguration? {
        var line = rawLine.trim()

        var protocol = ProxyProtocol.HTTP
        var username: String? = null
        var password: String? = null

        // Check for URI scheme (e.g. socks5:// or http:// or https://)
        if (line.contains("://")) {
            val schemeEnd = line.indexOf("://")
            val scheme = line.substring(0, schemeEnd).lowercase()
            protocol = ProxyProtocol.fromString(scheme)
            line = line.substring(schemeEnd + 3)
        }

        // Check for embedded user:pass@host:port
        if (line.contains("@")) {
            val atIndex = line.lastIndexOf("@")
            val userInfo = line.substring(0, atIndex)
            line = line.substring(atIndex + 1)

            val userPassParts = userInfo.split(":", limit = 2)
            if (userPassParts.isNotEmpty()) username = userPassParts[0]
            if (userPassParts.size > 1) password = userPassParts[1]
        }

        // Now line is either host:port or host:port:user:pass
        val parts = line.split(":")
        if (parts.size < 2) return null

        val host = parts[0].trim()
        val port = parts[1].trim().toIntOrNull() ?: return null

        if (parts.size >= 4 && username == null) {
            username = parts[2].trim()
            password = parts[3].trim()
        }

        val credentials = if (!username.isNullOrEmpty() || !password.isNullOrEmpty()) {
            ProxyCredentials(username = username, password = password)
        } else {
            null
        }

        return ProxyConfiguration(
            id = UUID.randomUUID().toString(),
            name = "${protocol.displayName} $host:$port",
            protocol = protocol,
            host = host,
            port = port,
            credentials = credentials,
            region = null,
            notes = "Imported from list",
            sourceId = sourceId,
            enabled = true,
            lastHealthStatus = ProxyHealthStatus.UNTESTED,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }
}
