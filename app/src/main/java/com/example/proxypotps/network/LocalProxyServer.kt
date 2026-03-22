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
import java.net.SocketTimeoutException
import java.net.URI
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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

    // ✅ 防止一次探测开太多连接把手机打穿
    private val connLimit = Semaphore(permits = 32)

    // ✅ header 最大 32KB，避免无限读
    private val maxHeaderBytes = 32 * 1024

    // ✅ socket 读超时（和你探测超时 8s 对齐）
    private val socketReadTimeoutMs = 8_000

    fun start() {
        if (running.getAndSet(true)) return

        try {
            // 1) 同步绑定端口：start() 返回时 port 已经可用
            serverSocket = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1")).also {
                it.soTimeout = 1500
            }

            Log.i("LOCAL_PROXY", "started node=${node.name} port=${serverSocket!!.localPort}")

            // 2) accept loop（IO 线程）
            acceptJob = scope.launch(Dispatchers.IO) {
                try {
                    while (running.get()) {
                        val client: Socket? = try {
                            serverSocket?.accept()
                        } catch (_: SocketTimeoutException) {
                            null
                        }

                        if (client != null) {
                            // ✅ 每个 client 独立协程处理 + 并发限流
                            launch(Dispatchers.IO) {
                                connLimit.withPermit {
                                    runCatching {
                                        client.soTimeout = socketReadTimeoutMs
                                        handleClient(client)
                                    }.onFailure { t ->
                                        Log.e("LOCAL_PROXY", "client handler crashed node=${node.name}", t)
                                    }
                                }
                            }
                        }
                    }
                } catch (t: Throwable) {
                    Log.e("LOCAL_PROXY", "accept loop crashed node=${node.name}", t)
                } finally {
                    kotlin.runCatching { serverSocket?.close() }
                    serverSocket = null
                    running.set(false)
                }
            }
        } catch (t: Throwable) {
            Log.e("LOCAL_PROXY", "start failed node=${node.name}", t)
            kotlin.runCatching { serverSocket?.close() }
            serverSocket = null
            running.set(false)
            acceptJob = null
        }
    }

    fun stop() {
        running.set(false)
        acceptJob?.cancel()
        acceptJob = null
        kotlin.runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private suspend fun handleClient(client: Socket) {
        withContext(Dispatchers.IO) {
            client.use { socket ->
                val input = BufferedInputStream(socket.getInputStream())
                val output = BufferedOutputStream(socket.getOutputStream())

                val headerBytes = readHeaders(input)
                if (headerBytes.isEmpty()) {
                    Log.w("LOCAL_PROXY", "empty request from client node=${node.name}")
                    return@withContext
                }

                val headerText = headerBytes.toString(Charsets.UTF_8)
                logStage("HTTP_REQUEST", headerText.take(500))
                val headerLines = headerText.split("\r\n")
                val requestLine = headerLines.firstOrNull() ?: return@withContext
                val parts = requestLine.split(" ")
                if (parts.size < 3) {
                    Log.w("LOCAL_PROXY", "invalid request line: $requestLine node=${node.name}")
                    return@withContext
                }

                val method = parts[0].uppercase(Locale.US)
                val target = parts[1]

                if (method == "CONNECT") {
                    logStage("HANDLE_CONNECT", "target=$target")
                    val hostPort = target.split(":")
                    val destHost = hostPort.firstOrNull().orEmpty()
                    val destPort = hostPort.getOrNull(1)?.toIntOrNull() ?: 443
                    openTunnelAndPipe(
                        destHost = destHost,
                        destPort = destPort,
                        clientInput = input,
                        clientOutput = output,
                        initialPayload = headerBytes,
                        isConnect = true
                    )
                } else {
                    val uri = runCatching { URI(target) }.getOrNull()
                    val destHost = uri?.host ?: extractHostHeader(headerLines)
                    val destPort = uri?.port?.takeIf { it > 0 } ?: 80

                    if (destHost.isNullOrBlank()) {
                        Log.e("LOCAL_PROXY", "missing host for node=${node.name} target=$target")
                        sendBadRequest(output, "missing host")
                        return@withContext
                    }

                    val path = buildString {
                        append(uri?.rawPath?.ifBlank { "/" } ?: "/")
                        if (!uri?.rawQuery.isNullOrEmpty()) {
                            append("?")
                            append(uri.rawQuery)
                        }
                    }
                    val query = uri?.rawQuery?.let { "?$it" }.orEmpty()
                    val newRequestLine = "$method $path$query ${parts[2]}"

                    // 保留原 headers（不含 Host，让后端决定）
                    val rewrittenHeaders = headerLines.drop(1)
                        .filter { it.isNotBlank() && !it.startsWith("Host:", ignoreCase = true) }
                        .joinToString("\r\n")

                    val rebuilt = buildString {
                        append(newRequestLine).append("\r\n")
                        if (rewrittenHeaders.isNotBlank()) {
                            append(rewrittenHeaders).append("\r\n")
                        }
                        append("Host: $destHost\r\n")
                        append("\r\n")
                    }.toByteArray(Charsets.UTF_8)

                    logStage("HTTP_REWRITTEN", rebuilt.toString(Charsets.UTF_8).take(500))

                    openTunnelAndPipe(
                        destHost = destHost,
                        destPort = destPort,
                        clientInput = input,
                        clientOutput = output,
                        initialPayload = rebuilt,
                        isConnect = false
                    )
                }
            }
        }
    }

    private fun sendBadRequest(output: BufferedOutputStream, reason: String) {
        try {
            val response = "HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
            output.write(response.toByteArray())
            output.flush()
        } catch (e: Exception) {
            Log.e("LOCAL_PROXY", "sendBadRequest failed", e)
        }
    }

    private fun logStage(stage: String, message: String) {
        Log.d("LOCAL_PROXY", "$stage node=${node.name} $message")
    }

    private suspend fun openTunnelAndPipe(
        destHost: String,
        destPort: Int,
        clientInput: BufferedInputStream,
        clientOutput: BufferedOutputStream,
        initialPayload: ByteArray,
        isConnect: Boolean
    ) = coroutineScope {
        val dialer = try {
            selectDialer(node)
        } catch (error: UnsupportedProtocolException) {
            Log.e("LOCAL_PROXY", "unsupported protocol node=${node.name} reason=${error.issue.detail}")
            if (!isConnect) {
                sendBadGateway(clientOutput, error.issue)
            }
            return@coroutineScope
        }
        Log.d("LOCAL_PROXY", "openTunnel start node=${node.name} dest=$destHost:$destPort type=${node.type}")

        val tunnel = try {
            dialer.openTunnel(node.toProbeNode(), destHost, destPort, 8_000)
        } catch (error: Exception) {
            Log.e("LOCAL_PROXY", "tunnel failed node=${node.name} host=$destHost:$destPort err=${error.message}", error)
            if (!isConnect) {
                sendBadGateway(clientOutput, ProtocolSupportIssue("TUNNEL_FAILED", error.message ?: "unknown"))
            }
            return@coroutineScope
        }

        Log.d("LOCAL_PROXY", "tunnel opened node=${node.name} dest=$destHost:$destPort")

        tunnel.use { socketLike ->
            val tunnelInput = BufferedInputStream(socketLike.input)
            val tunnelOutput = BufferedOutputStream(socketLike.output)

            if (isConnect) {
                Log.d("LOCAL_PROXY", "sending CONNECT response node=${node.name}")
                clientOutput.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                clientOutput.flush()
            } else {
                Log.d("LOCAL_PROXY", "sending HTTP request node=${node.name} size=${initialPayload.size}")
                tunnelOutput.write(initialPayload)
                tunnelOutput.flush()
            }

            val up = async(Dispatchers.IO) { pipe(clientInput, tunnelOutput) }
            val down = async(Dispatchers.IO) { pipe(tunnelInput, clientOutput) }

            try {
                awaitAll(up, down)
                Log.d("LOCAL_PROXY", "pipe completed node=${node.name}")
            } finally {
                up.cancel()
                down.cancel()
            }
        }
    }

    private fun sendBadGateway(output: BufferedOutputStream, reason: ProtocolSupportIssue) {
        try {
            val response = "HTTP/1.1 502 Bad Gateway\r\n" +
                "X-ProxyPot-Error-Code: ${reason.code}\r\n" +
                "X-ProxyPot-Error-Detail: ${reason.detail}\r\n" +
                "Content-Length: 0\r\nConnection: close\r\n\r\n"
            output.write(response.toByteArray())
            output.flush()
        } catch (e: Exception) {
            Log.e("LOCAL_PROXY", "sendBadGateway failed", e)
        }
    }

    private fun selectDialer(node: ProxyNode): OutboundDialer {
        node.protocolSupportIssue()?.let { throw UnsupportedProtocolException(it) }
        return when (node.type.lowercase()) {
            "ss", "shadowsocks" -> ssDialer
            "trojan" -> trojanDialer
            else -> throw UnsupportedProtocolException(
                ProtocolSupportIssue("UNSUPPORTED_PROTOCOL", node.type.lowercase(Locale.US))
            )
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

        while (buffer.size() < maxHeaderBytes) {
            val curr = try {
                input.read()
            } catch (_: SocketTimeoutException) {
                break
            }
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
            val read = try {
                input.read(buffer)
            } catch (_: SocketTimeoutException) {
                break
            }
            if (read <= 0) break
            output.write(buffer, 0, read)
        }
        kotlin.runCatching { output.flush() }
    }
}

private fun ProxyNode.toProbeNode(): com.example.proxypotps.probe.ProbeNode {
    val grpcServiceName = extras["grpc-service-name"]
        ?: extras["grpc-opts.grpc-service-name"]
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
