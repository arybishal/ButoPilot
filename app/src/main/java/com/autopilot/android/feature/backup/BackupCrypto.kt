package com.autopilot.android.feature.backup

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedPayload(
    val cipherText: ByteArray,
    val salt: ByteArray,
    val iv: ByteArray,
    val iterations: Int = BackupCrypto.PBKDF2_ITERATIONS
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedPayload) return false
        return cipherText.contentEquals(other.cipherText) &&
                salt.contentEquals(other.salt) &&
                iv.contentEquals(other.iv) &&
                iterations == other.iterations
    }

    override fun hashCode(): Int {
        var result = cipherText.contentHashCode()
        result = 31 * result + salt.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + iterations
        return result
    }
}

class BackupCrypto {

    companion object {
        const val PBKDF2_ITERATIONS = 250_000
        const val LEGACY_PBKDF2_ITERATIONS = 100_000
        const val KEY_LENGTH_BITS = 256
        const val SALT_LENGTH_BYTES = 16
        const val GCM_IV_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128

        private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
    }

    private val secureRandom = SecureRandom()

    fun encrypt(plainBytes: ByteArray, password: String, iterations: Int = PBKDF2_ITERATIONS): EncryptedPayload {
        require(password.isNotBlank()) { "Backup encryption password must not be empty" }

        val salt = ByteArray(SALT_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(GCM_IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }

        val secretKey = deriveKey(password, salt, iterations)

        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

        val cipherText = cipher.doFinal(plainBytes)

        return EncryptedPayload(
            cipherText = cipherText,
            salt = salt,
            iv = iv,
            iterations = iterations
        )
    }

    fun decrypt(
        cipherText: ByteArray,
        password: String,
        salt: ByteArray,
        iv: ByteArray,
        iterations: Int = PBKDF2_ITERATIONS
    ): ByteArray {
        require(password.isNotBlank()) { "Backup decryption password must not be empty" }
        require(salt.isNotEmpty()) { "Salt cannot be empty" }
        require(iv.isNotEmpty()) { "IV cannot be empty" }

        val secretKey = deriveKey(password, salt, iterations)

        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

        return cipher.doFinal(cipherText)
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKey {
        val keySpec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
        val keyFactory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
        val keyBytes = keyFactory.generateSecret(keySpec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }
}
