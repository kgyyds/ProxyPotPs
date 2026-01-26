package com.example.proxypotps.network

import android.util.Log
import com.example.proxypotps.domain.model.ProxyNode
import com.example.proxypotps.probe.OutboundDialer
import com.example.proxypotps.probe.SsOutboundDialer
import com.example.proxypotps.probe.TrojanOutboundDialer
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LocalProxyServer(
    private val node: ProxyNode,
    private val ssDialer: SsOutboundDialer,
    private val trojanDialer: TrojanOutboundDialer,
    private val scope: CoroutineScope
) {
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    val host: String = "127.0.0.1"
    val proxyType: String = "HTTP"
    val nodeId: Long = node.id
    val port: Int
        get() = serverSocket?.localPort ?: -1

    val isRunning: Boolean
        get() = running.get() && serverSocket?.isClosed == false

    fun start() {
        if (running.getAndSet(true)) return
        serverSocket = ServerSocket(0, 50, InetAddress.getByName(host))
        acceptJob = scope.launch(Dispatchers.IO) {
            while (running.get()) {
                try {
                    val client = serverSocket?.accept() ?: break
                    launch { handleClient(client) }
                } catch (error: Exception) {
                    if (running.get()) {
                        Log.e("LOCAL_PROXY", "accept failed node=${node.name}", error)
                    }
                }
            }
        }
    }

    fun stop() {
        running.set(false)
        acceptJob?.cancel()
        acceptJob = null
        serverSocket?.close()
        serverSocket = null
    }

    private suspend fun handleClient(client: Socket) {
        withContext(Dispatchers.IO) {
            client.use { socket ->
                val input = BufferedInputStream(socket.getInputStream())
                val output = BufferedOutputStream(socket.getOutputStream())
                val headerBytes = readHeaders(input)
                if (headerBytes.isEmpty()) return@withContext
                val headerText = headerBytes.toString(Charsets.UTF_8)
                val headerLines = headerText.split("\r\n")
                val requestLine = headerLines.firstOrNull() ?: return@withContext
                val parts = requestLine.split(" ")
                if (parts.size < 3) return@withContext
                val method = parts[0].uppercase(Locale.US)
                val target = parts[1]
                if (method == "CONNECT") {
                    val hostPort = target.split(":")
                    val destHost = hostPort.firstOrNull().orEmpty()
                    val destPort = hostPort.getOrNull(1)?.toIntOrNull() ?: 443
                    openTunnelAndPipe(destHost, destPort, input, output, headerBytes, isConnect = true)
                } else {
                    val uri = runCatching { URI(target) }.getOrNull()
                    val destHost = uri?.host ?: extractHostHeader(headerLines)
                    val destPort = uri?.port?.takeIf { it > 0 } ?: 80
                    if (destHost.isNullOrBlank()) {
                        Log.e("LOCAL_PROXY", "missing host for node=${node.name}")
                        return@withContext
                    }
                    val path = uri?.rawPath?.ifBlank { "/" } ?: "/"
                    val query = uri?.rawQuery?.let { "?$it" }.orEmpty()
                    val newRequestLine = "$method $path$query ${parts[2]}"
                    val rewrittenHeaders = headerLines.drop(1)
                        .filter { it.isNotBlank() }
                        .joinToString("\r\n")
                    val rebuilt = buildString {
                        append(newRequestLine)
                        append("\r\n")
                        if (rewrittenHeaders.isNotBlank()) {
                            append(rewrittenHeaders)
                            append("\r\n")
                        }
                        append("\r\n")
                    }.toByteArray(Charsets.UTF_8)
                    openTunnelAndPipe(destHost, destPort, input, output, rebuilt, isConnect = false)
                }
            }
        }
    }

    private suspend fun openTunnelAndPipe(
        destHost: String,
        destPort: Int,
        clientInput: BufferedInputStream,
        clientOutput: BufferedOutputStream,
        initialPayload: ByteArray,
        isConnect: Boolean
    ) {
        val dialer = selectDialer(node)
        val tunnel = try {
            dialer.openTunnel(node.toProbeNode(), destHost, destPort, 8_000)
        } catch (error: Exception) {
            Log.e("LOCAL_PROXY", "tunnel failed node=${node.name} host=$destHost:$destPort", error)
            return
        }
        tunnel.use { socketLike ->
            val tunnelInput = BufferedInputStream(socketLike.input)
            val tunnelOutput = BufferedOutputStream(socketLike.output)
            if (isConnect) {
                clientOutput.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                clientOutput.flush()
            } else {
                tunnelOutput.write(initialPayload)
                tunnelOutput.flush()
            }
            val upstream = scope.launch(Dispatchers.IO) {
                pipe(clientInput, tunnelOutput)
            }
            val downstream = scope.launch(Dispatchers.IO) {
                pipe(tunnelInput, clientOutput)
            }
            upstream.join()
            downstream.join()
        }
    }

    private fun selectDialer(node: ProxyNode): OutboundDialer {
        return when (node.type.lowercase()) {
            "ss", "shadowsocks" -> ssDialer
            "trojan" -> {
                if (node.extras["network"]?.lowercase() == "grpc") {
                    throw IllegalStateException("unsupported_trojan_grpc")
                }
                trojanDialer
            }
            else -> throw IllegalArgumentException("unsupported_type_${node.type}")
        }
    }

    private fun extractHostHeader(lines: List<String>): String? {
        return lines.firstOrNull { it.startsWith("Host:", ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()
            ?.substringBefore(":")
    }

    private fun readHeaders(input: BufferedInputStream): ByteArray {
        val buffer = ByteArrayOutputStream()
        val lastFour = IntArray(4)
        var index = 0
        while (true) {
            val curr = input.read()
            if (curr == -1) break
            buffer.write(curr)
            lastFour[index % 4] = curr
            index++
            if (index >= 4 &&
                lastFour[(index - 4) % 4] == '\r'.code &&
                lastFour[(index - 3) % 4] == '\n'.code &&
                lastFour[(index - 2) % 4] == '\r'.code &&
                lastFour[(index - 1) % 4] == '\n'.code
            ) {
                break
            }
        }
        return buffer.toByteArray()
    }

    private fun pipe(input: BufferedInputStream, output: BufferedOutputStream) {
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            output.write(buffer, 0, read)
            output.flush()
        }
    }
}

private fun ProxyNode.toProbeNode(): com.example.proxypotps.probe.ProbeNode {
    val grpcServiceName = extras["grpc-service-name"] ?: extras["grpc-opts.grpc-service-name"]
        ?: extras["grpc-opts"]?.substringAfter("grpc-service-name=")?.substringBefore(",")
    return com.example.proxypotps.probe.ProbeNode(
        id = id,
        name = name,
        type = type,
        server = server,
        port = port,
        cipher = extras["cipher"],
        password = extras["password"],
        sni = extras["sni"],
        network = extras["network"],
        grpcServiceName = grpcServiceName
    )
}
