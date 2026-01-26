package com.example.proxypotps.probe

import java.io.InputStream
import java.io.OutputStream

internal class SsAeadTunnel(
    private val input: InputStream,
    private val output: OutputStream,
    private val cipher: SsAeadCipher
) {
    private val writeNonce = SsNonce()
    private val readNonce = SsNonce()

    fun writeChunk(plaintext: ByteArray) {
        val lengthBytes = byteArrayOf(((plaintext.size ushr 8) and 0xFF).toByte(), (plaintext.size and 0xFF).toByte())
        val lengthCipher = cipher.encrypt(writeNonce.current(), lengthBytes)
        writeNonce.increment()
        val dataCipher = cipher.encrypt(writeNonce.current(), plaintext)
        writeNonce.increment()
        output.write(lengthCipher)
        output.write(dataCipher)
        output.flush()
    }

    fun readChunk(): ByteArray? {
        val lengthCipher = readFully(input, 2 + TAG_LENGTH) ?: return null
        val lengthPlain = cipher.decrypt(readNonce.current(), lengthCipher)
        readNonce.increment()
        val length = ((lengthPlain[0].toInt() and 0xFF) shl 8) or (lengthPlain[1].toInt() and 0xFF)
        val dataCipher = readFully(input, length + TAG_LENGTH) ?: return null
        val dataPlain = cipher.decrypt(readNonce.current(), dataCipher)
        readNonce.increment()
        return dataPlain
    }

    companion object {
        private const val TAG_LENGTH = 16

        fun readFully(input: InputStream, length: Int): ByteArray? {
            val buffer = ByteArray(length)
            var offset = 0
            while (offset < length) {
                val read = input.read(buffer, offset, length - offset)
                if (read == -1) return null
                offset += read
            }
            return buffer
        }
    }
}
