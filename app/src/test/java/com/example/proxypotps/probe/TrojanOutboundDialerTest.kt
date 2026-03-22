package com.example.proxypotps.probe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrojanOutboundDialerTest {
    @Test
    fun buildTrojanHeader_usesSha224HexPrefix() {
        val dialer = TrojanOutboundDialer()
        val request = byteArrayOf(0x01, 0x03, 0x07, 'e'.code.toByte())
        val method = TrojanOutboundDialer::class.java.getDeclaredMethod(
            "buildTrojanHeader",
            String::class.java,
            ByteArray::class.java
        )
        method.isAccessible = true

        val header = method.invoke(dialer, "my_password", request) as ByteArray
        val marker = "\r\n".toByteArray(Charsets.UTF_8)
        val markerIndex = header.indexOfSubarray(marker)

        assertTrue("header should include CRLF after auth segment", markerIndex > 0)

        val authSegment = header.copyOfRange(0, markerIndex).toString(Charsets.UTF_8)
        assertEquals("SHA-224 hex string must be 56 chars", 56, authSegment.length)
        assertTrue("SHA-224 auth segment must be lowercase hex", authSegment.matches(Regex("^[0-9a-f]{56}$")))
    }

    private fun ByteArray.indexOfSubarray(target: ByteArray): Int {
        if (target.isEmpty() || target.size > this.size) return -1
        for (index in 0..(this.size - target.size)) {
            if (this.copyOfRange(index, index + target.size).contentEquals(target)) {
                return index
            }
        }
        return -1
    }
}
