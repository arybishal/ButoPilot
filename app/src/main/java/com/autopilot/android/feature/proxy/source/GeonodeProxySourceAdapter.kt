package com.autopilot.android.feature.proxy.source

import com.autopilot.android.domain.model.proxy.CountryCodeValidator
import com.autopilot.android.domain.model.proxy.CountryFilterRule
import com.autopilot.android.domain.model.proxy.ProxyConfiguration
import com.autopilot.android.domain.model.proxy.ProxyHealthStatus
import com.autopilot.android.domain.model.proxy.ProxyProtocol
import com.autopilot.android.domain.validation.ProxyValidator
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import java.util.concurrent.TimeUnit

data class GeonodeProxyItem(
    @SerializedName("_id") val id: String? = null,
    @SerializedName("ip") val ip: String? = null,
    @SerializedName("port") val port: String? = null,
    @SerializedName("protocols") val protocols: List<String>? = null,
    @SerializedName("country") val country: String? = null,
    @SerializedName("city") val city: String? = null,
    @SerializedName("anonymityLevel") val anonymityLevel: String? = null,
    @SerializedName("latency") val latency: Double? = null,
    @SerializedName("responseTime") val responseTime: Long? = null,
    @SerializedName("speed") val speed: Int? = null,
    @SerializedName("upTime") val upTime: Double? = null,
    @SerializedName("org") val org: String? = null,
    @SerializedName("isp") val isp: String? = null
)

data class GeonodeApiResponse(
    @SerializedName("data") val data: List<GeonodeProxyItem>? = null,
    @SerializedName("total") val total: Int? = null,
    @SerializedName("page") val page: Int? = null,
    @SerializedName("limit") val limit: Int? = null
)

data class GeonodeFetchResult(
    val success: Boolean,
    val totalFetched: Int,
    val validProxies: List<ProxyConfiguration>,
    val filteredOutByCountry: Int,
    val duplicatesSkipped: Int,
    val invalidRecords: Int = 0,
    val missingCountryCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val errorMessage: String? = null
)

class GeonodeProxySourceAdapter(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val gson: Gson = Gson()
) {
    companion object {
        const val SOURCE_ID = "geonode"
        const val SOURCE_NAME = "GeoNode Free Proxy List"
        const val DEFAULT_URL = "https://proxylist.geonode.com/api/proxy-list?protocols=http&speed=fast&page=1&limit=500&sort_by=responseTime&sort_type=asc"
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    suspend fun fetchProxies(
        customUrl: String? = null,
        protocolFilter: ProxyProtocol? = null,
        countryFilter: CountryFilterRule? = null,
        existingProxies: List<ProxyConfiguration> = emptyList()
    ): GeonodeFetchResult = withContext(Dispatchers.IO) {
        val targetUrl = if (!customUrl.isNullOrBlank()) {
            customUrl.trim()
        } else {
            val protoParam = when (protocolFilter) {
                ProxyProtocol.HTTP -> "http"
                ProxyProtocol.HTTPS -> "https"
                ProxyProtocol.SOCKS5 -> "socks5"
                null -> "http"
            }
            "https://proxylist.geonode.com/api/proxy-list?protocols=$protoParam&speed=fast&page=1&limit=500&sort_by=responseTime&sort_type=asc"
        }

        val request = Request.Builder()
            .url(targetUrl)
            .header("User-Agent", USER_AGENT)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext GeonodeFetchResult(
                        success = false,
                        totalFetched = 0,
                        validProxies = emptyList(),
                        filteredOutByCountry = 0,
                        duplicatesSkipped = 0,
                        errorMessage = "HTTP error ${response.code}: ${response.message}"
                    )
                }

                val bodyString = response.body?.string()
                if (bodyString.isNullOrBlank()) {
                    return@withContext GeonodeFetchResult(
                        success = false,
                        totalFetched = 0,
                        validProxies = emptyList(),
                        filteredOutByCountry = 0,
                        duplicatesSkipped = 0,
                        errorMessage = "Empty response body received from GeoNode"
                    )
                }

                val apiResponse: GeonodeApiResponse = try {
                    gson.fromJson(bodyString, GeonodeApiResponse::class.java)
                } catch (e: Exception) {
                    return@withContext GeonodeFetchResult(
                        success = false,
                        totalFetched = 0,
                        validProxies = emptyList(),
                        filteredOutByCountry = 0,
                        duplicatesSkipped = 0,
                        errorMessage = "Failed to parse GeoNode JSON response: ${e.message}"
                    )
                }

                val items = apiResponse.data ?: emptyList()
                val existingKeys = existingProxies.map { "${it.host.lowercase()}:${it.port}" }.toMutableSet()
                val validList = mutableListOf<ProxyConfiguration>()
                var filteredByCountry = 0
                var duplicates = 0
                var invalidRecords = 0
                var missingCountryCount = 0

                for (item in items) {
                    val host = item.ip?.trim()
                    val port = item.port?.trim()?.toIntOrNull()
                    if (host.isNullOrBlank() || port == null || !ProxyValidator.isValidPort(port) || !ProxyValidator.isValidHost(host)) {
                        invalidRecords++
                        continue
                    }

                    val rawCountry = item.country
                    val countryCode = CountryCodeValidator.normalize(rawCountry)
                    if (countryCode == null) {
                        missingCountryCount++
                    }

                    if (countryFilter != null && !countryFilter.matches(countryCode)) {
                        filteredByCountry++
                        continue
                    }

                    val key = "${host.lowercase()}:$port"
                    if (existingKeys.contains(key)) {
                        duplicates++
                        continue
                    }
                    existingKeys.add(key)

                    val itemProto = when {
                        item.protocols?.any { it.equals("socks5", ignoreCase = true) } == true -> ProxyProtocol.SOCKS5
                        item.protocols?.any { it.equals("https", ignoreCase = true) } == true -> ProxyProtocol.HTTPS
                        else -> ProxyProtocol.HTTP
                    }

                    val notesList = mutableListOf<String>()
                    val location = item.city ?: item.country ?: "Unknown"
                    notesList.add("Location: $location")
                    if (item.speed != null) notesList.add("Speed: ${item.speed}")
                    if (item.upTime != null) notesList.add("Uptime: ${item.upTime.toInt()}%")
                    if (!item.anonymityLevel.isNullOrBlank()) notesList.add("Anonymity: ${item.anonymityLevel}")

                    val config = ProxyConfiguration(
                        id = UUID.randomUUID().toString(),
                        name = "GeoNode ${itemProto.displayName} ${countryCode ?: ""}".trim(),
                        protocol = itemProto,
                        host = host,
                        port = port,
                        credentials = null,
                        region = countryCode,
                        notes = "Imported from GeoNode (${notesList.joinToString(", ")})",
                        sourceId = SOURCE_ID,
                        enabled = true,
                        lastHealthStatus = ProxyHealthStatus.UNTESTED,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )

                    validList.add(config)
                }

                GeonodeFetchResult(
                    success = true,
                    totalFetched = items.size,
                    validProxies = validList,
                    filteredOutByCountry = filteredByCountry,
                    duplicatesSkipped = duplicates,
                    invalidRecords = invalidRecords,
                    missingCountryCount = missingCountryCount
                )
            }
        } catch (e: Exception) {
            GeonodeFetchResult(
                success = false,
                totalFetched = 0,
                validProxies = emptyList(),
                filteredOutByCountry = 0,
                duplicatesSkipped = 0,
                errorMessage = "Failed to fetch from GeoNode: ${e.message}"
            )
        }
    }
}
