package com.example.proxypotps.probe

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class SsAeadTunnelTest {
    @Test
    fun writeAndReadChunk_roundTrips() {
        val key = ByteArray(16) { it.toByte() }
        val encryptCipher = SsAeadCipher("aes-128-gcm", key)
        val decryptCipher = SsAeadCipher("aes-128-gcm", key)
        val payload = "hello-shadow".toByteArray()

        val outputStream = ByteArrayOutputStream()
        val writer = SsAeadTunnel(
            input = ByteArrayInputStream(ByteArray(0)),
            output = outputStream,
            encryptCipher = encryptCipher,
            decryptCipher = decryptCipher,
            encryptNonce = SsNonce(),
            decryptNonce = SsNonce()
        )
        writer.writeChunk(payload)

        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        val reader = SsAeadTunnel(
            input = inputStream,
            output = ByteArrayOutputStream(),
            encryptCipher = encryptCipher,
            decryptCipher = decryptCipher,
            encryptNonce = SsNonce(),
            decryptNonce = SsNonce()
        )

        val decoded = reader.readChunk()
        assertArrayEquals(payload, decoded)
    }
}
