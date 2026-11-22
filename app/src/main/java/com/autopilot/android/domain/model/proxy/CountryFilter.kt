package com.autopilot.android.domain.model.proxy

import java.util.regex.Pattern

object CountryCodeValidator {
    private val ISO_3166_ALPHA2_PATTERN = Pattern.compile("^[A-Z]{2}$")

    fun isValid(code: String?): Boolean {
        if (code.isNullOrBlank()) return false
        return ISO_3166_ALPHA2_PATTERN.matcher(code.trim().uppercase()).matches()
    }

    fun normalize(code: String?): String? {
        if (code.isNullOrBlank()) return null
        val upper = code.trim().uppercase()
        return if (ISO_3166_ALPHA2_PATTERN.matcher(upper).matches()) upper else null
    }
}

data class CountryFilterRule(
    val allowlist: Set<String> = emptySet(),
    val blocklist: Set<String> = emptySet()
) {
    private val normalizedAllowlist: Set<String> = allowlist.mapNotNull { CountryCodeValidator.normalize(it) }.toSet()
    private val normalizedBlocklist: Set<String> = blocklist.mapNotNull { CountryCodeValidator.normalize(it) }.toSet()

    fun matches(countryCode: String?): Boolean {
        val code = CountryCodeValidator.normalize(countryCode)

        // Null / Unknown country handling
        if (code == null) {
            val allowsUnknown = allowlist.any { it.equals("UNKNOWN", ignoreCase = true) || it == "??" }
            val blocksUnknown = blocklist.any { it.equals("UNKNOWN", ignoreCase = true) || it == "??" }

            if (blocksUnknown) return false
            if (normalizedAllowlist.isNotEmpty() && !allowsUnknown) return false
            return true
        }

        // Check explicit blocklist
        if (normalizedBlocklist.contains(code)) {
            return false
        }

        // Check allowlist
        if (normalizedAllowlist.isNotEmpty()) {
            return normalizedAllowlist.contains(code)
        }

        return true
    }

    val isEmpty: Boolean get() = normalizedAllowlist.isEmpty() && normalizedBlocklist.isEmpty()
}
