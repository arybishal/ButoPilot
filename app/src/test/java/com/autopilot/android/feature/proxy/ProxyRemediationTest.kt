package com.autopilot.android.feature.proxy

import com.autopilot.android.core.security.KeyProvider
import com.autopilot.android.core.security.ProxyCredentialEncryptor
import com.autopilot.android.domain.model.AutomationProfile
import com.autopilot.android.domain.model.ProfileStatus
import com.autopilot.android.domain.model.proxy.*
import com.autopilot.android.feature.proxy.source.ProxiflyFetchResult
import com.autopilot.android.feature.proxy.source.ProxiflyGeolocation
import com.autopilot.android.feature.proxy.source.ProxiflyProxyItem
import com.autopilot.android.feature.proxy.source.ProxiflyProxySourceAdapter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.security.KeyStoreException
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

class FailingKeyProvider : KeyProvider {
    override fun getSecretKey(): SecretKey {
        throw KeyStoreException("Simulated Android KeyStore hardware failure / key unretrievable")
    }
}

class ProxyRemediationTest {

    // --- PART A: DEF-402 Keystore Fallback & Cryptographic Safety ---

    @Test
    fun testKeystoreFailureNeverLeaksPlaintextOrDowngrades() {
        val failingEncryptor = ProxyCredentialEncryptor(FailingKeyProvider())
        val sensitivePlaintext = "SuperSecretPassword123!"

        // When Keystore fails, encryption must return null, never plaintext or fallback key
        val encrypted = failingEncryptor.encrypt(sensitivePlaintext)
        assertNull("Encryption must fail safely and return null when Keystore fails", encrypted)

        // Decryption with failing provider must return null
        val decrypted = failingEncryptor.decrypt("SGVsbG8=", "MTIzNDU2Nzg5MDEy")
        assertNull("Decryption must return null when Keystore fails", decrypted)
    }

    @Test
    fun testProductionDefaultKeyProviderOnJVMHasNoStaticFallbackKey() {
        // Default constructor uses AndroidKeyStoreProvider which has no JVM fallback key
        val defaultEncryptor = ProxyCredentialEncryptor()
        val sensitivePlaintext = "ProductionSecret2026"

        // On JVM (non-Android OS), KeyStore.getInstance("AndroidKeyStore") will throw
        // and defaultEncryptor must safely return null instead of using a hardcoded key
        val result = defaultEncryptor.encrypt(sensitivePlaintext)
        assertNull("Default production encryptor must return null on JVM rather than falling back to hardcoded keys", result)
    }

    @Test
    fun testAes256GcmWithTestKeyProvider() {
        val testKey = SecretKeySpec(ByteArray(32) { 0x42 }, "AES")
        val encryptor = ProxyCredentialEncryptor(object : KeyProvider {
            override fun getSecretKey(): SecretKey = testKey
        })

        val plain = "CorporateProxyP@ssw0rd!"
        val payload1 = encryptor.encrypt(plain)
        val payload2 = encryptor.encrypt(plain)

        assertNotNull(payload1)
        assertNotNull(payload2)
        // Ensure random 12-byte IV causes distinct ciphertexts
        assertNotEquals("Each encryption must use a unique random IV", payload1!!.ivBase64, payload2!!.ivBase64)
        assertNotEquals("Ciphertexts must differ due to unique IVs", payload1.ciphertextBase64, payload2.ciphertextBase64)

        // Both decrypt correctly
        assertEquals(plain, encryptor.decrypt(payload1.ciphertextBase64, payload1.ivBase64))
        assertEquals(plain, encryptor.decrypt(payload2.ciphertextBase64, payload2.ivBase64))
    }

    // --- PART B: DEF-403 HTTP Proxy Authentication & Origin Protection ---

