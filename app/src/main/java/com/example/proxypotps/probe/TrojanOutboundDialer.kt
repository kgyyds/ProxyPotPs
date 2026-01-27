package com.example.proxypotps.probe

import android.util.Log
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

@Singleton
class TrojanOutboundDialer @Inject constructor() : OutboundDialer {
    override suspend fun openTunnel(
        node: ProbeNode,
        destHost: String,
        destPort: Int,
        timeoutMs: Long
    ): SocketLike {
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

                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, null, null)
                val factory = sslContext.socketFactory
                val sslSocket = factory.createSocket(socket, node.server, node.port, true) as SSLSocket
                val params = SSLParameters()
                val sniHost = node.sni ?: node.server
                params.serverNames = listOf(SNIHostName(sniHost))
                sslSocket.sslParameters = params
                try {
                    sslSocket.startHandshake()
                    Log.d("TROJAN", "tls handshake ok ${node.name}")
                } catch (error: Exception) {
                    Log.e("TROJAN", "tls handshake failed ${node.name}", error)
                    throw error
                }
                sslSocket.soTimeout = timeoutMs.toInt()

                val output = sslSocket.outputStream
                val input = sslSocket.inputStream

                val request = buildTrojanRequest(destHost, destPort)
                val header = buildTrojanHeader(password, request)
                try {
                    output.write(header)
                    output.flush()
                    Log.d("TROJAN", "request sent ${node.name} to $destHost:$destPort")
                } catch (error: Exception) {
                    Log.e("TROJAN", "request send failed ${node.name}", error)
                    throw error
                }

                object : SocketLike {
                    override val input = input
                    override val output = output

                    override fun close() {
                        sslSocket.close()
                    }
                }
            } catch (error: Exception) {
                Log.e("PROBE", "DIAL_OPEN_FAIL nodeId=${node.id} node=${node.name} type=${node.type}", error)
                throw error
            } finally {
                cancelHandler.dispose()
            }
        }
    }

    private fun buildTrojanHeader(password: String, request: ByteArray): ByteArray {
        val passwordBytes = password.toByteArray(Charsets.UTF_8)
        val crlf = "\r\n".toByteArray(Charsets.UTF_8)
        return passwordBytes + crlf + request + crlf
    }

    private fun buildTrojanRequest(host: String, port: Int): ByteArray {
        val addressBytes = when {
            host.isIpV4() -> byteArrayOf(0x01) + InetAddress.getByName(host).address
            host.isIpV6() -> byteArrayOf(0x04) + InetAddress.getByName(host).address
            else -> {
                val hostBytes = host.toByteArray(Charsets.UTF_8)
                byteArrayOf(0x03, hostBytes.size.toByte()) + hostBytes
            }
        }
        val portBytes = byteArrayOf(((port ushr 8) and 0xFF).toByte(), (port and 0xFF).toByte())
        return byteArrayOf(0x01) + addressBytes + portBytes
    }
}

private fun String.isIpV4(): Boolean = Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+$").matches(this)
private fun String.isIpV6(): Boolean = contains(":")
