package com.autopilot.android.domain.validation

import java.net.URI
import java.net.URISyntaxException
import kotlin.random.Random

object UrlValidator {

    /**
     * Normalizes and validates a single URL.
     * Prefixes 'https://' if no scheme is specified.
     * Returns normalized URL if valid, or null if invalid.
     */
    fun normalizeAndValidate(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        val withScheme = if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            "https://$trimmed"
        } else {
            trimmed
        }

        return try {
            val uri = URI(withScheme)
            val host = uri.host
            if (host.isNullOrBlank() || !host.contains(".") || host.endsWith(".")) {
                null
            } else {
                withScheme
            }
        } catch (e: URISyntaxException) {
            null
        }
    }

    /**
     * Parses multiple URLs from a multiline or comma/semicolon delimited string.
     * Validates and normalizes each URL.
     * Returns a pair: (Valid Normalized URLs, Invalid Entries).
     */
    fun parseMultiple(rawInput: String): Pair<List<String>, List<String>> {
        val tokens = rawInput.split("\n", ",", ";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val valid = mutableListOf<String>()
        val invalid = mutableListOf<String>()

        for (token in tokens) {
            val normalized = normalizeAndValidate(token)
            if (normalized != null) {
                if (!valid.contains(normalized)) {
                    valid.add(normalized)
                }
            } else {
                invalid.add(token)
            }
        }

        return Pair(valid, invalid)
    }

    /**
     * Extracts host domain from a URL.
     */
    fun extractHost(url: String): String? {
        return try {
            URI(url).host?.lowercase()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Checks if a target URL's domain is in the allowed domains list.
     */
    fun isDomainAllowed(url: String, allowedDomains: List<String>): Boolean {
        if (allowedDomains.isEmpty()) return true
        val host = extractHost(url) ?: return false
        return allowedDomains.any { allowed ->
            val cleanAllowed = allowed.trim().lowercase()
            host == cleanAllowed || host.endsWith(".$cleanAllowed")
        }
    }
}

object IntervalCalculator {

    /**
     * Calculates the next refresh delay in milliseconds.
     */
    fun getNextRefreshDelayMs(
        fixedSec: Int,
        randomize: Boolean,
        minSec: Int,
        maxSec: Int
    ): Long {
        return if (randomize) {
            val effectiveMin = minOf(minSec, maxSec).coerceAtLeast(1)
            val effectiveMax = maxOf(minSec, maxSec).coerceAtLeast(effectiveMin)
            val chosen = Random.nextInt(effectiveMin, effectiveMax + 1)
            chosen * 1000L
        } else {
            fixedSec.coerceAtLeast(1) * 1000L
        }
    }

    /**
     * Calculates the next scroll delay in milliseconds.
     */
    fun getNextScrollDelayMs(
        fixedSec: Int,
        randomize: Boolean,
        minSec: Int,
        maxSec: Int
    ): Long {
        return if (randomize) {
            val effectiveMin = minOf(minSec, maxSec).coerceAtLeast(1)
            val effectiveMax = maxOf(minSec, maxSec).coerceAtLeast(effectiveMin)
            val chosen = Random.nextInt(effectiveMin, effectiveMax + 1)
            chosen * 1000L
        } else {
            fixedSec.coerceAtLeast(1) * 1000L
        }
    }
}
