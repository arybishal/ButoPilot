package com.autopilot.android.feature.traffic

import java.net.InetAddress
import org.junit.Assert.*
import org.junit.Test

class TrafficValidationTest {

    @Test
    fun normalize_addsHttpsAndKeepsPublicUrl() {
        assertEquals("https://example.com/pricing", TrafficUrlValidator.normalize("example.com/pricing"))
        assertEquals(
            "https://example.com:8443/a?b=1",
            TrafficUrlValidator.normalize("https://example.com:8443/a?b=1")
        )
    }

    @Test
    fun normalize_allowsHttpButRejectsCredentialsAndFragments() {
        assertEquals("http://example.com", TrafficUrlValidator.normalize("http://example.com"))
        assertNull(TrafficUrlValidator.normalize("https://user:pass@example.com"))
        assertNull(TrafficUrlValidator.normalize("https://example.com#frag"))
    }

    @Test
    fun normalize_blocksLoopbackPrivateAndMetadataTargets() {
        listOf(
            "localhost", "127.0.0.1", "10.0.0.5", "172.16.4.4", "192.168.1.1",
            "169.254.169.254", "metadata.google.internal", "100.64.1.1", "[::1]"
        ).forEach { host ->
            assertNull("expected $host to be blocked", TrafficUrlValidator.normalize("https://$host/test"))
        }
    }

    @Test
    fun blockedAddresses_excludesPrivateAndAllowsPublic() {
        assertTrue(TrafficUrlValidator.isBlockedAddress(InetAddress.getByName("127.0.0.1")))
        assertTrue(TrafficUrlValidator.isBlockedAddress(InetAddress.getByName("169.254.169.254")))
        assertFalse(TrafficUrlValidator.isBlockedAddress(InetAddress.getByName("93.184.216.34")))
    }

    @Test
    fun parsePagePaths_acceptsAbsolutePathsAndReportsBadOnes() {
        assertEquals(Pair(listOf("/a", "/b/c"), null), TrafficUrlValidator.parsePagePaths("/a, /b/c"))
        val (paths, error) = TrafficUrlValidator.parsePagePaths("https://evil.com")
        assertTrue(paths.isEmpty())
        assertNotNull(error)
    }

    @Test
    fun rateLimiterSpacesSlotsWithoutCatchingUpBursts() {
        assertEquals(200L, intervalMs(300))
        assertEquals(0L, delayForSlot(now = 10_000, nextSlotAt = 0, eventsPerMinute = 300))
        assertEquals(200L, delayForSlot(now = 10_000, nextSlotAt = 10_200, eventsPerMinute = 300))
        assertEquals(0L, delayForSlot(now = 11_000, nextSlotAt = 10_200, eventsPerMinute = 300))
    }

    @Test
    fun counters_derivePendingAndProgress() {
        val run = TrafficRun(
            id = "r1",
            websiteUrl = "https://example.com",
            plannedEvents = 100,
            mode = TrafficMode.PAGE_REQUEST,
            speed = TrafficSpeed.NORMAL,
            concurrency = 1,
            pagePaths = emptyList(),
            status = TrafficStatus.RUNNING,
            submitted = 40,
            failed = 10
        )
        assertEquals(50, run.pending)
        assertEquals(0.5f, run.progress, 0.001f)
        assertTrue(run.isActive)
    }

    @Test
    fun reportExport_containsCountersAndEscapesCsv() {
        val run = TrafficRun(
            id = "r1",
            websiteUrl = "https://example.com",
            plannedEvents = 10,
            mode = TrafficMode.PAGE_REQUEST,
            speed = TrafficSpeed.NORMAL,
            concurrency = 1,
            pagePaths = listOf("/a,b"),
            status = TrafficStatus.COMPLETED,
            submitted = 9,
            failed = 1,
            lastError = "HTTP 500"
        )
        val json = TrafficReportExporter.toJson(run)
        assertTrue(json.contains("\"submitted\": 9"))
        assertTrue(json.contains("\"failed\": 1"))

        val csv = TrafficReportExporter.toCsv(run)
        assertTrue(csv.contains("successful_submissions,9"))
        assertTrue(csv.contains("\"/a,b\""))
        assertTrue(csv.contains("not GA4-recorded events"))
    }
}