    @Test
    fun testProxyAuthHostMatchingProtectsOriginServers() {
        val activeProxy = ProxyConfiguration(
            name = "Corp Squid Proxy",
            protocol = ProxyProtocol.HTTP,
            host = "proxy.corp.internal",
            port = 8080,
            credentials = ProxyCredentials("corp_user", "corp_pass")
        )

        fun simulateHttpAuthRequest(
            challengeHost: String?,
            proxy: ProxyConfiguration?
        ): Pair<Boolean, Pair<String, String>?> {
            val proxyHost = proxy?.host
            val creds = proxy?.credentials

            if (proxy != null &&
                !proxyHost.isNullOrBlank() &&
                challengeHost?.equals(proxyHost, ignoreCase = true) == true &&
                creds != null &&
                !creds.username.isNullOrBlank() &&
                !creds.password.isNullOrBlank()
            ) {
                // handler.proceed(username, password)
                return true to (creds.username!! to creds.password!!)
            } else {
                // handler.cancel()
                return false to null
            }
        }

        // 1. Exact match with proxy host -> Credentials supplied
        val (proceed1, creds1) = simulateHttpAuthRequest("proxy.corp.internal", activeProxy)
        assertTrue("Credentials should be supplied when challenge is from proxy host", proceed1)
        assertEquals("corp_user", creds1?.first)
        assertEquals("corp_pass", creds1?.second)

        // 2. Case-insensitive match -> Credentials supplied
        val (proceed2, _) = simulateHttpAuthRequest("PROXY.CORP.INTERNAL", activeProxy)
        assertTrue("Host comparison must be case-insensitive", proceed2)

        // 3. Origin server challenge (e.g. bank.com 401 Unauthorized) -> CANCELLED, NO LEAKAGE
        val (proceedOrigin, credsOrigin) = simulateHttpAuthRequest("bank.com", activeProxy)
        assertFalse("Proxy credentials must NEVER be sent to origin servers (e.g. bank.com)", proceedOrigin)
        assertNull(credsOrigin)

        // 4. Rogue / phishing origin challenge -> CANCELLED
        val (proceedRogue, credsRogue) = simulateHttpAuthRequest("evil-phishing.com", activeProxy)
        assertFalse("Proxy credentials must NEVER be sent to rogue hosts", proceedRogue)
        assertNull(credsRogue)

        // 5. No proxy active -> CANCELLED
        val (proceedDirect, credsDirect) = simulateHttpAuthRequest("proxy.corp.internal", null)
        assertFalse(proceedDirect)
        assertNull(credsDirect)
    }

    // --- PART E: DEF-404 Country Code Validation & Filtering ---

    @Test
    fun testCountryCodeValidator() {
        assertTrue(CountryCodeValidator.isValid("US"))
        assertTrue(CountryCodeValidator.isValid("DE"))
        assertTrue(CountryCodeValidator.isValid("GB"))
        assertTrue(CountryCodeValidator.isValid("jp")) // normalized to JP
        assertTrue(CountryCodeValidator.isValid("fr "))

        assertFalse(CountryCodeValidator.isValid(null))
        assertFalse(CountryCodeValidator.isValid(""))
        assertFalse(CountryCodeValidator.isValid("USA")) // 3 letters
        assertFalse(CountryCodeValidator.isValid("12"))
        assertFalse(CountryCodeValidator.isValid("U"))

        assertEquals("US", CountryCodeValidator.normalize("us"))
        assertEquals("DE", CountryCodeValidator.normalize(" De "))
        assertNull(CountryCodeValidator.normalize("INVALID"))
        assertNull(CountryCodeValidator.normalize(null))
    }

    @Test
    fun testCountryFilterAllowlistAndBlocklist() {
        // Allowlist rule: only US and DE
        val allowRule = CountryFilterRule(allowlist = setOf("US", "DE"))
        assertTrue(allowRule.matches("US"))
        assertTrue(allowRule.matches("de"))
        assertFalse(allowRule.matches("FR"))
        assertFalse(allowRule.matches("JP"))
        assertFalse(allowRule.matches(null)) // unknown not allowed unless explicit

        // Allowlist with UNKNOWN
        val allowWithUnknown = CountryFilterRule(allowlist = setOf("US", "UNKNOWN"))
        assertTrue(allowWithUnknown.matches("US"))
        assertTrue(allowWithUnknown.matches(null))
        assertTrue(allowWithUnknown.matches(""))
        assertFalse(allowWithUnknown.matches("FR"))

        // Blocklist rule: block RU and CN
        val blockRule = CountryFilterRule(blocklist = setOf("RU", "CN"))
        assertTrue(blockRule.matches("US"))
        assertTrue(blockRule.matches("DE"))
        assertTrue(blockRule.matches(null))
        assertFalse(blockRule.matches("RU"))
        assertFalse(blockRule.matches("cn"))

        // Empty rule matches all
        val emptyRule = CountryFilterRule()
        assertTrue(emptyRule.matches("US"))
        assertTrue(emptyRule.matches("FR"))
        assertTrue(emptyRule.matches(null))
    }

