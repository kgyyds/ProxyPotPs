package com.example.proxypotps.probe

import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Security
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.jce.provider.BouncyCastleProvider

internal object SsCrypto {
    private val secureRandom = SecureRandom()

    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    fun deriveKey(password: String, keyLength: Int): ByteArray {
        val passwordBytes = password.toByteArray(Charsets.UTF_8)
        val md5 = MessageDigest.getInstance("MD5")
        val key = ByteArray(keyLength)
        var offset = 0
        var prev = ByteArray(0)
        while (offset < keyLength) {
            md5.reset()
            md5.update(prev)
            md5.update(passwordBytes)
            val digest = md5.digest()
            val copyLength = minOf(digest.size, keyLength - offset)
            System.arraycopy(digest, 0, key, offset, copyLength)
            offset += copyLength
            prev = digest
        }
        return key
    }

    fun hkdfSha1(salt: ByteArray, ikm: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(salt, "HmacSHA1"))
        val prk = mac.doFinal(ikm)
        val result = ByteArray(length)
        var previous = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            mac.init(SecretKeySpec(prk, "HmacSHA1"))
            mac.update(previous)
            mac.update(info)
            mac.update(counter.toByte())
            val output = mac.doFinal()
            val copyLength = minOf(output.size, length - offset)
            System.arraycopy(output, 0, result, offset, copyLength)
            offset += copyLength
            previous = output
            counter++
        }
        return result
    }

    fun randomBytes(length: Int): ByteArray {
        return ByteArray(length).also { secureRandom.nextBytes(it) }
    }
}

internal class SsAeadCipher(private val cipherName: String, private val key: ByteArray) {
    private val tagLengthBits = 128

    fun encrypt(nonce: ByteArray, plaintext: ByteArray): ByteArray {
        return createCipher(Cipher.ENCRYPT_MODE, nonce).doFinal(plaintext)
    }

    fun decrypt(nonce: ByteArray, ciphertext: ByteArray): ByteArray {
        return createCipher(Cipher.DECRYPT_MODE, nonce).doFinal(ciphertext)
    }

    private fun createCipher(mode: Int, nonce: ByteArray): Cipher {
        return when (cipherName) {
            "aes-128-gcm" -> {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val spec = GCMParameterSpec(tagLengthBits, nonce)
                cipher.init(mode, SecretKeySpec(key, "AES"), spec)
                cipher
            }
            "chacha20-ietf-poly1305" -> {
                val cipher = Cipher.getInstance("ChaCha20-Poly1305", BouncyCastleProvider.PROVIDER_NAME)
                val spec = IvParameterSpec(nonce)
                cipher.init(mode, SecretKeySpec(key, "ChaCha20"), spec)
                cipher
            }
            else -> error("Unsupported cipher $cipherName")
        }
    }
}

internal class SsNonce {
    private val nonce = ByteArray(12)

    fun current(): ByteArray = nonce.copyOf()

    fun increment() {
        for (i in nonce.indices) {
            val value = (nonce[i].toInt() and 0xFF) + 1
            nonce[i] = value.toByte()
            if (value <= 0xFF) {
                break
            }
        }
    }
}
