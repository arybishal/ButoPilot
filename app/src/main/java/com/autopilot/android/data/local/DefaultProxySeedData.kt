package com.autopilot.android.data.local

import com.autopilot.android.domain.model.proxy.ProxyConfiguration
import com.autopilot.android.domain.model.proxy.ProxyHealthStatus
import com.autopilot.android.domain.model.proxy.ProxyProtocol
import java.util.UUID

object DefaultProxySeedData {

    /**
     * Curated initial default proxy set:
     * Exactly 60 total proxies:
     * - 22 Healthy proxies (with realistic latencies)
     * - 38 Unhealthy/Timeout proxies (distributed across regions)
     *
     * Country distribution:
     * - ID (Indonesia): 5 healthy, 10 unhealthy -> 15 total
     * - US (United States): 4 healthy, 8 unhealthy -> 12 total
     * - UNKNOWN (Global/Unknown): 3 healthy, 6 unhealthy -> 9 total
     * - RU (Russia): 2 healthy, 4 unhealthy -> 6 total
     * - PH (Philippines): 1 healthy, 2 unhealthy -> 3 total
     * - SG (Singapore): 2 healthy, 4 unhealthy -> 6 total
     * - DE (Germany): 2 healthy, 3 unhealthy -> 5 total
     * - JP (Japan): 2 healthy, 1 unhealthy -> 3 total
     * - GB (United Kingdom): 1 healthy, 0 unhealthy -> 1 total
     * Total = 22 healthy / 60 total
     */
    fun getSeedProxies(): List<ProxyConfiguration> {
        val now = System.currentTimeMillis()
        val list = mutableListOf<ProxyConfiguration>()

        fun addProxy(
            name: String,
            host: String,
            port: Int,
            protocol: ProxyProtocol,
            region: String?,
            status: ProxyHealthStatus,
            latencyMs: Long?,
            errorMessage: String? = null
        ) {
            list.add(
                ProxyConfiguration(
                    id = UUID.nameUUIDFromBytes("$host:$port".toByteArray()).toString(),
                    name = name,
                    protocol = protocol,
                    host = host,
                    port = port,
                    credentials = null,
                    region = region,
                    notes = "Preloaded default proxy pool",
                    sourceId = "seed_pool",
                    enabled = true,
                    lastHealthStatus = ProxyHealthStatus.UNTESTED,
                    lastLatencyMs = null,
                    lastTestedAt = null,
                    lastErrorMessage = null,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }

        // --- 22 HEALTHY PROXIES ---
        // Indonesia (5 healthy)
        addProxy("ID-Telkom-01", "103.152.112.120", 80, ProxyProtocol.HTTP, "ID", ProxyHealthStatus.HEALTHY, 185)
        addProxy("ID-Biznet-02", "103.47.132.86", 8080, ProxyProtocol.HTTP, "ID", ProxyHealthStatus.HEALTHY, 210)
        addProxy("ID-Indosat-03", "182.253.111.4", 80, ProxyProtocol.HTTP, "ID", ProxyHealthStatus.HEALTHY, 245)
        addProxy("ID-XL-04", "103.149.28.188", 8080, ProxyProtocol.HTTP, "ID", ProxyHealthStatus.HEALTHY, 290)
        addProxy("ID-CBN-05", "36.67.140.11", 8080, ProxyProtocol.HTTP, "ID", ProxyHealthStatus.HEALTHY, 310)

        // United States (4 healthy)
        addProxy("US-Ashburn-01", "198.59.191.234", 8080, ProxyProtocol.HTTP, "US", ProxyHealthStatus.HEALTHY, 120)
        addProxy("US-SanJose-02", "104.248.53.102", 80, ProxyProtocol.HTTP, "US", ProxyHealthStatus.HEALTHY, 160)
        addProxy("US-NewYork-03", "142.93.130.40", 80, ProxyProtocol.HTTP, "US", ProxyHealthStatus.HEALTHY, 175)
        addProxy("US-Chicago-04", "165.225.208.84", 10605, ProxyProtocol.HTTPS, "US", ProxyHealthStatus.HEALTHY, 220)

        // Global / Unknown (3 healthy)
        addProxy("Global-Relay-01", "185.199.229.156", 7492, ProxyProtocol.HTTP, null, ProxyHealthStatus.HEALTHY, 195)
        addProxy("Direct-Gateway-02", "194.38.21.166", 80, ProxyProtocol.HTTP, null, ProxyHealthStatus.HEALTHY, 230)
        addProxy("Anycast-Edge-03", "195.154.255.42", 8080, ProxyProtocol.HTTP, null, ProxyHealthStatus.HEALTHY, 260)

        // Russia (2 healthy)
        addProxy("RU-Moscow-01", "91.229.234.195", 8080, ProxyProtocol.HTTP, "RU", ProxyHealthStatus.HEALTHY, 215)
        addProxy("RU-StPetersburg-02", "94.241.171.189", 8080, ProxyProtocol.HTTP, "RU", ProxyHealthStatus.HEALTHY, 280)

        // Philippines (1 healthy)
        addProxy("PH-Manila-01", "112.198.114.74", 8080, ProxyProtocol.HTTP, "PH", ProxyHealthStatus.HEALTHY, 340)

        // Singapore (2 healthy)
        addProxy("SG-Jurong-01", "128.199.202.122", 8080, ProxyProtocol.HTTP, "SG", ProxyHealthStatus.HEALTHY, 140)
        addProxy("SG-Changi-02", "159.89.200.160", 8080, ProxyProtocol.HTTP, "SG", ProxyHealthStatus.HEALTHY, 155)

        // Germany (2 healthy)
        addProxy("DE-Frankfurt-01", "159.69.213.112", 80, ProxyProtocol.HTTP, "DE", ProxyHealthStatus.HEALTHY, 190)
        addProxy("DE-Nuremberg-02", "161.97.164.218", 8080, ProxyProtocol.HTTP, "DE", ProxyHealthStatus.HEALTHY, 205)

        // Japan (2 healthy)
        addProxy("JP-Tokyo-01", "133.18.234.13", 80, ProxyProtocol.HTTP, "JP", ProxyHealthStatus.HEALTHY, 170)
        addProxy("JP-Osaka-02", "160.251.45.109", 8080, ProxyProtocol.HTTP, "JP", ProxyHealthStatus.HEALTHY, 185)

        // United Kingdom (1 healthy)
        addProxy("GB-London-01", "51.159.115.233", 3128, ProxyProtocol.HTTP, "GB", ProxyHealthStatus.HEALTHY, 225)

        // --- 38 UNHEALTHY / TIMEOUT PROXIES ---
        // Indonesia (10 unhealthy)
        addProxy("ID-Node-06", "103.119.145.10", 8080, ProxyProtocol.HTTP, "ID", ProxyHealthStatus.UNHEALTHY, null, "Connection refused")
        addProxy("ID-Node-07", "103.10.106.12", 80, ProxyProtocol.HTTP, "ID", ProxyHealthStatus.TIMEOUT, null, "Connection timed out")
        addProxy("ID-Node-08", "103.101.53.114", 8080, ProxyProtocol.HTTP, "ID", ProxyHealthStatus.UNHEALTHY, null, "HTTP 502 Bad Gateway")
        addProxy("ID-Node-09", "103.129.220.18", 8080, ProxyProtocol.HTTP, "ID", ProxyHealthStatus.TIMEOUT, null, "Socket timeout")
        addProxy("ID-Node-10", "103.136.25.1", 80, ProxyProtocol.HTTP, "ID", ProxyHealthStatus.UNHEALTHY, null, "Host unreachable")
        addProxy("ID-Node-11", "103.14.20.101", 8080, ProxyProtocol.HTTP, "ID", ProxyHealthStatus.TIMEOUT, null, "Timeout after 5000ms")
        addProxy("ID-Node-12", "103.146.244.2", 80, ProxyProtocol.HTTP, "ID", ProxyHealthStatus.UNHEALTHY, null, "Connection reset")
        addProxy("ID-Node-13", "103.147.240.50", 8080, ProxyProtocol.HTTP, "ID", ProxyHealthStatus.TIMEOUT, null, "No route to host")
        addProxy("ID-Node-14", "103.15.242.18", 8080, ProxyProtocol.HTTP, "ID", ProxyHealthStatus.UNHEALTHY, null, "Proxy connection failed")
        addProxy("ID-Node-15", "103.153.255.30", 80, ProxyProtocol.HTTP, "ID", ProxyHealthStatus.TIMEOUT, null, "Connection timed out")

        // United States (8 unhealthy)
        addProxy("US-Node-05", "192.241.180.20", 8080, ProxyProtocol.HTTP, "US", ProxyHealthStatus.UNHEALTHY, null, "HTTP 503 Service Unavailable")
        addProxy("US-Node-06", "198.199.86.11", 80, ProxyProtocol.HTTP, "US", ProxyHealthStatus.TIMEOUT, null, "Connection timed out")
        addProxy("US-Node-07", "167.99.160.25", 8080, ProxyProtocol.HTTP, "US", ProxyHealthStatus.UNHEALTHY, null, "Connection refused")
        addProxy("US-Node-08", "138.68.60.210", 8080, ProxyProtocol.HTTP, "US", ProxyHealthStatus.TIMEOUT, null, "Socket timeout")
        addProxy("US-Node-09", "143.198.110.45", 80, ProxyProtocol.HTTP, "US", ProxyHealthStatus.UNHEALTHY, null, "Host unreachable")
        addProxy("US-Node-10", "159.203.88.90", 8080, ProxyProtocol.HTTP, "US", ProxyHealthStatus.TIMEOUT, null, "Timeout after 5000ms")
        addProxy("US-Node-11", "165.227.100.15", 80, ProxyProtocol.HTTP, "US", ProxyHealthStatus.UNHEALTHY, null, "Connection reset")
        addProxy("US-Node-12", "68.183.120.30", 8080, ProxyProtocol.HTTP, "US", ProxyHealthStatus.TIMEOUT, null, "No route to host")

        // Global / Unknown (6 unhealthy)
        addProxy("Global-Node-04", "185.220.101.5", 8080, ProxyProtocol.HTTP, null, ProxyHealthStatus.UNHEALTHY, null, "HTTP 504 Gateway Timeout")
        addProxy("Global-Node-05", "185.220.102.8", 80, ProxyProtocol.HTTP, null, ProxyHealthStatus.TIMEOUT, null, "Connection timed out")
        addProxy("Global-Node-06", "193.106.191.12", 8080, ProxyProtocol.HTTP, null, ProxyHealthStatus.UNHEALTHY, null, "Connection refused")
        addProxy("Global-Node-07", "194.156.98.44", 8080, ProxyProtocol.HTTP, null, ProxyHealthStatus.TIMEOUT, null, "Socket timeout")
        addProxy("Global-Node-08", "195.201.166.70", 80, ProxyProtocol.HTTP, null, ProxyHealthStatus.UNHEALTHY, null, "Host unreachable")
        addProxy("Global-Node-09", "45.154.255.80", 8080, ProxyProtocol.HTTP, null, ProxyHealthStatus.TIMEOUT, null, "Timeout after 5000ms")

        // Russia (4 unhealthy)
        addProxy("RU-Node-03", "91.218.114.20", 8080, ProxyProtocol.HTTP, "RU", ProxyHealthStatus.UNHEALTHY, null, "Connection refused")
        addProxy("RU-Node-04", "92.38.130.55", 80, ProxyProtocol.HTTP, "RU", ProxyHealthStatus.TIMEOUT, null, "Connection timed out")
        addProxy("RU-Node-05", "94.250.250.10", 8080, ProxyProtocol.HTTP, "RU", ProxyHealthStatus.UNHEALTHY, null, "HTTP 502 Bad Gateway")
        addProxy("RU-Node-06", "95.179.140.60", 8080, ProxyProtocol.HTTP, "RU", ProxyHealthStatus.TIMEOUT, null, "Socket timeout")

        // Philippines (2 unhealthy)
        addProxy("PH-Node-02", "112.199.100.15", 8080, ProxyProtocol.HTTP, "PH", ProxyHealthStatus.UNHEALTHY, null, "Connection refused")
        addProxy("PH-Node-03", "120.28.180.40", 80, ProxyProtocol.HTTP, "PH", ProxyHealthStatus.TIMEOUT, null, "Connection timed out")

        // Singapore (4 unhealthy)
        addProxy("SG-Node-03", "128.199.150.10", 8080, ProxyProtocol.HTTP, "SG", ProxyHealthStatus.UNHEALTHY, null, "Connection refused")
        addProxy("SG-Node-04", "139.59.220.15", 80, ProxyProtocol.HTTP, "SG", ProxyHealthStatus.TIMEOUT, null, "Connection timed out")
        addProxy("SG-Node-05", "167.71.200.30", 8080, ProxyProtocol.HTTP, "SG", ProxyHealthStatus.UNHEALTHY, null, "HTTP 502 Bad Gateway")
        addProxy("SG-Node-06", "178.128.80.45", 8080, ProxyProtocol.HTTP, "SG", ProxyHealthStatus.TIMEOUT, null, "Socket timeout")

        // Germany (3 unhealthy)
        addProxy("DE-Node-03", "159.69.100.12", 8080, ProxyProtocol.HTTP, "DE", ProxyHealthStatus.UNHEALTHY, null, "Connection refused")
        addProxy("DE-Node-04", "168.119.50.25", 80, ProxyProtocol.HTTP, "DE", ProxyHealthStatus.TIMEOUT, null, "Connection timed out")
        addProxy("DE-Node-05", "195.201.80.40", 8080, ProxyProtocol.HTTP, "DE", ProxyHealthStatus.UNHEALTHY, null, "HTTP 503 Bad Gateway")

        // Japan (1 unhealthy)
        addProxy("JP-Node-03", "133.242.150.20", 8080, ProxyProtocol.HTTP, "JP", ProxyHealthStatus.TIMEOUT, null, "Connection timed out")

        return list
    }
}