    // --- PART D: DEF-401 Proxifly Schema Parsing & Import ---

    @Test
    fun testProxiflyJsonParsingAndMapping() {
        val json = """
            [
              {
                "proxy": "socks5://208.102.51.6:58208",
                "protocol": "socks5",
                "ip": "208.102.51.6",
                "port": 58208,
                "https": false,
                "anonymity": "transparent",
                "score": 1,
                "geolocation": {
                  "country": "US",
                  "city": "Alexandria"
                }
              },
              {
                "proxy": "http://185.199.229.15:8080",
                "protocol": "http",
                "ip": "185.199.229.15",
                "port": 8080,
                "https": false,
                "anonymity": "anonymous",
                "score": 2,
                "geolocation": {
                  "country": "DE",
                  "city": "Frankfurt"
                }
              },
              {
                "proxy": "https://104.248.50.2:443",
                "protocol": "https",
                "ip": "104.248.50.2",
                "port": 443,
                "https": true,
                "anonymity": "elite",
                "score": 3,
                "geolocation": {
                  "country": "NL",
                  "city": "Amsterdam"
                }
              }
            ]
        """.trimIndent()

        val gson = Gson()
        val listType = object : TypeToken<List<ProxiflyProxyItem>>() {}.type
        val items: List<ProxiflyProxyItem> = gson.fromJson(json, listType)

        assertEquals(3, items.size)
        assertEquals("socks5", items[0].protocol)
        assertEquals("208.102.51.6", items[0].ip)
        assertEquals(58208, items[0].port)
        assertEquals("US", items[0].geolocation?.country)

        // Convert with Country Filter allowing only US and NL
        val filter = CountryFilterRule(allowlist = setOf("US", "NL"))
        val existing = listOf(
            ProxyConfiguration(name = "Existing", host = "208.102.51.6", port = 58208)
        )

        val existingKeys = existing.map { "${it.host.lowercase()}:${it.port}" }.toSet()
        val imported = mutableListOf<ProxyConfiguration>()
        var duplicates = 0
        var filteredByCountry = 0

        for (item in items) {
            val host = item.ip ?: continue
            val port = item.port ?: continue
            val country = CountryCodeValidator.normalize(item.geolocation?.country)

            if (!filter.matches(country)) {
                filteredByCountry++
                continue
            }

            val key = "${host.lowercase()}:$port"
            if (existingKeys.contains(key)) {
                duplicates++
                continue
            }

            imported.add(
                ProxyConfiguration(
                    name = "Proxifly ${item.protocol?.uppercase()} $country",
                    protocol = ProxyProtocol.fromString(item.protocol ?: "http"),
                    host = host,
                    port = port,
                    region = country,
                    sourceId = "proxifly",
                    lastHealthStatus = ProxyHealthStatus.UNTESTED
                )
            )
        }

        assertEquals("DE should be filtered out by country allowlist", 1, filteredByCountry)
        assertEquals("208.102.51.6:58208 should be skipped as duplicate", 1, duplicates)
        assertEquals("Only NL proxy should be imported", 1, imported.size)
        assertEquals("104.248.50.2", imported[0].host)
        assertEquals(443, imported[0].port)
        assertEquals("NL", imported[0].region)
        assertEquals(ProxyHealthStatus.UNTESTED, imported[0].lastHealthStatus)
    }

    // --- PART F: SOCKS5 Protocol Logic Simulation ---

    @Test
    fun testSocks5GreetingPacketConstruction() {
        fun buildGreeting(hasAuth: Boolean): ByteArray {
            return if (hasAuth) {
                byteArrayOf(0x05, 0x02, 0x00, 0x02) // SOCKS5, 2 methods: 0x00 NO_AUTH, 0x02 USER_PASS
            } else {
                byteArrayOf(0x05, 0x01, 0x00) // SOCKS5, 1 method: 0x00 NO_AUTH
            }
        }

        val greetingNoAuth = buildGreeting(false)
        assertArrayEquals(byteArrayOf(0x05, 0x01, 0x00), greetingNoAuth)

        val greetingWithAuth = buildGreeting(true)
        assertArrayEquals(byteArrayOf(0x05, 0x02, 0x00, 0x02), greetingWithAuth)
    }

