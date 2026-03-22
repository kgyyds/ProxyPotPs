package com.example.proxypotps.network

import android.util.Log
import com.example.proxypotps.domain.model.NodeStatus
import com.example.proxypotps.domain.model.ProxyNode
import java.net.Proxy
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class LocalProxyProbe @Inject constructor(
    private val clientProvider: OkHttpClientProvider
) {
    suspend fun probe(
        node: ProxyNode,
        probeUrl: String,
        timeoutSeconds: Long,
        verboseLogs: Boolean
    ): ProxyNode {
        val proxyPort = node.localProxyPort
        if (proxyPort == null) {
            Log.e("PROBE", "missing local proxy port for node=${node.name}")
            return node.copy(status = NodeStatus.UNAVAILABLE, latencyMs = null, statusReason = "MISSING_LOCAL_PROXY_PORT")
        }
        return withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            Log.i(
                "PROBE",
                "PROBE_START nodeId=${node.id} node=${node.name} type=${node.type} url=$probeUrl proxy=${node.localProxyHost}:$proxyPort"
            )
            logStage(verboseLogs, "PROBE_START_DETAIL", node, "timeout=${timeoutSeconds}s url=$probeUrl")
            val request = Request.Builder().url(probeUrl).get().build()
            val proxyType = if (node.localProxyType.uppercase() == "SOCKS") Proxy.Type.SOCKS else Proxy.Type.HTTP
            Log.d("PROBE", "Creating HTTP client proxyType=$proxyType host=${node.localProxyHost}:$proxyPort")
            val client = clientProvider.getClient(proxyType, node.localProxyHost, proxyPort, timeoutSeconds)
            val call = client.newCall(request)
            var errorMessage: String? = null
            var errorDetail: String? = null
            var statusReason: String? = null
            val result = runCatching {
                withTimeout(timeoutSeconds * 1000) {
                    call.await().use { response ->
                        logStage(verboseLogs, "HTTP_RESPONSE", node, "code=${response.code} headers=${response.headers}")
                        if (!response.isSuccessful) {
                            val errorCode = response.header("X-ProxyPot-Error-Code")
                            val errorReason = response.header("X-ProxyPot-Error-Detail")
                            if (!errorCode.isNullOrBlank() && !errorReason.isNullOrBlank()) {
                                statusReason = "$errorCode:$errorReason"
                            }
                        }
                        response.isSuccessful
                    }
                }
            }
            val latency = System.currentTimeMillis() - start
            if (result.isSuccess && result.getOrDefault(false)) {
                Log.i("PROBE", "PROBE_SUCCESS nodeId=${node.id} node=${node.name} latency=${latency}ms")
                node.copy(status = NodeStatus.AVAILABLE, latencyMs = latency, statusReason = null)
            } else {
                val error = result.exceptionOrNull()
                errorMessage = error?.message ?: "unknown_error"
                errorDetail = error?.let { Log.getStackTraceString(it) }
                val timeout = error is SocketTimeoutException || error is TimeoutCancellationException
                val status = if (timeout) NodeStatus.TIMEOUT else NodeStatus.UNAVAILABLE
                if (statusReason == null && !timeout) {
                    statusReason = "PROBE_ERROR:$errorMessage"
                }
                val tag = if (timeout) "PROBE_TIMEOUT" else "PROBE_FAIL"
                Log.w(
                    "PROBE",
                    "$tag nodeId=${node.id} node=${node.name} proxy=${node.localProxyHost}:$proxyPort err=${errorMessage}"
                )
                if (verboseLogs) {
                    Log.e("PROBE", "PROBE_ERROR_DETAIL nodeId=${node.id} node=${node.name}", Exception(errorDetail))
                }
                node.copy(status = status, latencyMs = null, statusReason = statusReason)
            }
        }
    }

    private fun logStage(verbose: Boolean, stage: String, node: ProxyNode, message: String) {
        if (!verbose) return
        Log.d("PROBE", "$stage nodeId=${node.id} node=${node.name} type=${node.type} $message")
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isCancelled) return
                cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                cont.resume(response)
            }
        })
    }
}
