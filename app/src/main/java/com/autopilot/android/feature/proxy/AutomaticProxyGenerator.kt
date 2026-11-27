package com.autopilot.android.feature.proxy

import com.autopilot.android.domain.model.proxy.CountryCodeValidator
import com.autopilot.android.domain.model.proxy.CountryFilterRule
import com.autopilot.android.domain.model.proxy.ProxyConfiguration
import com.autopilot.android.domain.model.proxy.ProxyProtocol
import com.autopilot.android.feature.proxy.source.GeonodeProxySourceAdapter
import com.autopilot.android.feature.proxy.source.ProxiflyProxySourceAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

enum class ProxyTier(val displayName: String, val shortLabel: String, val description: String) {
    TOP_TIER(
        displayName = "Top Tier",
        shortLabel = "Top",
        description = "High-speed, elite/HTTPS proxies & Tier-1 countries"
    ),
    MID_TIER(
        displayName = "Mid Tier",
        shortLabel = "Mid",
        description = "Balanced latency, standard proxies & Tier-2 countries"
    ),
    LOW_TIER(
        displayName = "Low Tier",
        shortLabel = "Low",
        description = "Broadest high-volume pool & Tier-3 countries"
    )
}

data class CountryOption(
    val code: String,
    val name: String,
    val tier: ProxyTier
)

data class ProxyGenerationRequest(
    val countries: Set<String> = emptySet(),
    val tier: ProxyTier = ProxyTier.TOP_TIER,
    val quantity: Int = AutomaticProxyGenerator.DEFAULT_QUANTITY,
    val protocolFilter: ProxyProtocol? = null
)

data class ProxyGenerationResult(
    val success: Boolean,
    val generatedProxies: List<ProxyConfiguration>,
    val totalCandidatesFound: Int,
    val duplicatesSkipped: Int,
    val sourcesUsed: List<String>,
    val errorMessage: String? = null
)

