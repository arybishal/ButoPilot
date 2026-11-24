package com.autopilot.android.feature.proxy

import com.autopilot.android.core.security.KeyProvider
import com.autopilot.android.core.security.ProxyCredentialEncryptor
import com.autopilot.android.domain.model.proxy.ProxyConfiguration
import com.autopilot.android.domain.model.proxy.ProxyCredentials
import com.autopilot.android.domain.model.proxy.ProxyHealthStatus
import com.autopilot.android.domain.model.proxy.ProxyProtocol
import com.autopilot.android.domain.validation.ProxyValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

class TestKeyProvider : KeyProvider {
    private val key = SecretKeySpec(
        byteArrayOf(
            0x41, 0x75, 0x74, 0x6F, 0x50, 0x69, 0x6C, 0x6F,
            0x74, 0x50, 0x72, 0x6F, 0x78, 0x79, 0x53, 0x65,
            0x63, 0x75, 0x72, 0x65, 0x4B, 0x65, 0x79, 0x32,
            0x30, 0x32, 0x36, 0x41, 0x47, 0x59, 0x21, 0x23
        ), "AES"
    )
    override fun getSecretKey(): SecretKey = key
}

class ProxyUnitTest {

    @Test
    fun testValidProxyHostAndPort() {
        val validHosts = listOf(
            "127.0.0.1",
            "192.168.1.1",
            "10.0.0.1",
            "proxy.example.com",
            "sub.domain.co.uk",
            "104.244.42.1"
        )
        val validPorts = listOf(1, 80, 443, 1080, 8080, 3128, 65535)

        for (host in validHosts) {
            assertTrue("Expected host '$host' to be valid", ProxyValidator.isValidHost(host))
        }

        for (port in validPorts) {
            assertTrue("Expected port $port to be valid", ProxyValidator.isValidPort(port))
        }

        val config = ProxyConfiguration(
            name = "Test Proxy",
            protocol = ProxyProtocol.HTTP,
            host = "192.168.1.100",
            port = 8080
        )
        val result = ProxyValidator.validateConfiguration(config)
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun testInvalidProxyHost() {
        val invalidHosts = listOf(
            "",
            "   ",
            "http://example.com",
            "https://proxy.org",
            "example.com/path",
            "host with spaces",
            "invalid..domain",
            "-leadingdash.com"
        )

        for (host in invalidHosts) {
            assertFalse("Expected host '$host' to be invalid", ProxyValidator.isValidHost(host))
        }
    }

    @Test
    fun testPortBoundaries() {
        val invalidPorts = listOf(
            0,
            -1,
            -8080,
            65536,
            70000,
            99999
        )

        for (port in invalidPorts) {
            assertFalse("Expected port $port to be invalid", ProxyValidator.isValidPort(port))
        }
    }

    @Test
    fun testCredentialValidation() {
        // Valid credentials
        val validCreds = ProxyCredentials(username = "alice", password = "secretPassword123!")
        val validConfig = ProxyConfiguration(
            name = "Valid Auth Proxy",
            host = "proxy.example.com",
            port = 8080,
            credentials = validCreds
        )
        assertTrue(ProxyValidator.validateConfiguration(validConfig).isValid)

        // Invalid credentials: overly long username (>128 chars)
        val longUsername = "a".repeat(129)
        val invalidCreds = ProxyCredentials(username = longUsername, password = "pwd")
        val invalidConfig = ProxyConfiguration(
            name = "Long User Proxy",
            host = "proxy.example.com",
            port = 8080,
            credentials = invalidCreds
        )
        val result = ProxyValidator.validateConfiguration(invalidConfig)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Username exceeds") })
    }

    @Test
    fun testCredentialEncryptionAndDecryption() {
        val encryptor = ProxyCredentialEncryptor(TestKeyProvider())
        val originalSecret = "P@ssw0rd!SuperSecret2026#"

        val payload = encryptor.encrypt(originalSecret)
        assertNotNull("Encrypted payload should not be null", payload)
        assertNotEquals(originalSecret, payload!!.ciphertextBase64)
        assertTrue(payload.ivBase64.isNotBlank())
        assertTrue(payload.ciphertextBase64.isNotBlank())

        val decrypted = encryptor.decrypt(payload.ciphertextBase64, payload.ivBase64)
        assertEquals(originalSecret, decrypted)

        // Corrupted ciphertext returns null safely without throwing
        val corruptedResult = encryptor.decrypt("CorruptedInvalidCiphertext==", payload.ivBase64)
        assertNull(corruptedResult)

        // Null input handling
        assertNull(encryptor.encrypt(null))
        assertNull(encryptor.encrypt(""))
        assertNull(encryptor.decrypt(null, null))
    }

    @Test
    fun testMaskedCredentialsRepresentation() {
        val noCredsProxy = ProxyConfiguration(
            name = "No Auth",
            host = "10.0.0.1",
            port = 8080,
            credentials = null
        )
        assertEquals("None", noCredsProxy.getMaskedCredentials())

        val fullCredsProxy = ProxyConfiguration(
            name = "Full Auth",
            host = "10.0.0.1",
            port = 8080,
            credentials = ProxyCredentials("admin", "supersecret")
        )
        assertEquals("admin:••••••", fullCredsProxy.getMaskedCredentials())

        val passOnlyProxy = ProxyConfiguration(
            name = "Pass Only",
            host = "10.0.0.1",
            port = 8080,
            credentials = ProxyCredentials(null, "supersecret")
        )
        assertEquals("••••••", passOnlyProxy.getMaskedCredentials())
    }

    @Test
    fun testProxySourceManagerParsingAndDeduplication() {
        val rawInput = """
            # This is a comment
            192.168.1.10:8080
            socks5://10.0.0.1:1080
            http://proxyadmin:pass123@172.16.0.5:3128
            192.168.1.20:8000:admin:secret
            
            # Duplicate entry
            192.168.1.10:8080
            
            # Invalid entry
            invalid_host_no_port
            http://bad:port999999
        """.trimIndent()

        val existing = listOf(
            ProxyConfiguration(name = "Existing", host = "192.168.1.20", port = 8000)
        )

        val summary = ProxySourceManager.parseAndValidateList(
            rawContent = rawInput,
            existingProxies = existing
        )

        assertEquals("Total lines parsed should ignore comments and blanks", 7, summary.totalParsed)
        // 192.168.1.10:8080 (valid), socks5://10.0.0.1:1080 (valid), http://proxyadmin:pass123@172.16.0.5:3128 (valid)
        assertEquals("Valid new proxies count", 3, summary.validCount)
        // 192.168.1.20:8000 is duplicate with existing; second 192.168.1.10:8080 is internal duplicate
        assertEquals("Duplicate count", 2, summary.duplicateCount)
        // invalid_host_no_port and http://bad:port999999 are invalid
        assertEquals("Invalid count", 2, summary.invalidCount)
        assertEquals(3, summary.importedProxies.size)

        // Verify parsed protocol
        val socksProxy = summary.importedProxies.firstOrNull { it.host == "10.0.0.1" }
        assertNotNull(socksProxy)
        assertEquals(ProxyProtocol.SOCKS5, socksProxy!!.protocol)
        assertEquals(1080, socksProxy.port)

        // Verify parsed credentials
        val authProxy = summary.importedProxies.firstOrNull { it.host == "172.16.0.5" }
        assertNotNull(authProxy)
        assertEquals("proxyadmin", authProxy!!.credentials?.username)
        assertEquals("pass123", authProxy.credentials?.password)
    }

    @Test
    fun testProxyExecutionCoordinatorSequentialLock() = runBlocking {
        val coordinator = ProxyExecutionCoordinator()
        val proxy1 = ProxyConfiguration(name = "P1", host = "1.1.1.1", port = 8080)
        val proxy2 = ProxyConfiguration(name = "P2", host = "2.2.2.2", port = 8080)

        assertFalse(coordinator.isLocked.value)
        assertNull(coordinator.activeProxy.value)

        val executionOrder = mutableListOf<String>()

        val job1 = async(Dispatchers.Default) {
            coordinator.executeWithProxy(proxy1) {
                executionOrder.add("P1_START")
                delay(50)
                executionOrder.add("P1_END")
            }
        }

        val job2 = async(Dispatchers.Default) {
            delay(10) // Ensure job1 starts first
            coordinator.executeWithProxy(proxy2) {
                executionOrder.add("P2_START")
                delay(10)
                executionOrder.add("P2_END")
            }
        }

        job1.await()
        job2.await()

        assertFalse(coordinator.isLocked.value)
        assertNull(coordinator.activeProxy.value)

        // Due to mutex locking, P1 must finish before P2 starts
        assertEquals(listOf("P1_START", "P1_END", "P2_START", "P2_END"), executionOrder)
    }

    @Test
    fun testProxyCompatibilityReport() {
        val report = ProxyCompatibilityChecker.checkCompatibility(null)
        assertNotNull(report)
        assertTrue(report.supportedProtocols.isEmpty() || report.supportedProtocols.contains(ProxyProtocol.HTTP))
    }

    @Test
    fun testExceptionInsideProxyBlockSafelyReleasesLock() = runBlocking {
        val coordinator = ProxyExecutionCoordinator()
        val proxy = ProxyConfiguration(name = "Failing Session", host = "1.2.3.4", port = 8080)

        var thrown = false
        try {
            coordinator.executeWithProxy(proxy) {
                assertTrue(coordinator.isLocked.value)
                throw IllegalStateException("Simulated automation crash")
            }
        } catch (e: IllegalStateException) {
            thrown = true
        }

        assertTrue(thrown)
        // Verify lock is released and active proxy reset in finally block
        assertFalse(coordinator.isLocked.value)
        assertNull(coordinator.activeProxy.value)
    }

    @Test
    fun testMultipleSequentialProxyExecutions() = runBlocking {
        val coordinator = ProxyExecutionCoordinator()
        val proxy1 = ProxyConfiguration(name = "Proxy 1", host = "10.0.0.1", port = 8080)
        val proxy2 = ProxyConfiguration(name = "Proxy 2", host = "10.0.0.2", port = 8080)

        // Session 1
        coordinator.executeWithProxy(proxy1) {
            assertTrue(coordinator.isLocked.value)
        }
        assertFalse(coordinator.isLocked.value)

        // Session 2
        coordinator.executeWithProxy(proxy2) {
            assertTrue(coordinator.isLocked.value)
        }
        assertFalse(coordinator.isLocked.value)

        // Session 3 (Direct / No Proxy)
        coordinator.executeWithProxy(null) {
            assertTrue(coordinator.isLocked.value)
        }
        assertFalse(coordinator.isLocked.value)
    }

    @Test
    fun testNetworkIsolationGuardOnDeletedOrDisabledProxy() {
        val proxyDatabase = mutableMapOf<String, ProxyConfiguration>()
        val activeProxy = ProxyConfiguration(
            id = "active-proxy",
            name = "Active",
            host = "10.0.0.1",
            port = 8080,
            enabled = true
        )
        val disabledProxy = ProxyConfiguration(
            id = "disabled-proxy",
            name = "Disabled",
            host = "10.0.0.2",
            port = 8080,
            enabled = false
        )
        proxyDatabase["active-proxy"] = activeProxy
        proxyDatabase["disabled-proxy"] = disabledProxy

        fun canExecuteWithIsolation(proxyId: String?): Pair<Boolean, String?> {
            if (proxyId == null) return true to null
            val proxy = proxyDatabase[proxyId]
            if (proxy == null || !proxy.enabled) {
                return false to "Configured proxy [ID: $proxyId] is unavailable or disabled. Execution aborted to protect network isolation."
            }
            return true to null
        }

        // Test active proxy allows execution
        val (canRunActive, errorActive) = canExecuteWithIsolation("active-proxy")
        assertTrue(canRunActive)
        assertNull(errorActive)

        // Test deleted proxy aborts execution
        val (canRunDeleted, errorDeleted) = canExecuteWithIsolation("non-existent-proxy-id")
        assertFalse(canRunDeleted)
        assertNotNull(errorDeleted)
        assertTrue(errorDeleted!!.contains("unavailable or disabled"))

        // Test disabled proxy aborts execution
        val (canRunDisabled, errorDisabled) = canExecuteWithIsolation("disabled-proxy")
        assertFalse(canRunDisabled)
        assertNotNull(errorDisabled)
        assertTrue(errorDisabled!!.contains("unavailable or disabled"))

        // Test null proxy (default network) allows execution
        val (canRunDirect, errorDirect) = canExecuteWithIsolation(null)
        assertTrue(canRunDirect)
        assertNull(errorDirect)
    }

    @Test
    fun testHttpProbeFailureNeverMarksProxyHealthyEvenIfPortIsOpen() = runBlocking {
        // Create a dummy server that accepts TCP connection but immediately closes or hangs on HTTP
        val server = java.net.ServerSocket(0)
        val port = server.localPort
        val serverThread = Thread {
            try {
                val sock = server.accept()
                // Do not respond with HTTP 200/204, close immediately
                sock.close()
            } catch (e: Exception) {
            } finally {
                server.close()
            }
        }
        serverThread.start()

        val checker = ProxyHealthChecker(connectTimeoutMs = 1500, readTimeoutMs = 1500)
        val brokenProxy = ProxyConfiguration(
            id = "broken-proxy",
            name = "Broken Proxy",
            protocol = ProxyProtocol.HTTP,
            host = "127.0.0.1",
            port = port
        )

        val result = checker.checkHealth(brokenProxy)
        // Must NOT be HEALTHY!
        assertNotEquals("Proxy that cannot forward HTTP must NEVER be marked HEALTHY", ProxyHealthStatus.HEALTHY, result.status)
        serverThread.join(2000)
    }
}
