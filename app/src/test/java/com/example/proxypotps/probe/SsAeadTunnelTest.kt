package com.example.proxypotps.probe

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class SsAeadTunnelTest {
    @Test
    fun writeAndReadChunk_roundTrips() {
        val masterKey = ByteArray(16) { it.toByte() }
        val saltLength = 16
        val keyLength = 16
        val salt = ByteArray(saltLength) { (it + 1).toByte() }
        val subKey = SsCrypto.hkdfSha1(
            salt,
            masterKey,
            "ss-subkey".toByteArray(Charsets.UTF_8),
            keyLength
        )
        val encryptCipher = SsAeadCipher("aes-128-gcm", subKey)
        val payload = "hello-shadow".toByteArray()

        val outputStream = ByteArrayOutputStream()
        outputStream.write(salt)
        val writer = SsAeadTunnel(
            input = ByteArrayInputStream(ByteArray(0)),
            output = outputStream,
            cipherName = "aes-128-gcm",
            masterKey = masterKey,
            saltLength = saltLength,
            keyLength = keyLength,
            encryptCipher = encryptCipher,
            encryptNonce = SsNonce(),
            decryptNonce = SsNonce()
        )
        writer.writeChunk(payload)

        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        val reader = SsAeadTunnel(
            input = inputStream,
            output = ByteArrayOutputStream(),
            cipherName = "aes-128-gcm",
            masterKey = masterKey,
            saltLength = saltLength,
            keyLength = keyLength,
            encryptCipher = encryptCipher,
            encryptNonce = SsNonce(),
            decryptNonce = SsNonce()
        )

        val decoded = reader.readChunk()
        assertArrayEquals(payload, decoded)
    }
}
