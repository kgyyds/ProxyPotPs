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
            return node.copy(status = NodeStatus.UNAVAILABLE, latencyMs = null)
        }
        return withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            Log.i(
                "PROBE",
                "PROBE_START nodeId=${node.id} node=${node.name} type=${node.type} url=$probeUrl proxy=${node.localProxyHost}:$proxyPort"
            )
            logStage(verboseLogs, "PROBE_START_DETAIL", node, "timeout=${timeoutSeconds}s")
            val request = Request.Builder().url(probeUrl).get().build()
            val proxyType = if (node.localProxyType.uppercase() == "SOCKS") Proxy.Type.SOCKS else Proxy.Type.HTTP
            val client = clientProvider.getClient(proxyType, node.localProxyHost, proxyPort, timeoutSeconds)
            val call = client.newCall(request)
            val result = runCatching {
                withTimeout(timeoutSeconds * 1000) {
                    call.await().use { response -> response.isSuccessful }
                }
            }
            val latency = System.currentTimeMillis() - start
            if (result.isSuccess && result.getOrDefault(false)) {
                Log.i("PROBE", "PROBE_SUCCESS nodeId=${node.id} node=${node.name} latency=${latency}ms")
                node.copy(status = NodeStatus.AVAILABLE, latencyMs = latency)
            } else {
                val error = result.exceptionOrNull()
                val timeout = error is SocketTimeoutException || error is TimeoutCancellationException
                val status = if (timeout) NodeStatus.TIMEOUT else NodeStatus.UNAVAILABLE
                val tag = if (timeout) "PROBE_TIMEOUT" else "PROBE_FAIL"
                Log.w(
                    "PROBE",
                    "$tag nodeId=${node.id} node=${node.name} proxy=${node.localProxyHost}:$proxyPort err=${error?.message}"
                )
                node.copy(status = status, latencyMs = null)
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
