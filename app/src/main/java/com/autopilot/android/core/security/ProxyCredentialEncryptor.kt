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

data class EncryptedPayload(
    val ciphertextBase64: String,
    val ivBase64: String
)

interface KeyProvider {
    fun getSecretKey(): SecretKey
}

class AndroidKeyStoreProvider(
    private val keyAlias: String = KEY_ALIAS
) : KeyProvider {

    companion object {
        const val KEY_ALIAS = "autopilot_proxy_keystore_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }

    override fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(keyAlias)) {
            val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGen.init(spec)
            return keyGen.generateKey()
        }
        val entry = keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
            ?: throw IllegalStateException("KeyStore entry for alias '$keyAlias' is not a SecretKeyEntry")
        return entry.secretKey
    }
}

class ProxyCredentialEncryptor(
    private val keyProvider: KeyProvider = AndroidKeyStoreProvider()
) {

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val IV_LENGTH_BYTES = 12
    }

    private val secureRandom = SecureRandom()

    fun encrypt(plaintext: String?): EncryptedPayload? {
        if (plaintext.isNullOrEmpty()) return null

        return try {
            val iv = ByteArray(IV_LENGTH_BYTES)
            secureRandom.nextBytes(iv)

            val secretKey = keyProvider.getSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))

            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            EncryptedPayload(
                ciphertextBase64 = encodeBase64(ciphertext),
                ivBase64 = encodeBase64(iv)
            )
        } catch (e: Exception) {
            // Safe explicit failure: never downgrade encryption or store plaintext
            null
        }
    }

    fun decrypt(ciphertextBase64: String?, ivBase64: String?): String? {
        if (ciphertextBase64.isNullOrEmpty() || ivBase64.isNullOrEmpty()) return null

        return try {
            val ciphertext = decodeBase64(ciphertextBase64)
            val iv = decodeBase64(ivBase64)

            val secretKey = keyProvider.getSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))

            val decryptedBytes = cipher.doFinal(ciphertext)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            // Return null on decryption error; never expose partial or corrupted plaintext
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
