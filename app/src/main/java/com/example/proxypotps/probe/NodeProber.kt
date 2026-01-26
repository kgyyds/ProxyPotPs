package com.example.proxypotps.probe

import android.os.SystemClock
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
        val start = SystemClock.elapsedRealtime()
        Log.d("PROBE", "start node=${node.name} host=${node.server}:${node.port}")
        return try {
            withTimeout(timeoutMs) {
                probeInternal(node, probeUrl, timeoutMs, start)
            }
        } catch (timeout: TimeoutCancellationException) {
            Log.e("PROBE", "timeout node=${node.name}", timeout)
            ProbeResult.Timeout()
        } catch (error: Throwable) {
            Log.e("PROBE", "probe failed node=${node.name}", error)
            throw error
        }
    }

    private suspend fun probeInternal(
        node: ProbeNode,
        probeUrl: String,
        timeoutMs: Long,
        startMs: Long
    ): ProbeResult {
        val uri = try {
            URI(probeUrl)
        } catch (error: Exception) {
            Log.e("PROBE", "invalid probe url=$probeUrl", error)
            throw error
        }
        val scheme = uri.scheme?.lowercase() ?: "http"
        if (scheme != "http") {
            val error = IllegalArgumentException("unsupported_scheme_$scheme")
            Log.e("PROBE", "unsupported scheme node=${node.name}", error)
            throw error
        }
        val host = uri.host ?: throw IllegalArgumentException("invalid_probe_host")
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
                    val error = IllegalStateException("Unsupported trojan grpc")
                    Log.e("PROBE", "trojan grpc unsupported node=${node.name}", error)
                    throw error
                }
                trojanDialer
            }
            else -> throw IllegalArgumentException("unsupported_type_${node.type}")
        }

        val socket = dialer.openTunnel(node, host, port, timeoutMs)
        socket.use {
            val request = buildHttpRequest(host, path)
            try {
                it.output.write(request)
                it.output.flush()
                val stageTag = if (node.type.lowercase() in listOf("ss", "shadowsocks")) "SS" else "TROJAN"
                Log.d(stageTag, "http request sent node=${node.name}")
            } catch (error: Exception) {
                Log.e("PROBE", "http request failed node=${node.name}", error)
                throw error
            }
            val responseLine = try {
                readResponseLine(it.input)
            } catch (error: Exception) {
                Log.e("PROBE", "http response read failed node=${node.name}", error)
                throw error
            } ?: throw IllegalStateException("no_response")
            val latency = (SystemClock.elapsedRealtime() - startMs).toInt()
            val code = parseStatusCode(responseLine)
            if (code != null && code in 200..399) {
                val stageTag = if (node.type.lowercase() in listOf("ss", "shadowsocks")) "SS" else "TROJAN"
                Log.d(stageTag, "response received node=${node.name} status=$code")
                Log.d("PROBE", "success node=${node.name} latency=${latency}ms")
                Log.d("PERF", "probe cost node=${node.name} latency=${latency}ms")
                return ProbeResult.Available(latency, code)
            }
            val error = IllegalStateException("http_${code ?: -1}")
            Log.e("PROBE", "unexpected response node=${node.name} response=$responseLine", error)
            throw error
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