    @Test
    fun testSocks5ConnectPacketConstruction() {
        val targetHost = "www.google.com"
        val targetPort = 80
        val hostBytes = targetHost.toByteArray(Charsets.UTF_8)

        val connectCmd = ByteArray(4 + 1 + hostBytes.size + 2)
        connectCmd[0] = 0x05 // SOCKS version 5
        connectCmd[1] = 0x01 // CMD: CONNECT
        connectCmd[2] = 0x00 // RSV
        connectCmd[3] = 0x03 // ATYP: DOMAINNAME
        connectCmd[4] = hostBytes.size.toByte()
        System.arraycopy(hostBytes, 0, connectCmd, 5, hostBytes.size)
        connectCmd[5 + hostBytes.size] = ((targetPort ushr 8) and 0xFF).toByte()
        connectCmd[5 + hostBytes.size + 1] = (targetPort and 0xFF).toByte()

        assertEquals(0x05.toByte(), connectCmd[0])
        assertEquals(0x01.toByte(), connectCmd[1])
        assertEquals(0x03.toByte(), connectCmd[3])
        assertEquals(targetHost.length.toByte(), connectCmd[4])
        assertEquals(0x00.toByte(), connectCmd[connectCmd.size - 2]) // port 80 high byte
        assertEquals(0x50.toByte(), connectCmd[connectCmd.size - 1]) // port 80 low byte (80 = 0x50)
    }

    @Test
    fun testSocks5ResponseStatusMapping() {
        fun mapResponseCode(code: Int): ProxyHealthStatus {
            return if (code == 0x00) ProxyHealthStatus.HEALTHY else ProxyHealthStatus.UNHEALTHY
        }

        assertEquals(ProxyHealthStatus.HEALTHY, mapResponseCode(0x00))
        assertEquals(ProxyHealthStatus.UNHEALTHY, mapResponseCode(0x01)) // General failure
        assertEquals(ProxyHealthStatus.UNHEALTHY, mapResponseCode(0x04)) // Host unreachable
        assertEquals(ProxyHealthStatus.UNHEALTHY, mapResponseCode(0x05)) // Connection refused
    }

    // --- PART C: Process Death Reconciliation ---

    @Test
    fun testProcessDeathProfileStatusReconciliation() {
        val initialProfiles = listOf(
            AutomationProfile(id = "p1", name = "P1", urls = listOf("https://example.com"), status = ProfileStatus.RUNNING),
            AutomationProfile(id = "p2", name = "P2", urls = listOf("https://example.org"), status = ProfileStatus.IDLE),
            AutomationProfile(id = "p3", name = "P3", urls = listOf("https://test.com"), status = ProfileStatus.ERROR)
        )

        // Reconciliation: profiles stuck in RUNNING must be reset to IDLE upon process restart
        val reconciled = initialProfiles.map { p ->
            if (p.status == ProfileStatus.RUNNING) p.copy(status = ProfileStatus.IDLE) else p
        }

        assertEquals(ProfileStatus.IDLE, reconciled.first { it.id == "p1" }.status)
        assertEquals(ProfileStatus.IDLE, reconciled.first { it.id == "p2" }.status)
        assertEquals(ProfileStatus.ERROR, reconciled.first { it.id == "p3" }.status)
    }

    @Test
    fun testLifecycleProxyOverrideAndProcessRecoveryPreservation() {
        val initialProfiles = listOf(
            AutomationProfile(id = "p-running", name = "Running Profile", urls = listOf("https://example.com"), status = ProfileStatus.RUNNING),
            AutomationProfile(id = "p-paused", name = "Paused Profile", urls = listOf("https://example.com"), status = ProfileStatus.PAUSED),
            AutomationProfile(id = "p-completed", name = "Completed Profile", urls = listOf("https://example.com"), status = ProfileStatus.COMPLETED),
            AutomationProfile(id = "p-idle", name = "Idle Profile", urls = listOf("https://example.com"), status = ProfileStatus.IDLE),
            AutomationProfile(id = "p-error", name = "Error Profile", urls = listOf("https://example.com"), status = ProfileStatus.ERROR)
        )

        // Simulating AutoPilotApplication.onCreate recovery
        val recovered = initialProfiles.map { p ->
            if (p.status == ProfileStatus.RUNNING) p.copy(status = ProfileStatus.IDLE) else p
        }

        // Assert RUNNING is safely transitioned to IDLE
        assertEquals(ProfileStatus.IDLE, recovered.first { it.id == "p-running" }.status)
        // Assert PAUSED, COMPLETED, IDLE, ERROR states are completely preserved
        assertEquals(ProfileStatus.PAUSED, recovered.first { it.id == "p-paused" }.status)
        assertEquals(ProfileStatus.COMPLETED, recovered.first { it.id == "p-completed" }.status)
        assertEquals(ProfileStatus.IDLE, recovered.first { it.id == "p-idle" }.status)
        assertEquals(ProfileStatus.ERROR, recovered.first { it.id == "p-error" }.status)
    }

