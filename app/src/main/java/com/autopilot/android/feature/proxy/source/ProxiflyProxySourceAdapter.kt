package com.autopilot.android.feature.proxy.source

import com.autopilot.android.domain.model.proxy.CountryCodeValidator
import com.autopilot.android.domain.model.proxy.CountryFilterRule
import com.autopilot.android.domain.model.proxy.ProxyConfiguration
import com.autopilot.android.domain.model.proxy.ProxyHealthStatus
import com.autopilot.android.domain.model.proxy.ProxyProtocol
import com.autopilot.android.domain.validation.ProxyValidator
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import java.util.concurrent.TimeUnit

data class ProxiflyGeolocation(
    @SerializedName("country") val country: String? = null,
    @SerializedName("city") val city: String? = null
)

data class ProxiflyProxyItem(
    @SerializedName("proxy") val proxy: String? = null,
    @SerializedName("protocol") val protocol: String? = null,
    @SerializedName("ip") val ip: String? = null,
    @SerializedName("port") val port: Int? = null,
    @SerializedName("https") val https: Boolean? = null,
    @SerializedName("anonymity") val anonymity: String? = null,
    @SerializedName("score") val score: Int? = null,
    @SerializedName("geolocation") val geolocation: ProxiflyGeolocation? = null
)

data class ProxiflyFetchResult(
    val success: Boolean,
    val totalFetched: Int,
    val validProxies: List<ProxyConfiguration>,
    val filteredOutByCountry: Int,
    val duplicatesSkipped: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val errorMessage: String? = null
)

class ProxiflyProxySourceAdapter(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
    private val gson: Gson = Gson()
) {
    companion object {
        const val SOURCE_ID = "proxifly"
        const val SOURCE_NAME = "Proxifly Free Proxy List"
        const val URL_ALL = "https://cdn.jsdelivr.net/gh/proxifly/free-proxy-list@main/proxies/all/data.json"
        const val URL_HTTP = "https://cdn.jsdelivr.net/gh/proxifly/free-proxy-list@main/proxies/protocols/http/data.json"
        const val URL_HTTPS = "https://cdn.jsdelivr.net/gh/proxifly/free-proxy-list@main/proxies/protocols/https/data.json"
        const val URL_SOCKS5 = "https://cdn.jsdelivr.net/gh/proxifly/free-proxy-list@main/proxies/protocols/socks5/data.json"
    }

    suspend fun fetchProxies(
        protocolFilter: ProxyProtocol? = null,
        countryFilter: CountryFilterRule? = null,
        existingProxies: List<ProxyConfiguration> = emptyList()
    ): ProxiflyFetchResult = withContext(Dispatchers.IO) {
        val targetUrl = when (protocolFilter) {
            ProxyProtocol.HTTP -> URL_HTTP
            ProxyProtocol.HTTPS -> URL_HTTPS
            ProxyProtocol.SOCKS5 -> URL_SOCKS5
            null -> URL_ALL
        }

        val request = Request.Builder()
            .url(targetUrl)
            .header("User-Agent", "AutoPilot-Android/1.0.0 (ProxySync)")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext ProxiflyFetchResult(
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
                    return@withContext ProxiflyFetchResult(
                        success = false,
                        totalFetched = 0,
                        validProxies = emptyList(),
                        filteredOutByCountry = 0,
                        duplicatesSkipped = 0,
                        errorMessage = "Empty response body received from Proxifly"
                    )
                }

                val listType = object : TypeToken<List<ProxiflyProxyItem>>() {}.type
                val items: List<ProxiflyProxyItem> = gson.fromJson(bodyString, listType) ?: emptyList()

                val existingKeys = existingProxies.map { "${it.host.lowercase()}:${it.port}" }.toMutableSet()
                val validList = mutableListOf<ProxyConfiguration>()
                var filteredByCountry = 0
                var duplicates = 0

                for (item in items) {
                    val host = item.ip?.trim() ?: continue
                    val port = item.port ?: continue
                    if (!ProxyValidator.isValidPort(port)) continue
                    if (!ProxyValidator.isValidHost(host)) continue

                    val rawCountry = item.geolocation?.country
                    val countryCode = CountryCodeValidator.normalize(rawCountry)

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

                    val itemProto = when (item.protocol?.lowercase()) {
                        "socks5" -> ProxyProtocol.SOCKS5
                        "https" -> ProxyProtocol.HTTPS
                        else -> ProxyProtocol.HTTP
                    }

                    val config = ProxyConfiguration(
                        id = UUID.randomUUID().toString(),
                        name = "Proxifly ${itemProto.displayName} ${countryCode ?: ""}".trim(),
                        protocol = itemProto,
                        host = host,
                        port = port,
                        credentials = null,
                        region = countryCode,
                        notes = "Imported from Proxifly Free Proxy List (${item.geolocation?.city ?: "Unknown"})",
                        sourceId = SOURCE_ID,
                        enabled = true,
                        lastHealthStatus = ProxyHealthStatus.UNTESTED,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )

                    validList.add(config)
                }

                ProxiflyFetchResult(
                    success = true,
                    totalFetched = items.size,
                    validProxies = validList,
                    filteredOutByCountry = filteredByCountry,
                    duplicatesSkipped = duplicates
                )
            }
        } catch (e: Exception) {
            ProxiflyFetchResult(
                success = false,
                totalFetched = 0,
                validProxies = emptyList(),
                filteredOutByCountry = 0,
                duplicatesSkipped = 0,
                errorMessage = "Failed to fetch from Proxifly: ${e.message}"
            )
        }
    }
}
