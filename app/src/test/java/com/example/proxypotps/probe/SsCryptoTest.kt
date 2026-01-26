package com.example.proxypotps.probe

import org.junit.Assert.assertEquals
import org.junit.Test

class SsCryptoTest {
    @Test
    fun deriveKey_matchesKnownVector() {
        val key = SsCrypto.deriveKey("password", 16)
        assertEquals("5f4dcc3b5aa765d61d8327deb882cf99", key.toHex())
    }

    @Test
    fun hkdfSha1_matchesKnownVector() {
        val output = SsCrypto.hkdfSha1(
            salt = "salt".toByteArray(),
            ikm = "input".toByteArray(),
            info = "ss-subkey".toByteArray(),
            length = 16
        )
        assertEquals("3e0e71e839012e731aebea6aeb4ec96f", output.toHex())
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
