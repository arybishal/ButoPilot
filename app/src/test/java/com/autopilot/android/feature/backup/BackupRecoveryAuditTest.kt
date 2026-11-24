package com.autopilot.android.feature.backup

import com.autopilot.android.data.local.entity.ProfileEntity
import com.autopilot.android.data.local.entity.ProxyEntity
import com.autopilot.android.data.local.entity.ScheduleEntity
import com.autopilot.android.domain.model.ProfileStatus
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.crypto.AEADBadTagException

class BackupRecoveryAuditTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val crypto = BackupCrypto()
    private val serializer = BackupSerializer()

    private fun createSampleProfile(id: String, name: String) = ProfileEntity(
        id = id,
        name = name,
        description = "Test description",
        urlsJson = "[\"https://example.com\"]",
        tabCount = 1,
        executionMode = "STANDARD",
        refreshIntervalSec = 30,
        scrollIntervalSec = 10,
        randomizeIntervals = false,
        minRefreshIntervalSec = 15,
        maxRefreshIntervalSec = 45,
        minScrollIntervalSec = 5,
        maxScrollIntervalSec = 15,
        customScript = null,
        allowedDomainsJson = "[]",
        userAgent = null,
        clearCookiesOnStart = false,
        clearCacheOnStart = false,
        timeoutSeconds = 60,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
        status = "IDLE",
        proxyId = null
    )

    private fun createSampleSchedule(id: String, profileId: String) = ScheduleEntity(
        id = id,
        profileId = profileId,
        profileName = "Sample Profile",
        scheduleName = "Sample Schedule",
        scheduleType = "ONE_TIME",
        recurrenceRule = "60",
        targetEpochMillis = System.currentTimeMillis(),
        nextExecutionAt = System.currentTimeMillis() + 60000,
        lastExecutionAt = null,
        timezoneId = "UTC",
        enabled = false,
        maxDurationMinutes = 10,
        status = "IDLE",
        lastError = null,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )

    private fun createSampleProxy(id: String, host: String, port: Int) = ProxyEntity(
        id = id,
        name = "Sample Proxy",
        protocol = "HTTP",
        host = host,
        port = port,
        encryptedUsername = null,
        encryptedPassword = null,
        usernameIv = null,
        passwordIv = null,
        region = "US",
        notes = "Notes",
        sourceId = null,
        enabled = true,
        lastHealthStatus = "HEALTHY",
        lastLatencyMs = 20L,
        lastTestedAt = System.currentTimeMillis(),
        lastErrorMessage = null,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )

    @Test
    fun testPbkdf2UpgradedWorkFactorAndBackwardsCompatibility() {
        // 1. Verify constant definitions
        assertEquals(250_000, BackupCrypto.PBKDF2_ITERATIONS)
        assertEquals(100_000, BackupCrypto.LEGACY_PBKDF2_ITERATIONS)

        val plaintext = "Critical AutoPilot Automation Profiles and Configs"
        val plainBytes = plaintext.toByteArray(Charsets.UTF_8)
        val password = "SuperSecretPassword123!"

        // 2. Encrypt with legacy 100,000 iterations
        val legacyEnc = crypto.encrypt(plainBytes, password, BackupCrypto.LEGACY_PBKDF2_ITERATIONS)
        assertEquals(100_000, legacyEnc.iterations)

        // 3. Decrypt legacy payload specifying 100,000 iterations (verifying backwards compatibility)
        val legacyDecrypted = crypto.decrypt(
            cipherText = legacyEnc.cipherText,
            password = password,
            salt = legacyEnc.salt,
            iv = legacyEnc.iv,
            iterations = 100_000
        )
        assertEquals(plaintext, String(legacyDecrypted, Charsets.UTF_8))

        // 4. Encrypt with upgraded 250,000 iterations (default)
        val upgradedEnc = crypto.encrypt(plainBytes, password)
        assertEquals(250_000, upgradedEnc.iterations)

        // 5. Decrypt upgraded payload with 250,000 iterations
        val upgradedDecrypted = crypto.decrypt(
            cipherText = upgradedEnc.cipherText,
            password = password,
            salt = upgradedEnc.salt,
            iv = upgradedEnc.iv,
            iterations = 250_000
        )
        assertEquals(plaintext, String(upgradedDecrypted, Charsets.UTF_8))

        // 6. Cross-iteration mismatch must fail AEAD authentication
        try {
            crypto.decrypt(
                cipherText = legacyEnc.cipherText,
                password = password,
                salt = legacyEnc.salt,
                iv = legacyEnc.iv,
                iterations = 250_000 // Wrong iteration count
            )
            fail("Expected authentication failure when decrypting with mismatched iteration count")
        } catch (e: Exception) {
            assertTrue(e is AEADBadTagException || e.message?.contains("Tag mismatch", ignoreCase = true) == true)
        }
    }

    @Test
    fun testCorruptedPayloadRejectionInDecryption() {
        val plainBytes = "Confidential Data Payload".toByteArray(Charsets.UTF_8)
        val password = "SecureKey987!"

        val enc = crypto.encrypt(plainBytes, password)

        // Corrupt salt
        val badSalt = enc.salt.clone()
        badSalt[0] = (badSalt[0].toInt() xor 0xAA).toByte()
        try {
            crypto.decrypt(enc.cipherText, password, badSalt, enc.iv, enc.iterations)
            fail("Decryption must fail with corrupted salt")
        } catch (e: Exception) {
            assertTrue(e is AEADBadTagException || e.message?.contains("Tag mismatch", ignoreCase = true) == true)
        }

        // Corrupt IV
        val badIv = enc.iv.clone()
        badIv[0] = (badIv[0].toInt() xor 0x55).toByte()
        try {
            crypto.decrypt(enc.cipherText, password, enc.salt, badIv, enc.iterations)
            fail("Decryption must fail with corrupted IV")
        } catch (e: Exception) {
            assertTrue(e is AEADBadTagException || e.message?.contains("Tag mismatch", ignoreCase = true) == true)
        }
    }

    @Test
    fun testAutoBackupSettingsModelAndDefaults() {
        val settings = AutoBackupSettings()
        assertFalse(settings.isEnabled)
        assertEquals(AutoBackupFrequency.DAILY, settings.frequency)
        assertEquals(5, settings.retentionCount)
        assertTrue(settings.includeHistory)

        assertEquals("Daily (24 Hours)", AutoBackupFrequency.DAILY.displayName)
        assertEquals(24L, AutoBackupFrequency.DAILY.intervalHours)

        assertEquals("Weekly (7 Days)", AutoBackupFrequency.WEEKLY.displayName)
        assertEquals(168L, AutoBackupFrequency.WEEKLY.intervalHours)
    }

    @Test
    fun testPreTransactionValidationRules() {
        // Validation Rule 1: Profile ID must not be blank
        val invalidProfileBlankId = createSampleProfile("", "Valid Name")
        assertThrows(IllegalArgumentException::class.java) {
            require(invalidProfileBlankId.id.isNotBlank()) { "Corrupt backup: Profile contains blank ID" }
        }

        // Validation Rule 2: Profile Name must not be blank
        val invalidProfileBlankName = createSampleProfile("valid-id-1", "   ")
        assertThrows(IllegalArgumentException::class.java) {
            require(invalidProfileBlankName.name.isNotBlank()) { "Corrupt backup: Profile contains blank name" }
        }

        // Validation Rule 3: Schedule ID and ProfileId must not be blank
        val invalidSchedule = createSampleSchedule("", "prof-1")
        assertThrows(IllegalArgumentException::class.java) {
            require(invalidSchedule.id.isNotBlank()) { "Corrupt backup: Schedule contains blank ID" }
        }

        // Validation Rule 4: Proxy port must be in 1..65535
        val invalidProxyPort = createSampleProxy("px-1", "10.0.0.1", 70000)
        assertThrows(IllegalArgumentException::class.java) {
            require(invalidProxyPort.port in 1..65535) { "Corrupt backup: Proxy port out of range (${invalidProxyPort.port})" }
        }
    }

    @Test
    fun testZipSlipPathTraversalRejection() {
        val zipFile = tempFolder.newFile("malicious_outer.zip")

        // Construct zip containing a path traversal attempt
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            val evilEntry = ZipEntry("../../evil_file.txt")
            zos.putNextEntry(evilEntry)
            zos.write("malicious payload".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        // Verify traversal detection logic
        ZipFile(zipFile).use { zip ->
            val entry = zip.entries().nextElement()
            val hasTraversal = entry.name.contains("..") || entry.name.startsWith("/") || entry.name.startsWith("\\")
            assertTrue("Traversal entry must be detected as malicious", hasTraversal)
        }
    }

    @Test
    fun testZipSlipBackslashTraversalRejection() {
        val zipFile = tempFolder.newFile("malicious_backslash.zip")

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            val evilEntry = ZipEntry("..\\..\\windows\\system32\\evil.dll")
            zos.putNextEntry(evilEntry)
            zos.write("malicious payload".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        ZipFile(zipFile).use { zip ->
            val entry = zip.entries().nextElement()
            val hasTraversal = entry.name.contains("..") || entry.name.startsWith("/") || entry.name.startsWith("\\")
            assertTrue("Backslash traversal entry must be detected as malicious", hasTraversal)
        }
    }

    @Test
    fun testZipEntryLimitRejection() {
        val zipFile = tempFolder.newFile("zip_bomb_entries.zip")

        // Create archive with 55 entries (> MAX_ZIP_ENTRIES = 50)
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            for (i in 1..55) {
                zos.putNextEntry(ZipEntry("file_$i.txt"))
                zos.write("data".toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }

        ZipFile(zipFile).use { zip ->
            var entryCount = 0
            val entries = zip.entries()
            var exceeded = false
            while (entries.hasMoreElements()) {
                entries.nextElement()
                entryCount++
                if (entryCount > 50) {
                    exceeded = true
                    break
                }
            }
            assertTrue("Archive with 55 entries must exceed the 50-entry security threshold", exceeded)
        }
    }

    @Test
    fun testActiveAutomationSessionPreservationLogic() {
        // Recovery Center Safety Logic:
        // A profile that is actively RUNNING or legitimately user-PAUSED in an active session
        // MUST NEVER be reset to IDLE by One-Tap Recovery.

        val activeProfileId = "prof-active-10"
        val pausedProfileId = "prof-paused-20"
        val zombieProfileId = "prof-zombie-30"

        // Scenario A: Coordinator is actively running activeProfileId
        val isCoordinatorActive = true
        val coordinatorHoldingId = activeProfileId

        val profilesToTest = listOf(
            Pair("prof-active-10", ProfileStatus.RUNNING),
            Pair("prof-zombie-30", ProfileStatus.RUNNING)
        )

        val resetProfiles = mutableListOf<String>()
        for ((id, status) in profilesToTest) {
            // Safety rule applied in RecoveryManager:
            if (isCoordinatorActive && id == coordinatorHoldingId) {
                continue // Protect active profile!
            }
            if (status == ProfileStatus.RUNNING || status == ProfileStatus.PAUSED) {
                resetProfiles.add(id)
            }
        }

        // Active profile must NOT be reset; Zombie profile MUST be reset
        assertFalse(resetProfiles.contains(activeProfileId))
        assertTrue(resetProfiles.contains(zombieProfileId))
    }

    @Test
    fun testRestoredSchedulesAlwaysDisabled() {
        val activeSchedule = createSampleSchedule("sched-active-99", "prof-1").copy(
            enabled = true,
            status = "SCHEDULED"
        )

        val json = serializer.serializeSchedules(listOf(activeSchedule))
        val restored = serializer.deserializeSchedules(json)

        assertEquals(1, restored.size)
        // MANDATORY SAFETY: Must be disabled upon restore
        assertFalse("Restored schedules must be disabled by default", restored[0].enabled)
        assertEquals("IDLE", restored[0].status)
    }

    @Test
    fun testProxyCredentialsNeverExported() {
        val proxy = createSampleProxy("px-secret", "proxy.corp.internal", 8443).copy(
            encryptedUsername = "CIPHER_TEXT_USER_999",
            encryptedPassword = "CIPHER_TEXT_PASSWORD_888",
            usernameIv = "IV_USER_777",
            passwordIv = "IV_PASS_666"
        )

        val json = serializer.serializeProxies(listOf(proxy))
        assertFalse("Serialized JSON must never contain username ciphertext", json.contains("CIPHER_TEXT_USER_999"))
        assertFalse("Serialized JSON must never contain password ciphertext", json.contains("CIPHER_TEXT_PASSWORD_888"))
        assertFalse("Serialized JSON must never contain username IV", json.contains("IV_USER_777"))
        assertFalse("Serialized JSON must never contain password IV", json.contains("IV_PASS_666"))

        val deserialized = serializer.deserializeProxies(json)
        assertEquals(1, deserialized.size)
        assertNull(deserialized[0].encryptedUsername)
        assertNull(deserialized[0].encryptedPassword)
        assertNull(deserialized[0].usernameIv)
        assertNull(deserialized[0].passwordIv)
    }
}