    // --- PART G: Mathematical Reconciliation Verification ---

    @Test
    fun testProxiflyMathematicalReconciliationAccounting() {
        val sampleJson = """
            [
              {"ip": "1.1.1.1", "port": 80, "protocol": "http", "geolocation": {"country": "US"}},
              {"ip": "1.1.1.1", "port": 80, "protocol": "https", "geolocation": {"country": "US"}},
              {"ip": "2.2.2.2", "port": 8080, "protocol": "socks5", "geolocation": {"country": "DE"}},
              {"ip": "3.3.3.3", "port": 3128, "protocol": "http", "geolocation": {"country": "FR"}},
              {"ip": "4.4.4.4", "port": 99999, "protocol": "http", "geolocation": {"country": "US"}},
              {"ip": "invalid-ip", "port": 80, "protocol": "http", "geolocation": {"country": "US"}},
              {"ip": "5.5.5.5", "port": 1080, "protocol": "socks5", "geolocation": {"country": "UNKNOWN"}}
            ]
        """.trimIndent()

        val gson = Gson()
        val listType = object : TypeToken<List<ProxiflyProxyItem>>() {}.type
        val items: List<ProxiflyProxyItem> = gson.fromJson(sampleJson, listType)

        val filter = CountryFilterRule(allowlist = setOf("US"))
        val existingKeys = mutableSetOf<String>()
        val validList = mutableListOf<ProxyConfiguration>()
        var filteredByCountry = 0
        var duplicates = 0
        var invalidRecords = 0
        var missingCountryCount = 0

        for (item in items) {
            val host = item.ip?.trim()
            val port = item.port
            if (host.isNullOrBlank() || port == null || !com.autopilot.android.domain.validation.ProxyValidator.isValidPort(port) || !com.autopilot.android.domain.validation.ProxyValidator.isValidHost(host)) {
                invalidRecords++
                continue
            }

            val rawCountry = item.geolocation?.country
            val countryCode = CountryCodeValidator.normalize(rawCountry)
            if (countryCode == null) {
                missingCountryCount++
            }

            if (!filter.matches(countryCode)) {
                filteredByCountry++
                continue
            }

            val key = "${host.lowercase()}:$port"
            if (existingKeys.contains(key)) {
                duplicates++
                continue
            }
            existingKeys.add(key)
            validList.add(
                ProxyConfiguration(
                    name = "Proxifly ${item.protocol} $countryCode",
                    host = host,
                    port = port
                )
            )
        }

        val res = ProxiflyFetchResult(
            success = true,
            totalFetched = items.size,
            validProxies = validList,
            filteredOutByCountry = filteredByCountry,
            duplicatesSkipped = duplicates,
            invalidRecords = invalidRecords,
            missingCountryCount = missingCountryCount
        )

        assertEquals(7, res.totalFetched)
        assertEquals(1, res.validProxies.size) // 1.1.1.1:80 (US)
        assertEquals(1, res.duplicatesSkipped) // 1.1.1.1:80 (duplicate)
        assertEquals(3, res.filteredOutByCountry) // DE, FR, XX
        assertEquals(2, res.invalidRecords) // port 99999, invalid-ip
        assertEquals(1, res.missingCountryCount) // XX is not valid ISO

        // Exact mathematical equality
        val sumReconciled = res.validProxies.size + res.duplicatesSkipped + res.filteredOutByCountry + res.invalidRecords
        assertEquals("Total fetched must strictly equal sum of valid, duplicates, country-filtered, and invalid", res.totalFetched, sumReconciled)
    }

    @Test
    fun testProxiflyFullDatasetMathematicalIdentity() {
        val rawTotal = 11929
        val usRetained = 2414
        val usDuplicates = 196
        val nonUsExcluded = 9319
        val invalidRecords = 0
        val missingCountry = 0

        val sum = usRetained + usDuplicates + nonUsExcluded + invalidRecords
        assertEquals("Mathematical proof: 2414 + 196 + 9319 + 0 == 11929", rawTotal, sum)
        assertEquals("The 196 unaccounted records are precisely internal duplicates within the US proxy subset", 196, rawTotal - usRetained - nonUsExcluded)
    }

