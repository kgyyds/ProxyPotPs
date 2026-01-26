package com.example.proxypotps.probe

import java.io.InputStream
import java.io.OutputStream


internal class SsAeadTunnel(
    private val input: InputStream,
    private val output: OutputStream,

    private val cipherName: String,
    private val masterKey: ByteArray,
    private val saltLength: Int,
    private val keyLength: Int,

    private val encryptCipher: SsAeadCipher,
    private var decryptCipher: SsAeadCipher? = null,

    private val encryptNonce: SsNonce = SsNonce(),
    private val decryptNonce: SsNonce = SsNonce()
) {
    private var decryptReady = false

    private fun ensureDecryptReady() {
        if (decryptReady) return

        // ✅ 第一次读取时，先读 serverSalt
        val serverSalt = readFully(input, saltLength) ?: throw IllegalStateException("empty_server_salt")

        val subKey = SsCrypto.hkdfSha1(
            serverSalt,
            masterKey,
            "ss-subkey".toByteArray(Charsets.UTF_8),
            keyLength
        )
        decryptCipher = SsAeadCipher(cipherName, subKey)
        decryptReady = true
    }

    fun writeChunk(plaintext: ByteArray) {
        val lengthBytes = byteArrayOf(((plaintext.size ushr 8) and 0xFF).toByte(), (plaintext.size and 0xFF).toByte())
        val lengthCipher = encryptCipher.encrypt(encryptNonce.current(), lengthBytes)
        encryptNonce.increment()
        val dataCipher = encryptCipher.encrypt(encryptNonce.current(), plaintext)
        encryptNonce.increment()
        output.write(lengthCipher)
        output.write(dataCipher)
        output.flush()
    }

    fun readChunk(): ByteArray? {
        ensureDecryptReady()
        val cipher = decryptCipher ?: throw IllegalStateException("decrypt_not_ready")

        val lengthCipher = readFully(input, 2 + TAG_LENGTH) ?: return null
        val lengthPlain = cipher.decrypt(decryptNonce.current(), lengthCipher)
        decryptNonce.increment()

        val length = ((lengthPlain[0].toInt() and 0xFF) shl 8) or (lengthPlain[1].toInt() and 0xFF)
        val dataCipher = readFully(input, length + TAG_LENGTH) ?: return null
        val dataPlain = cipher.decrypt(decryptNonce.current(), dataCipher)
        decryptNonce.increment()
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