class AutomaticProxyGenerator(
    private val proxiflyAdapter: ProxiflyProxySourceAdapter = ProxiflyProxySourceAdapter(),
    private val geonodeAdapter: GeonodeProxySourceAdapter = GeonodeProxySourceAdapter()
) {

    companion object {
        const val MIN_QUANTITY = 1
        const val MAX_QUANTITY = 500
        const val DEFAULT_QUANTITY = 50
        const val SOURCE_ID = "auto_generator"

        private val UPTIME_REGEX = Regex("""Uptime:\s*(\d+)%""", RegexOption.IGNORE_CASE)

        val SUPPORTED_COUNTRIES: List<CountryOption> = listOf(
            // Top Tier (Tier 1)
            CountryOption("US", "United States", ProxyTier.TOP_TIER),
            CountryOption("GB", "United Kingdom", ProxyTier.TOP_TIER),
            CountryOption("CA", "Canada", ProxyTier.TOP_TIER),
            CountryOption("AU", "Australia", ProxyTier.TOP_TIER),
            CountryOption("DE", "Germany", ProxyTier.TOP_TIER),
            CountryOption("FR", "France", ProxyTier.TOP_TIER),
            CountryOption("NL", "Netherlands", ProxyTier.TOP_TIER),
            CountryOption("JP", "Japan", ProxyTier.TOP_TIER),
            CountryOption("SG", "Singapore", ProxyTier.TOP_TIER),
            CountryOption("CH", "Switzerland", ProxyTier.TOP_TIER),
            CountryOption("SE", "Sweden", ProxyTier.TOP_TIER),
            CountryOption("NO", "Norway", ProxyTier.TOP_TIER),
            CountryOption("DK", "Denmark", ProxyTier.TOP_TIER),
            CountryOption("FI", "Finland", ProxyTier.TOP_TIER),
            CountryOption("IE", "Ireland", ProxyTier.TOP_TIER),
            CountryOption("NZ", "New Zealand", ProxyTier.TOP_TIER),
            CountryOption("AT", "Austria", ProxyTier.TOP_TIER),
            CountryOption("BE", "Belgium", ProxyTier.TOP_TIER),

            // Mid Tier (Tier 2)
            CountryOption("ES", "Spain", ProxyTier.MID_TIER),
            CountryOption("IT", "Italy", ProxyTier.MID_TIER),
            CountryOption("PL", "Poland", ProxyTier.MID_TIER),
            CountryOption("PT", "Portugal", ProxyTier.MID_TIER),
            CountryOption("CZ", "Czechia", ProxyTier.MID_TIER),
            CountryOption("BR", "Brazil", ProxyTier.MID_TIER),
            CountryOption("MX", "Mexico", ProxyTier.MID_TIER),
            CountryOption("KR", "South Korea", ProxyTier.MID_TIER),
            CountryOption("TW", "Taiwan", ProxyTier.MID_TIER),
            CountryOption("HK", "Hong Kong", ProxyTier.MID_TIER),
            CountryOption("AE", "United Arab Emirates", ProxyTier.MID_TIER),
            CountryOption("SA", "Saudi Arabia", ProxyTier.MID_TIER),
            CountryOption("GR", "Greece", ProxyTier.MID_TIER),
            CountryOption("HU", "Hungary", ProxyTier.MID_TIER),
            CountryOption("RO", "Romania", ProxyTier.MID_TIER),
            CountryOption("TR", "Turkey", ProxyTier.MID_TIER),
            CountryOption("AR", "Argentina", ProxyTier.MID_TIER),
            CountryOption("CL", "Chile", ProxyTier.MID_TIER),
            CountryOption("ZA", "South Africa", ProxyTier.MID_TIER),
            CountryOption("MY", "Malaysia", ProxyTier.MID_TIER),
            CountryOption("TH", "Thailand", ProxyTier.MID_TIER),

            // Low Tier (Tier 3)
            CountryOption("ID", "Indonesia", ProxyTier.LOW_TIER),
            CountryOption("IN", "India", ProxyTier.LOW_TIER),
            CountryOption("PH", "Philippines", ProxyTier.LOW_TIER),
            CountryOption("VN", "Vietnam", ProxyTier.LOW_TIER),
            CountryOption("BD", "Bangladesh", ProxyTier.LOW_TIER),
            CountryOption("PK", "Pakistan", ProxyTier.LOW_TIER),
            CountryOption("NG", "Nigeria", ProxyTier.LOW_TIER),
            CountryOption("EG", "Egypt", ProxyTier.LOW_TIER),
            CountryOption("KE", "Kenya", ProxyTier.LOW_TIER),
            CountryOption("UA", "Ukraine", ProxyTier.LOW_TIER),
            CountryOption("RU", "Russia", ProxyTier.LOW_TIER),
            CountryOption("CO", "Colombia", ProxyTier.LOW_TIER),
            CountryOption("PE", "Peru", ProxyTier.LOW_TIER)
        )

        private val COUNTRY_TIER_MAP: Map<String, ProxyTier> =
            SUPPORTED_COUNTRIES.associate { it.code to it.tier }

        fun countriesForTier(tier: ProxyTier): List<CountryOption> =
            SUPPORTED_COUNTRIES.filter { it.tier == tier }

        fun countryCodesForTier(tier: ProxyTier): Set<String> =
            SUPPORTED_COUNTRIES.filter { it.tier == tier }.map { it.code }.toSet()

        fun tierForCountry(countryCode: String?): ProxyTier {
            val normalized = CountryCodeValidator.normalize(countryCode) ?: return ProxyTier.LOW_TIER
            return COUNTRY_TIER_MAP[normalized] ?: ProxyTier.LOW_TIER
        }
    }

    suspend fun generate(
        request: ProxyGenerationRequest,
        existingProxies: List<ProxyConfiguration> = emptyList()
    ): ProxyGenerationResult = withContext(Dispatchers.IO) {
        val normalizedCountries = request.countries
            .mapNotNull { CountryCodeValidator.normalize(it) }
            .toSet()

        val countryFilter = if (normalizedCountries.isNotEmpty()) {
            CountryFilterRule(allowlist = normalizedCountries)
        } else {
            null
        }

        val (proxiflyResult, geonodeResult) = coroutineScope {
            val proxiflyDeferred = async {
                proxiflyAdapter.fetchProxies(
                    protocolFilter = request.protocolFilter,
                    countryFilter = countryFilter,
                    existingProxies = existingProxies
                )
            }
            val geonodeDeferred = async {
                geonodeAdapter.fetchProxies(
                    protocolFilter = request.protocolFilter,
                    countryFilter = countryFilter,
                    existingProxies = existingProxies
                )
            }
            proxiflyDeferred.await() to geonodeDeferred.await()
        }

        val sourcesUsed = mutableListOf<String>()
        val allCandidates = mutableListOf<ProxyConfiguration>()
        val sourceErrors = mutableListOf<String>()

        if (proxiflyResult.success) {
            sourcesUsed.add("Proxifly")
            allCandidates.addAll(proxiflyResult.validProxies)
        } else if (!proxiflyResult.errorMessage.isNullOrBlank()) {
            sourceErrors.add("Proxifly: ${proxiflyResult.errorMessage}")
        }

        if (geonodeResult.success) {
            sourcesUsed.add("GeoNode")
            allCandidates.addAll(geonodeResult.validProxies)
        } else if (!geonodeResult.errorMessage.isNullOrBlank()) {
            sourceErrors.add("GeoNode: ${geonodeResult.errorMessage}")
        }

        if (sourcesUsed.isEmpty()) {
            return@withContext ProxyGenerationResult(
                success = false,
                generatedProxies = emptyList(),
                totalCandidatesFound = 0,
                duplicatesSkipped = 0,
                sourcesUsed = emptyList(),
                errorMessage = sourceErrors.joinToString("; ").ifBlank {
                    "Unable to reach proxy sources. Check your internet connection."
                }
            )
        }

        val selected = selectAndRankProxies(
            candidates = allCandidates,
            request = request.copy(countries = normalizedCountries),
            existingProxies = existingProxies
        )

        val totalDuplicatesSkipped = proxiflyResult.duplicatesSkipped + geonodeResult.duplicatesSkipped

        ProxyGenerationResult(
            success = true,
            generatedProxies = selected,
            totalCandidatesFound = allCandidates.size,
            duplicatesSkipped = totalDuplicatesSkipped,
            sourcesUsed = sourcesUsed,
            errorMessage = if (selected.isEmpty()) {
                "No new proxies matched the selected countries and tier."
            } else {
                null
            }
        )
    }

    internal fun selectAndRankProxies(
        candidates: List<ProxyConfiguration>,
        request: ProxyGenerationRequest,
        existingProxies: List<ProxyConfiguration> = emptyList()
    ): List<ProxyConfiguration> {
        val targetQuantity = request.quantity.coerceIn(MIN_QUANTITY, MAX_QUANTITY)
        val normalizedCountries = request.countries
            .mapNotNull { CountryCodeValidator.normalize(it) }
            .toSet()

        val existingKeys = existingProxies
            .map { "${it.host.lowercase()}:${it.port}" }
            .toMutableSet()

        val deduplicated = mutableListOf<ProxyConfiguration>()
        for (candidate in candidates) {
            val key = "${candidate.host.lowercase()}:${candidate.port}"
            if (existingKeys.contains(key)) continue
            if (request.protocolFilter != null && candidate.protocol != request.protocolFilter) continue
            if (normalizedCountries.isNotEmpty()) {
                val region = CountryCodeValidator.normalize(candidate.region)
                if (region == null || !normalizedCountries.contains(region)) continue
            }
            existingKeys.add(key)
            deduplicated.add(candidate)
        }

        val ranked = deduplicated
            .sortedByDescending { scoreProxyForTier(it, request.tier) }
            .take(targetQuantity)

        val now = System.currentTimeMillis()
        return ranked.map { proxy ->
            val countryLabel = proxy.region ?: "Global"
            val updatedNotes = if (proxy.notes.isBlank()) {
                "Auto-Generated (${request.tier.displayName}, $countryLabel)"
            } else {
                "Auto-Generated (${request.tier.displayName}) - ${proxy.notes}"
            }
            proxy.copy(
                sourceId = SOURCE_ID,
                notes = updatedNotes,
                updatedAt = now
            )
        }
    }

    internal fun scoreProxyForTier(proxy: ProxyConfiguration, targetTier: ProxyTier): Int {
        val countryTier = tierForCountry(proxy.region)
        val uptime = extractUptime(proxy.notes)
        val notesLower = proxy.notes.lowercase()
        val isElite = notesLower.contains("elite")
        val isAnonymous = notesLower.contains("anonymous")

        val qualityScore = buildQualityScore(proxy.protocol, uptime, isElite, isAnonymous)

        return when (targetTier) {
            ProxyTier.TOP_TIER -> {
                val countryBonus = when (countryTier) {
                    ProxyTier.TOP_TIER -> 40
                    ProxyTier.MID_TIER -> 15
                    ProxyTier.LOW_TIER -> 0
                }
                qualityScore + countryBonus
            }
            ProxyTier.MID_TIER -> {
                val countryBonus = when (countryTier) {
                    ProxyTier.MID_TIER -> 40
                    ProxyTier.TOP_TIER -> 20
                    ProxyTier.LOW_TIER -> 10
                }
                val midQualityFit = when {
                    uptime in 40..79 -> 30
                    isAnonymous -> 25
                    proxy.protocol == ProxyProtocol.HTTP || proxy.protocol == ProxyProtocol.HTTPS -> 15
                    else -> 5
                }
                midQualityFit + countryBonus
            }
            ProxyTier.LOW_TIER -> {
                val countryBonus = when (countryTier) {
                    ProxyTier.LOW_TIER -> 40
                    ProxyTier.MID_TIER -> 15
                    ProxyTier.TOP_TIER -> 5
                }
                val lowTierFit = when {
                    proxy.protocol == ProxyProtocol.HTTP -> 25
                    uptime != null && uptime < 60 -> 20
                    else -> 10
                }
                lowTierFit + countryBonus
            }
        }
    }

    private fun buildQualityScore(
        protocol: ProxyProtocol,
        uptime: Int?,
        isElite: Boolean,
        isAnonymous: Boolean
    ): Int {
        var score = 0
        score += when (protocol) {
            ProxyProtocol.HTTPS -> 25
            ProxyProtocol.SOCKS5 -> 25
            ProxyProtocol.HTTP -> 10
        }
        if (isElite) score += 25
        else if (isAnonymous) score += 15

        if (uptime != null) {
            score += when {
                uptime >= 85 -> 30
                uptime >= 65 -> 20
                uptime >= 40 -> 10
                else -> 0
            }
        }
        return score
    }

    private fun extractUptime(notes: String): Int? {
        val match = UPTIME_REGEX.find(notes) ?: return null
        return match.groupValues.getOrNull(1)?.toIntOrNull()
    }
}
