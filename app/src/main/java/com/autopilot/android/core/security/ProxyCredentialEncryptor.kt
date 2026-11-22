package com.autopilot.android.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedPayload(
    val ciphertextBase64: String,
    val ivBase64: String
)

class ProxyCredentialEncryptor {

    companion object {
        private const val KEY_ALIAS = "autopilot_proxy_keystore_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val IV_LENGTH_BYTES = 12

        // Deterministic fallback key for JVM unit tests where AndroidKeyStore is absent
        private val JVM_FALLBACK_KEY_BYTES = byteArrayOf(
            0x41, 0x75, 0x74, 0x6F, 0x50, 0x69, 0x6C, 0x6F,
            0x74, 0x50, 0x72, 0x6F, 0x78, 0x79, 0x53, 0x65,
            0x63, 0x75, 0x72, 0x65, 0x4B, 0x65, 0x79, 0x32,
            0x30, 0x32, 0x36, 0x41, 0x47, 0x59, 0x21, 0x23
        ) // 32 bytes = 256 bits
    }

    private val secureRandom = SecureRandom()

    private fun getSecretKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGen.init(spec)
                keyGen.generateKey()
            } else {
                (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
            }
        } catch (e: Exception) {
            // Fallback for JVM unit tests without AndroidKeyStore
            SecretKeySpec(JVM_FALLBACK_KEY_BYTES, "AES")
        }
    }

    fun encrypt(plaintext: String?): EncryptedPayload? {
        if (plaintext.isNullOrEmpty()) return null

        val iv = ByteArray(IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))

        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return EncryptedPayload(
            ciphertextBase64 = encodeBase64(ciphertext),
            ivBase64 = encodeBase64(iv)
        )
    }

    fun decrypt(ciphertextBase64: String?, ivBase64: String?): String? {
        if (ciphertextBase64.isNullOrEmpty() || ivBase64.isNullOrEmpty()) return null

        return try {
            val ciphertext = decodeBase64(ciphertextBase64)
            val iv = decodeBase64(ivBase64)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))

            val decryptedBytes = cipher.doFinal(ciphertext)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    private fun encodeBase64(bytes: ByteArray): String {
        return try {
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            java.util.Base64.getEncoder().encodeToString(bytes)
        }
    }

    private fun decodeBase64(str: String): ByteArray {
        return try {
            Base64.decode(str, Base64.NO_WRAP)
        } catch (e: Exception) {
            java.util.Base64.getDecoder().decode(str)
        }
    }
}
