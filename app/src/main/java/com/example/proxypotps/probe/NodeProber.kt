package com.example.proxypotps.probe

import android.os.SystemClock
import android.util.Log
import java.net.URI
import kotlin.coroutines.coroutineContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.job
import kotlinx.coroutines.withTimeout

@Singleton
class NodeProber @Inject constructor(
    private val ssDialer: SsOutboundDialer,
    private val trojanDialer: TrojanOutboundDialer
) {
    suspend fun probe(
        node: ProbeNode,
        probeUrl: String,
        timeoutMs: Long,
        verboseLogs: Boolean = false
    ): ProbeResult {
        val start = SystemClock.elapsedRealtime()
        Log.i(
            "PROBE",
            "PROBE_START nodeId=${node.id} node=${node.name} type=${node.type} host=${node.server}:${node.port}"
        )
        logStage(verboseLogs, "PROBE_START_DETAIL", node, "timeoutMs=$timeoutMs url=$probeUrl")
        return try {
            withTimeout(timeoutMs) {
                probeInternal(node, probeUrl, timeoutMs, start, verboseLogs)
            }
        } catch (timeout: TimeoutCancellationException) {
            Log.e("PROBE", "PROBE_TIMEOUT nodeId=${node.id} node=${node.name}", timeout)
            ProbeResult.Timeout()
        } catch (cancelled: CancellationException) {
            Log.w("PROBE", "PROBE_CANCEL nodeId=${node.id} node=${node.name}", cancelled)
            throw cancelled
        } catch (error: Throwable) {
            Log.e("PROBE", "PROBE_FAIL nodeId=${node.id} node=${node.name}", error)
            throw error
        }
    }

    private suspend fun probeInternal(
        node: ProbeNode,
        probeUrl: String,
        timeoutMs: Long,
        startMs: Long,
        verboseLogs: Boolean
    ): ProbeResult {
        val normalizedUrl = normalizeProbeUrl(probeUrl, verboseLogs)
        val uri = try {
            URI(normalizedUrl)
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
            val cancelHandler = coroutineContext.job.invokeOnCompletion { socket.close() }
            try {
                val request = buildHttpRequest(host, path)
                try {
                    it.output.write(request)
                    it.output.flush()
                    logStage(verboseLogs, "HTTP_WRITE_OK", node, "host=$host port=$port")
                } catch (error: Exception) {
                    Log.e("PROBE", "HTTP_WRITE_FAIL node=${node.name}", error)
                    throw error
                }
                val responseLine = try {
                    readResponseLine(it.input)
                } catch (error: Exception) {
                    Log.e("PROBE", "READ_LINE_FAIL node=${node.name}", error)
                    throw error
                } ?: throw IllegalStateException("no_response")
                logStage(verboseLogs, "READ_LINE_OK", node, "line=$responseLine")
                val latency = (SystemClock.elapsedRealtime() - startMs).toInt()
                val code = parseStatusCode(responseLine)
                if (code != null && code in 200..399) {
                    Log.d("PROBE", "PROBE_SUCCESS nodeId=${node.id} node=${node.name} latency=${latency}ms")
                    Log.d("PERF", "probe cost node=${node.name} latency=${latency}ms")
                    return ProbeResult.Available(latency, code)
                }
                val error = IllegalStateException("http_${code ?: -1}")
                Log.e("PROBE", "unexpected response node=${node.name} response=$responseLine", error)
                throw error
            } finally {
                cancelHandler.dispose()
            }
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
            if (buffer.length > 1024) {
                throw IllegalStateException("response_line_too_long")
            }
        }
        return if (buffer.isNotEmpty()) buffer.toString() else null
    }

    private fun parseStatusCode(line: String): Int? {
        val parts = line.split(" ")
        if (parts.size < 2) return null
        return parts[1].toIntOrNull()
    }

    private fun normalizeProbeUrl(probeUrl: String, verboseLogs: Boolean): String {
        val uri = runCatching { URI(probeUrl) }.getOrNull()
        val scheme = uri?.scheme?.lowercase() ?: "http"
        if (scheme != "https") return probeUrl
        val fallback = "http://www.gstatic.com/generate_204"
        Log.w("PROBE", "https probe url not supported in direct tunnel, fallback to $fallback")
        if (verboseLogs) {
            Log.d("PROBE", "PROBE_FALLBACK url=$fallback")
        }
        return fallback
    }

    private fun logStage(verbose: Boolean, stage: String, node: ProbeNode, message: String) {
        if (!verbose) return
        Log.d("PROBE", "$stage nodeId=${node.id} node=${node.name} type=${node.type} $message")
    }
}
