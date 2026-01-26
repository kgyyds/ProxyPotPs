package com.example.proxypotps.probe

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SsOutboundDialer @Inject constructor() : OutboundDialer {
    override suspend fun openTunnel(
        node: ProbeNode,
        destHost: String,
        destPort: Int,
        timeoutMs: Long
    ): SocketLike {
        val cipher = node.cipher ?: error("Missing cipher")
        val password = node.password ?: error("Missing password")
        return withContext(Dispatchers.IO) {
            val socket = Socket()
            socket.soTimeout = timeoutMs.toInt()
            socket.connect(InetSocketAddress(node.server, node.port), timeoutMs.toInt())
            val output = socket.getOutputStream()
            val input = socket.getInputStream()

            val saltLength = when (cipher) {
                "aes-128-gcm" -> 16
                "chacha20-ietf-poly1305" -> 32
                else -> error("Unsupported cipher $cipher")
            }
            val keyLength = when (cipher) {
                "aes-128-gcm" -> 16
                "chacha20-ietf-poly1305" -> 32
                else -> error("Unsupported cipher $cipher")
            }
            val masterKey = SsCrypto.deriveKey(password, keyLength)
            val salt = SsCrypto.randomBytes(saltLength)
            val subKey = SsCrypto.hkdfSha1(salt, masterKey, "ss-subkey".toByteArray(Charsets.UTF_8), keyLength)
            val aeadCipher = SsAeadCipher(cipher, subKey)

            output.write(salt)
            output.flush()

            val tunnel = SsAeadTunnel(input, output, aeadCipher)
            val addressHeader = buildAddressHeader(destHost, destPort)
            tunnel.writeChunk(addressHeader)

            object : SocketLike {
                override val input = SsTunnelInputStream(tunnel)
                override val output = SsTunnelOutputStream(tunnel)

                override fun close() {
                    socket.close()
                }
            }
        }
    }

    private fun buildAddressHeader(host: String, port: Int): ByteArray {
        val addressBytes = when {
            host.isIpV4() -> byteArrayOf(0x01) + InetAddress.getByName(host).address
            host.isIpV6() -> byteArrayOf(0x04) + InetAddress.getByName(host).address
            else -> {
                val hostBytes = host.toByteArray(Charsets.UTF_8)
                byteArrayOf(0x03, hostBytes.size.toByte()) + hostBytes
            }
        }
        val portBytes = byteArrayOf(((port ushr 8) and 0xFF).toByte(), (port and 0xFF).toByte())
        return addressBytes + portBytes
    }
}

private class SsTunnelInputStream(private val tunnel: SsAeadTunnel) : java.io.InputStream() {
    private var buffer: ByteArray? = null
    private var offset = 0

    override fun read(): Int {
        if (!ensureBuffer()) return -1
        val value = buffer!![offset].toInt() and 0xFF
        offset++
        return value
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (!ensureBuffer()) return -1
        val available = buffer!!.size - offset
        val toRead = minOf(len, available)
        System.arraycopy(buffer!!, offset, b, off, toRead)
        offset += toRead
        return toRead
    }

    private fun ensureBuffer(): Boolean {
        if (buffer == null || offset >= buffer!!.size) {
            val next = tunnel.readChunk() ?: return false
            buffer = next
            offset = 0
        }
        return true
    }
}

private class SsTunnelOutputStream(private val tunnel: SsAeadTunnel) : java.io.OutputStream() {
    private val buffer = ByteArrayOutputStreamWithLimit()

    override fun write(b: Int) {
        buffer.write(byteArrayOf(b.toByte()))
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        buffer.write(b, off, len)
    }

    override fun flush() {
        val data = buffer.drain()
        if (data.isNotEmpty()) {
            tunnel.writeChunk(data)
        }
    }
}

private class ByteArrayOutputStreamWithLimit {
    private var buffer = ByteArray(0)

    fun write(bytes: ByteArray) {
        buffer += bytes
    }

    fun write(bytes: ByteArray, offset: Int, length: Int) {
        buffer += bytes.copyOfRange(offset, offset + length)
    }

    fun drain(): ByteArray {
        val data = buffer
        buffer = ByteArray(0)
        return data
    }
}

private fun String.isIpV4(): Boolean = Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+$").matches(this)
private fun String.isIpV6(): Boolean = contains(":")
