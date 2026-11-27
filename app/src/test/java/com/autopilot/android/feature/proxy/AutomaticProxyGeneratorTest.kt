package com.autopilot.android.feature.proxy

import com.autopilot.android.domain.model.proxy.ProxyConfiguration
import com.autopilot.android.domain.model.proxy.ProxyHealthStatus
import com.autopilot.android.domain.model.proxy.ProxyProtocol
import com.autopilot.android.domain.model.proxy.ProxyTestResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticProxyGeneratorTest {

    private val generator = AutomaticProxyGenerator()

    private fun makeProxy(
        host: String,
        port: Int = 8080,
        protocol: ProxyProtocol = ProxyProtocol.HTTP,
        region: String? = "US",
        notes: String = ""
    ) = ProxyConfiguration(
        name = "Test $host:$port",
        protocol = protocol,
        host = host,
        port = port,
        region = region,
        notes = notes
    )

    @Test
    fun `quantity is clamped between 1 and 500`() {
        val candidates = (1..600).map { i ->
            makeProxy(host = "10.0.${i / 256}.${i % 256}", port = 8080, region = "US")
        }

        val zeroResult = generator.selectAndRankProxies(
            candidates = candidates,
            request = ProxyGenerationRequest(quantity = 0)
        )
        assertEquals(1, zeroResult.size)

        val negativeResult = generator.selectAndRankProxies(
            candidates = candidates,
            request = ProxyGenerationRequest(quantity = -25)
        )
        assertEquals(1, negativeResult.size)

        val overMaxResult = generator.selectAndRankProxies(
            candidates = candidates,
            request = ProxyGenerationRequest(quantity = 750)
        )
        assertEquals(500, overMaxResult.size)

        val exactResult = generator.selectAndRankProxies(
            candidates = candidates,
            request = ProxyGenerationRequest(quantity = 120)
        )
        assertEquals(120, exactResult.size)
    }

    @Test
    fun `country filter restricts proxies to selected countries`() {
        val candidates = listOf(
            makeProxy("1.1.1.1", region = "US"),
            makeProxy("2.2.2.2", region = "DE"),
            makeProxy("3.3.3.3", region = "ID"),
            makeProxy("4.4.4.4", region = null)
        )

        val result = generator.selectAndRankProxies(
            candidates = candidates,
            request = ProxyGenerationRequest(
                countries = setOf("us", "de"),
                tier = ProxyTier.TOP_TIER,
                quantity = 50
            )
        )

        assertEquals(2, result.size)
        assertTrue(result.all { it.region == "US" || it.region == "DE" })
    }

    @Test
    fun `deduplicates against existing proxies and within candidates`() {
        val existing = listOf(
            makeProxy("1.1.1.1", port = 8080, region = "US")
        )
        val candidates = listOf(
            makeProxy("1.1.1.1", port = 8080, region = "US"),
            makeProxy("2.2.2.2", port = 3128, region = "US"),
            makeProxy("2.2.2.2", port = 3128, region = "US"),
            makeProxy("3.3.3.3", port = 80, region = "GB")
        )

        val result = generator.selectAndRankProxies(
            candidates = candidates,
            request = ProxyGenerationRequest(quantity = 50),
            existingProxies = existing
        )

        assertEquals(2, result.size)
        assertFalse(result.any { it.host == "1.1.1.1" })
        assertEquals(setOf("2.2.2.2", "3.3.3.3"), result.map { it.host }.toSet())
    }

    @Test
    fun `tier ranking prioritizes matching tier proxies`() {
        val topProxy = makeProxy(
            host = "10.0.0.1",
            protocol = ProxyProtocol.HTTPS,
            region = "US",
            notes = "Uptime: 95%, Anonymity: elite"
        )
        val midProxy = makeProxy(
            host = "10.0.0.2",
            protocol = ProxyProtocol.HTTP,
            region = "BR",
            notes = "Uptime: 60%, Anonymity: anonymous"
        )
        val lowProxy = makeProxy(
            host = "10.0.0.3",
            protocol = ProxyProtocol.HTTP,
            region = "ID",
            notes = "Uptime: 25%, Anonymity: transparent"
        )
        val candidates = listOf(lowProxy, midProxy, topProxy)

        val topRanked = generator.selectAndRankProxies(
            candidates = candidates,
            request = ProxyGenerationRequest(tier = ProxyTier.TOP_TIER, quantity = 3)
        )
        assertEquals("10.0.0.1", topRanked.first().host)

        val midRanked = generator.selectAndRankProxies(
            candidates = candidates,
            request = ProxyGenerationRequest(tier = ProxyTier.MID_TIER, quantity = 3)
        )
        assertEquals("10.0.0.2", midRanked.first().host)

        val lowRanked = generator.selectAndRankProxies(
            candidates = candidates,
            request = ProxyGenerationRequest(tier = ProxyTier.LOW_TIER, quantity = 3)
        )
        assertEquals("10.0.0.3", lowRanked.first().host)
    }

    @Test
    fun `protocol filter excludes non-matching protocols`() {
        val candidates = listOf(
            makeProxy("1.1.1.1", protocol = ProxyProtocol.HTTP, region = "US"),
            makeProxy("2.2.2.2", protocol = ProxyProtocol.SOCKS5, region = "US"),
            makeProxy("3.3.3.3", protocol = ProxyProtocol.HTTPS, region = "US")
        )

        val result = generator.selectAndRankProxies(
            candidates = candidates,
            request = ProxyGenerationRequest(
                tier = ProxyTier.TOP_TIER,
                quantity = 10,
                protocolFilter = ProxyProtocol.SOCKS5
            )
        )

        assertEquals(1, result.size)
        assertEquals("2.2.2.2", result.first().host)
    }

    @Test
    fun `tier country lists are non-empty and distinct`() {
        val top = AutomaticProxyGenerator.countryCodesForTier(ProxyTier.TOP_TIER)
        val mid = AutomaticProxyGenerator.countryCodesForTier(ProxyTier.MID_TIER)
        val low = AutomaticProxyGenerator.countryCodesForTier(ProxyTier.LOW_TIER)

        assertTrue(top.isNotEmpty())
        assertTrue(mid.isNotEmpty())
        assertTrue(low.isNotEmpty())
        assertTrue(top.intersect(mid).isEmpty())
        assertTrue(top.intersect(low).isEmpty())
        assertTrue(mid.intersect(low).isEmpty())
    }

    @Test
    fun `keepHealthyProxies keeps only healthy results and stamps status`() {
        val healthy = makeProxy("1.1.1.1")
        val unhealthy = makeProxy("2.2.2.2")
        val timeout = makeProxy("3.3.3.3")
        val untested = makeProxy("4.4.4.4")

        val results = mapOf(
            healthy.id to ProxyTestResult(healthy.id, ProxyHealthStatus.HEALTHY, 120L),
            unhealthy.id to ProxyTestResult(unhealthy.id, ProxyHealthStatus.UNHEALTHY, 50L),
            timeout.id to ProxyTestResult(timeout.id, ProxyHealthStatus.TIMEOUT, 5000L)
        )

        val kept = keepHealthyProxies(listOf(healthy, unhealthy, timeout, untested), results)

        assertEquals(1, kept.size)
        assertEquals("1.1.1.1", kept.first().host)
        assertEquals(ProxyHealthStatus.HEALTHY, kept.first().lastHealthStatus)
        assertEquals(120L, kept.first().lastLatencyMs)
    }
}
