package com.example.proxypotps.network

import android.util.Log
import com.example.proxypotps.domain.model.NodeStatus
import com.example.proxypotps.domain.model.ProxyNode
import java.net.Proxy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

@Singleton
class LocalProxyProbe @Inject constructor(
    private val clientProvider: OkHttpClientProvider
) {
    suspend fun probe(node: ProxyNode, probeUrl: String, timeoutSeconds: Long): ProxyNode {
        val proxyPort = node.localProxyPort
        if (proxyPort == null) {
            Log.e("PROBE", "missing local proxy port for node=${node.name}")
            return node.copy(status = NodeStatus.UNAVAILABLE, latencyMs = null)
        }
        return withContext(Dispatchers.IO) {
            val request = Request.Builder().url(probeUrl).get().build()
            val proxyType = if (node.localProxyType.uppercase() == "SOCKS") Proxy.Type.SOCKS else Proxy.Type.HTTP
            val client = clientProvider.getClient(proxyType, node.localProxyHost, proxyPort, timeoutSeconds)
            val start = System.currentTimeMillis()
            val result = runCatching {
                client.newCall(request).execute().use { response -> response.isSuccessful }
            }
            val latency = System.currentTimeMillis() - start
            if (result.isSuccess && result.getOrDefault(false)) {
                Log.i("PROBE", "probe success node=${node.name} proxy=${node.localProxyHost}:$proxyPort latency=${latency}ms")
                node.copy(status = NodeStatus.AVAILABLE, latencyMs = latency)
            } else {
                val message = result.exceptionOrNull()?.message
                Log.w("PROBE", "probe failed node=${node.name} proxy=${node.localProxyHost}:$proxyPort err=$message")
                node.copy(status = NodeStatus.UNAVAILABLE, latencyMs = null)
            }
        }
    }
}
