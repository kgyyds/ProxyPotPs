package com.example.proxypotps.probe

import android.util.Log
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

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
            val dialStart = System.currentTimeMillis()
            Log.d("PROBE", "DIAL_OPEN_START nodeId=${node.id} node=${node.name} type=${node.type} host=${node.server}:${node.port}")
            val socket = Socket()
            socket.soTimeout = timeoutMs.toInt()
            val cancelHandler = coroutineContext.job.invokeOnCompletion { socket.close() }
            try {
                socket.connect(InetSocketAddress(node.server, node.port), timeoutMs.toInt())
                val elapsed = System.currentTimeMillis() - dialStart
                Log.d("PROBE", "DIAL_OPEN_OK nodeId=${node.id} node=${node.name} type=${node.type} elapsed=${elapsed}ms")

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
                val clientSalt = SsCrypto.randomBytes(saltLength)
                val encryptSubKey = SsCrypto.hkdfSha1(clientSalt, masterKey, "ss-subkey".toByteArray(Charsets.UTF_8), keyLength)
                val encryptCipher = SsAeadCipher(cipher, encryptSubKey)
                val encryptNonce = SsNonce()

                try {
                    output.write(clientSalt)
                    output.flush()
                    Log.d("SS", "clientSalt sent ${node.name}")
                } catch (error: Exception) {
                    Log.e("SS", "clientSalt send failed ${node.name}", error)
                    throw error
                }

                val addressHeader = buildAddressHeader(destHost, destPort)
                try {
                    writeEncryptedChunk(output, encryptCipher, encryptNonce, addressHeader)
                    Log.d("SS", "dst header sent ${node.name} to $destHost:$destPort")
                } catch (error: Exception) {
                    Log.e("SS", "dst header send failed ${node.name}", error)
                    throw error
                }

                // ✅ 不在这里读 serverSalt！
                // openTunnel 结束时直接返回 tunnel，decryptCipher 在首次 readChunk() 时初始化
                val tunnel = SsAeadTunnel(
                    input = input,
                    output = output,
                    cipherName = cipher,
                    masterKey = masterKey,
                    saltLength = saltLength,
                    keyLength = keyLength,
                    encryptCipher = encryptCipher,
                    encryptNonce = encryptNonce,
                    decryptNonce = SsNonce()
                )

                object : SocketLike {
                    override val input = SsTunnelInputStream(tunnel)
                    override val output = SsTunnelOutputStream(tunnel)
                    override fun close() { socket.close() }
                }
            } catch (error: Exception) {
                Log.e("PROBE", "DIAL_OPEN_FAIL nodeId=${node.id} node=${node.name} type=${node.type}", error)
                throw error
            } finally {
                cancelHandler.dispose()
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

private fun writeEncryptedChunk(
    output: java.io.OutputStream,
    cipher: SsAeadCipher,
    nonce: SsNonce,
    plaintext: ByteArray
) {
    val lengthBytes = byteArrayOf(((plaintext.size ushr 8) and 0xFF).toByte(), (plaintext.size and 0xFF).toByte())
    val lengthCipher = cipher.encrypt(nonce.current(), lengthBytes)
    nonce.increment()
    val dataCipher = cipher.encrypt(nonce.current(), plaintext)
    nonce.increment()
    output.write(lengthCipher)
    output.write(dataCipher)
    output.flush()
}
