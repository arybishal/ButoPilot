package com.autopilot.android.feature.proxy

import com.autopilot.android.domain.model.proxy.ProxyHealthStatus
import com.autopilot.android.domain.model.proxy.ProxyTestResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyRoutingVerificationTest {

    @Test
    fun exitLocationIsShownAsCountryAndIp() {
        val result = ProxyTestResult(
            proxyId = "p1",
            status = ProxyHealthStatus.HEALTHY,
            latencyMs = 120L,
            exitIp = "203.0.113.7",
            exitCountry = "US"
        )
        assertEquals("US (203.0.113.7)", result.locationLabel)
    }

    @Test
    fun proxyMatchingDeviceIpIsALeak() {
        assertTrue(isDeviceIpLeak("203.0.113.7", "203.0.113.7"))
        assertTrue(isDeviceIpLeak("203.0.113.7", " 203.0.113.7 "))
        assertFalse(isDeviceIpLeak("203.0.113.7", "198.51.100.2"))
        assertFalse(isDeviceIpLeak("203.0.113.7", null))
    }
}
