package com.example.proxypotps.probe

import android.util.Log
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

@Singleton
class NodeProber @Inject constructor(
    private val ssDialer: SsOutboundDialer,
    private val trojanDialer: TrojanOutboundDialer
) {
    suspend fun probe(node: ProbeNode, probeUrl: String, timeoutMs: Long): ProbeResult {
        return try {
            withTimeout(timeoutMs) {
                probeInternal(node, probeUrl, timeoutMs)
            }
        } catch (timeout: TimeoutCancellationException) {
            Log.w("NodeProber", "Probe timeout for ${node.name}")
            ProbeResult.Timeout()
        } catch (error: Throwable) {
            val typeName = error::class.java.simpleName
            Log.w("NodeProber", "Probe failed for ${node.name}: [$typeName] ${error.message}")
            ProbeResult.Unavailable(error.message ?: "probe_error")
        }
    }

    private suspend fun probeInternal(node: ProbeNode, probeUrl: String, timeoutMs: Long): ProbeResult {
        val uri = runCatching { URI(probeUrl) }.getOrNull()
            ?: return ProbeResult.Unavailable("invalid_probe_url")
        val scheme = uri.scheme?.lowercase() ?: "http"
        if (scheme != "http") {
            return ProbeResult.Unavailable("unsupported_scheme_$scheme")
        }
        val host = uri.host ?: return ProbeResult.Unavailable("invalid_probe_host")
        val port = if (uri.port != -1) uri.port else 80
        val path = buildString {
            append(if (uri.rawPath.isNullOrEmpty()) "/" else uri.rawPath)
            if (!uri.rawQuery.isNullOrEmpty()) {
                append("?")
                append(uri.rawQuery)
            }
        }
        val dialer = when (node.type.lowercase()) {
            "ss", "shadowsocks" -> ssDialer
            "trojan" -> {
                if (node.network?.lowercase() == "grpc") {
                    return ProbeResult.Unavailable("Unsupported trojan grpc")
                }
                trojanDialer
            }
            else -> return ProbeResult.Unavailable("unsupported_type_${node.type}")
        }

        val start = System.currentTimeMillis()
        val socket = try {
            dialer.openTunnel(node, host, port, timeoutMs)
        } catch (error: Exception) {
            throw IllegalStateException("stage=open_tunnel: ${error.message}", error)
        }
        socket.use {
            val request = buildHttpRequest(host, path)
            try {
                it.output.write(request)
                it.output.flush()
            } catch (error: Exception) {
                throw IllegalStateException("stage=http_request: ${error.message}", error)
            }
            val responseLine = try {
                readResponseLine(it.input)
            } catch (error: Exception) {
                throw IllegalStateException("stage=read_response: ${error.message}", error)
            } ?: return ProbeResult.Unavailable("no_response")
            val latency = (System.currentTimeMillis() - start).toInt()
            val code = parseStatusCode(responseLine)
            if (code != null && code in 200..399) {
                Log.i("NodeProber", "Probe ok ${node.name} code=$code latency=${latency}ms")
                return ProbeResult.Available(latency, code)
            }
            Log.w("NodeProber", "Probe unavailable ${node.name} response=$responseLine")
            return ProbeResult.Unavailable("http_${code ?: -1}")
        }
    }

    private fun buildHttpRequest(host: String, path: String): ByteArray {
        return (
            "GET $path HTTP/1.1\r\n" +
                "Host: $host\r\n" +
                "User-Agent: ProxyPotPs/1.0\r\n" +
                "Connection: close\r\n" +
                "\r\n"
            ).toByteArray(Charsets.UTF_8)
    }

    private fun readResponseLine(input: java.io.InputStream): String? {
        val buffer = StringBuilder()
        var prev = -1
        while (true) {
            val value = input.read()
            if (value == -1) break
            if (prev == '\r'.code && value == '\n'.code) {
                buffer.setLength(buffer.length - 1)
                return buffer.toString()
            }
            buffer.append(value.toChar())
            prev = value
        }
        return if (buffer.isNotEmpty()) buffer.toString() else null
    }

    private fun parseStatusCode(line: String): Int? {
        val parts = line.split(" ")
        if (parts.size < 2) return null
        return parts[1].toIntOrNull()
    }
}