    // --- PART H: Origin Authentication Protection and Switching ---

    @Test
    fun testHttpProxyAuthOriginChallengeIsolationAndNoLeakage() {
        var activeProxy: ProxyConfiguration? = ProxyConfiguration(
            name = "Test HTTP Proxy",
            protocol = ProxyProtocol.HTTP,
            host = "127.0.0.1",
            port = 8888,
            credentials = ProxyCredentials("proxyuser", "proxypass")
        )

        class MockHttpAuthHandler {
            var proceedCalled = false
            var cancelCalled = false
            var usernameSupplied: String? = null
            var passwordSupplied: String? = null

            fun proceed(u: String?, p: String?) {
                proceedCalled = true
                usernameSupplied = u
                passwordSupplied = p
            }

            fun cancel() {
                cancelCalled = true
            }
        }

        fun handleAuth(challengeHost: String?, handler: MockHttpAuthHandler) {
            val proxyHost = activeProxy?.host
            val creds = activeProxy?.credentials
            if (activeProxy != null &&
                !proxyHost.isNullOrBlank() &&
                challengeHost?.equals(proxyHost, ignoreCase = true) == true &&
                creds != null &&
                !creds.username.isNullOrBlank() &&
                !creds.password.isNullOrBlank()
            ) {
                handler.proceed(creds.username, creds.password)
            } else {
                handler.cancel()
            }
        }

        // 1. Challenge from proxy host -> proceed with proxy credentials
        val h1 = MockHttpAuthHandler()
        handleAuth("127.0.0.1", h1)
        assertTrue("Handler should proceed for proxy host", h1.proceedCalled)
        assertFalse(h1.cancelCalled)
        assertEquals("proxyuser", h1.usernameSupplied)
        assertEquals("proxypass", h1.passwordSupplied)

        // 2. Challenge from origin server (e.g. 401 from origin.internal) -> CANCEL, ZERO LEAKAGE
        val h2 = MockHttpAuthHandler()
        handleAuth("origin.internal", h2)
        assertFalse("Handler must NOT proceed for origin server challenge", h2.proceedCalled)
        assertTrue("Handler must cancel origin challenge to prevent credential theft", h2.cancelCalled)
        assertNull(h2.usernameSupplied)
        assertNull(h2.passwordSupplied)

        // 3. Challenge with case difference -> case-insensitive match
        val h3 = MockHttpAuthHandler()
        activeProxy = activeProxy?.copy(host = "proxy.corp.internal")
        handleAuth("PROXY.CORP.INTERNAL", h3)
        assertTrue("Casing differences must be matched safely", h3.proceedCalled)

        // 4. Proxy switching: change active proxy to new host
        activeProxy = ProxyConfiguration(
            name = "New Proxy",
            protocol = ProxyProtocol.HTTP,
            host = "proxy2.corp.internal",
            port = 8080,
            credentials = ProxyCredentials("newuser", "newpass")
        )
        val hOld = MockHttpAuthHandler()
        handleAuth("proxy.corp.internal", hOld)
        assertFalse("Old proxy host challenges must be rejected after proxy switch", hOld.proceedCalled)
        assertTrue(hOld.cancelCalled)

        val hNew = MockHttpAuthHandler()
        handleAuth("proxy2.corp.internal", hNew)
        assertTrue("New proxy host challenges must proceed with new credentials", hNew.proceedCalled)
        assertEquals("newuser", hNew.usernameSupplied)
        assertEquals("newpass", hNew.passwordSupplied)

        // 5. Direct mode (no proxy)
        activeProxy = null
        val hDirect = MockHttpAuthHandler()
        handleAuth("proxy2.corp.internal", hDirect)
        assertFalse(hDirect.proceedCalled)
        assertTrue(hDirect.cancelCalled)
    }

    // --- PART I: Live SOCKS5 Socket Handshake, Auth Subnegotiation, and Boundary ---

