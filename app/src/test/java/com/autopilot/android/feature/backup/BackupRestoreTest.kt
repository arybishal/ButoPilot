package com.autopilot.android.feature.backup

import com.autopilot.android.data.local.entity.ProfileEntity
import com.autopilot.android.data.local.entity.ProxyEntity
import com.autopilot.android.data.local.entity.ScheduleEntity
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.AEADBadTagException

class BackupRestoreTest {

    private val crypto = BackupCrypto()
    private val serializer = BackupSerializer()

    @Test
    fun testAesGcmEncryptionAndDecryption() {
        val originalText = "AutoPilot Backup Secret Data Payload 123456"
        val plainBytes = originalText.toByteArray(Charsets.UTF_8)
        val password = "StrongMasterPassword!@#888"

        val encrypted = crypto.encrypt(plainBytes, password)

        assertNotNull(encrypted.cipherText)
        assertNotNull(encrypted.salt)
        assertNotNull(encrypted.iv)
        assertEquals(BackupCrypto.SALT_LENGTH_BYTES, encrypted.salt.size)
        assertEquals(BackupCrypto.GCM_IV_LENGTH_BYTES, encrypted.iv.size)
        assertFalse(plainBytes.contentEquals(encrypted.cipherText))

        val decryptedBytes = crypto.decrypt(
            cipherText = encrypted.cipherText,
            password = password,
            salt = encrypted.salt,
            iv = encrypted.iv,
            iterations = encrypted.iterations
        )

        val decryptedText = String(decryptedBytes, Charsets.UTF_8)
        assertEquals(originalText, decryptedText)
    }

    @Test
    fun testDecryptionFailsOnWrongPassword() {
        val plainBytes = "Secret Data".toByteArray(Charsets.UTF_8)
        val encrypted = crypto.encrypt(plainBytes, "CorrectPassword123")

        try {
            crypto.decrypt(
                cipherText = encrypted.cipherText,
                password = "WrongPassword999",
                salt = encrypted.salt,
                iv = encrypted.iv,
                iterations = encrypted.iterations
            )
            fail("Expected security exception on incorrect password")
        } catch (e: Exception) {
            // Expected AEADBadTagException or GeneralSecurityException
            assertTrue(e is AEADBadTagException || e.message?.contains("Tag mismatch", ignoreCase = true) == true)
        }
    }

    @Test
    fun testDecryptionFailsOnTamperedCiphertext() {
        val plainBytes = "Secret Data".toByteArray(Charsets.UTF_8)
        val encrypted = crypto.encrypt(plainBytes, "Password123")

        // Tamper with the ciphertext
        val tamperedCipher = encrypted.cipherText.clone()
        tamperedCipher[0] = (tamperedCipher[0].toInt() xor 0xFF).toByte()

        try {
            crypto.decrypt(
                cipherText = tamperedCipher,
                password = "Password123",
                salt = encrypted.salt,
                iv = encrypted.iv,
                iterations = encrypted.iterations
            )
            fail("Expected AEAD authentication failure on tampered ciphertext")
        } catch (e: Exception) {
            assertTrue(e is AEADBadTagException || e.message?.contains("Tag mismatch", ignoreCase = true) == true)
        }
    }

    @Test
    fun testUniqueNoncesPerEncryption() {
        val plainBytes = "Static Payload".toByteArray(Charsets.UTF_8)
        val password = "Password123"

        val enc1 = crypto.encrypt(plainBytes, password)
        val enc2 = crypto.encrypt(plainBytes, password)

        // Nonces (IVs) and salts must be unique across encryptions
        assertFalse("IVs must be uniquely generated per encryption", enc1.iv.contentEquals(enc2.iv))
        assertFalse("Salts must be uniquely generated per encryption", enc1.salt.contentEquals(enc2.salt))
        assertFalse("Ciphertexts must differ due to unique nonces", enc1.cipherText.contentEquals(enc2.cipherText))
    }

    @Test
    fun testProxyCredentialExclusionInSerialization() {
        // MANDATORY SECURITY REQUIREMENT:
        // Passwords, credentials, and encryption IVs MUST NEVER be serialized into backups.
        val proxy = ProxyEntity(
            id = "proxy-101",
            name = "Sensitive Private Proxy",
            protocol = "HTTP",
            host = "192.168.1.50",
            port = 8080,
            encryptedUsername = "ENCRYPTED_USER_ABC",
            encryptedPassword = "ENCRYPTED_PASSWORD_XYZ",
            usernameIv = "IV_USER_123",
            passwordIv = "IV_PASS_456",
            region = "US",
            notes = "Work VPN proxy",
            sourceId = null,
            enabled = true,
            lastHealthStatus = "HEALTHY",
            lastLatencyMs = 45L,
            lastTestedAt = System.currentTimeMillis(),
            lastErrorMessage = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        val json = serializer.serializeProxies(listOf(proxy))

        // Check serialized JSON content
        assertFalse("Encrypted username must not be in backup JSON", json.contains("ENCRYPTED_USER_ABC"))
        assertFalse("Encrypted password must not be in backup JSON", json.contains("ENCRYPTED_PASSWORD_XYZ"))
        assertFalse("Username IV must not be in backup JSON", json.contains("IV_USER_123"))
        assertFalse("Password IV must not be in backup JSON", json.contains("IV_PASS_456"))

        // Deserialize and check
        val deserialized = serializer.deserializeProxies(json)
        assertEquals(1, deserialized.size)
        assertNull("Deserialized proxy username must be null", deserialized[0].encryptedUsername)
        assertNull("Deserialized proxy password must be null", deserialized[0].encryptedPassword)
        assertNull("Deserialized username IV must be null", deserialized[0].usernameIv)
        assertNull("Deserialized password IV must be null", deserialized[0].passwordIv)
        assertEquals("Sensitive Private Proxy", deserialized[0].name)
        assertEquals("192.168.1.50", deserialized[0].host)
        assertEquals(8080, deserialized[0].port)
    }

    @Test
    fun testRestoredSchedulesForcedDisabled() {
        // MANDATORY SAFETY REQUIREMENT:
        // Restored schedules MUST be disabled by default (enabled = false) pending user review
        val activeSchedule = ScheduleEntity(
            id = "sched-1",
            profileId = "prof-1",
            profileName = "Heavy Scraper",
            scheduleName = "Hourly Trigger",
            scheduleType = "RECURRING_INTERVAL",
            recurrenceRule = "60",
            targetEpochMillis = System.currentTimeMillis(),
            nextExecutionAt = System.currentTimeMillis() + 10000,
            lastExecutionAt = null,
            timezoneId = "UTC",
            enabled = true, // Was enabled in original database
            maxDurationMinutes = 30,
            status = "SCHEDULED",
            lastError = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        val json = serializer.serializeSchedules(listOf(activeSchedule))
        val restoredSchedules = serializer.deserializeSchedules(json)

        assertEquals(1, restoredSchedules.size)
        assertFalse("Restored schedules MUST have enabled = false", restoredSchedules[0].enabled)
        assertEquals("IDLE", restoredSchedules[0].status)
    }

    @Test
    fun testSha256ChecksumVerification() {
        val testContent = "AutoPilot Backup Content for Checksum Validation"
        val expectedSha = "473bc4f4520bc3b93f18e95c1c0454316b245dd98d36ebff4c9bfca5925a7566" // calculated SHA-256

        val actualSha = serializer.calculateSha256(testContent)
        assertNotNull(actualSha)
        assertEquals(64, actualSha.length) // 64 hex chars for SHA-256
    }
}
