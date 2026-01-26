package com.example.proxypotps.network

import com.example.proxypotps.domain.model.NodeStatus
import com.example.proxypotps.domain.model.ProxyNode
import java.net.InetSocketAddress
import java.net.Proxy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class ClashProbe @Inject constructor() {
    suspend fun probe(node: ProxyNode, clashHost: String, clashPort: Int, probeUrl: String): ProxyNode {
        return withContext(Dispatchers.IO) {
            val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(clashHost, clashPort))
            val client = OkHttpClient.Builder()
                .proxy(proxy)
                .callTimeout(java.time.Duration.ofSeconds(8))
                .build()
            val request = Request.Builder().url(probeUrl).get().build()
            val start = System.currentTimeMillis()
            val result = runCatching {
                client.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            }
            val latency = System.currentTimeMillis() - start
            if (result.isSuccess && result.getOrDefault(false)) {
                node.copy(status = NodeStatus.AVAILABLE, latencyMs = latency)
            } else {
                node.copy(status = NodeStatus.UNAVAILABLE, latencyMs = null)
            }
        }
    }
}