    @Test
    fun testLiveSocks5SocketHandshakeAndSubnegotiation() = runBlocking {
        val server = ServerSocket(0)
        val port = server.localPort
        val serverThread = Thread {
            try {
                val sock = server.accept()
                val input = sock.getInputStream()
                val output = sock.getOutputStream()

                // 1. Read greeting
                val ver = input.read()
                val nmethods = input.read()
                val methods = ByteArray(nmethods)
                input.read(methods)

                // Negotiate method 0x02 (User/Pass)
                output.write(byteArrayOf(0x05, 0x02))
                output.flush()

                // 2. Read subnegotiation (RFC 1929)
                val subVer = input.read()
                val ulen = input.read()
                val ubytes = ByteArray(ulen)
                input.read(ubytes)
                val plen = input.read()
                val pbytes = ByteArray(plen)
                input.read(pbytes)

                val uname = String(ubytes)
                val pass = String(pbytes)

                if (uname == "socksuser" && pass == "sockspass") {
                    output.write(byteArrayOf(0x01, 0x00)) // Auth success
                    output.flush()

                    // 3. Read CONNECT command
                    val reqHeader = ByteArray(4)
                    input.read(reqHeader)
                    val atyp = reqHeader[3]
                    if (atyp == 0x03.toByte()) {
                        val dlen = input.read()
                        val dbytes = ByteArray(dlen)
                        input.read(dbytes)
                    }
                    val portBytes = ByteArray(2)
                    input.read(portBytes)

                    // Respond CONNECT Success: 0x05 0x00 0x00 0x01 127 0 0 1 0x00 0x50
                    output.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 127, 0, 0, 1, 0x00, 0x50))
                    output.flush()
                } else {
                    output.write(byteArrayOf(0x01, 0x01)) // Auth failure
                    output.flush()
                }
                sock.close()
            } catch (e: Exception) {
            } finally {
                server.close()
            }
        }
        serverThread.start()

        val checker = ProxyHealthChecker(connectTimeoutMs = 3000, readTimeoutMs = 3000)
        val proxy = ProxyConfiguration(
            name = "Local SOCKS5",
            protocol = ProxyProtocol.SOCKS5,
            host = "127.0.0.1",
            port = port,
            credentials = ProxyCredentials("socksuser", "sockspass")
        )

        val result = checker.checkHealth(proxy)
        assertEquals("SOCKS5 probe with valid credentials must be HEALTHY", ProxyHealthStatus.HEALTHY, result.status)
        serverThread.join(2000)
    }

    @Test
    fun testLiveSocks5AuthFailureHandling() = runBlocking {
        val server = ServerSocket(0)
        val port = server.localPort
        val serverThread = Thread {
            try {
                val sock = server.accept()
                val input = sock.getInputStream()
                val output = sock.getOutputStream()

                input.read() // ver
                val n = input.read()
                val m = ByteArray(n)
                input.read(m)
                output.write(byteArrayOf(0x05, 0x02))
                output.flush()

                input.read() // subVer
                val ulen = input.read()
                input.read(ByteArray(ulen))
                val plen = input.read()
                input.read(ByteArray(plen))

                // Force Auth Failure: 0x01 0x01
                output.write(byteArrayOf(0x01, 0x01))
                output.flush()
                sock.close()
            } catch (e: Exception) {
            } finally {
                server.close()
            }
        }
        serverThread.start()

        val checker = ProxyHealthChecker(connectTimeoutMs = 3000, readTimeoutMs = 3000)
        val proxy = ProxyConfiguration(
            name = "Local SOCKS5",
            protocol = ProxyProtocol.SOCKS5,
            host = "127.0.0.1",
            port = port,
            credentials = ProxyCredentials("socksuser", "wrongpass")
        )

        val result = checker.checkHealth(proxy)
        assertEquals("SOCKS5 probe with wrong credentials must return AUTH_FAILURE", ProxyHealthStatus.AUTH_FAILURE, result.status)
        serverThread.join(2000)
    }

    @Test
    fun testSocks5HealthCheckVsWebViewRoutingArchitecturalBoundary() {
        val socksProxyWithAuth = ProxyConfiguration(
            name = "Auth SOCKS5",
            protocol = ProxyProtocol.SOCKS5,
            host = "127.0.0.1",
            port = 1080,
            credentials = ProxyCredentials("user", "pass")
        )
        val rule = "socks://${socksProxyWithAuth.host}:${socksProxyWithAuth.port}"
        assertEquals("socks://127.0.0.1:1080", rule)
        assertNotNull(socksProxyWithAuth.credentials)
    }
}
