package com.example.proxypotps.network

import android.util.Log
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
            val request = Request.Builder().url(probeUrl).get().build()
            val proxyTypes = listOf(Proxy.Type.HTTP, Proxy.Type.SOCKS)
            var lastError: Throwable? = null

            for (proxyType in proxyTypes) {
                val proxy = Proxy(proxyType, InetSocketAddress(clashHost, clashPort))
                val client = OkHttpClient.Builder()
                    .proxy(proxy)
                    .callTimeout(java.time.Duration.ofSeconds(8))
                    .connectTimeout(java.time.Duration.ofSeconds(8))
                    .readTimeout(java.time.Duration.ofSeconds(8))
                    .writeTimeout(java.time.Duration.ofSeconds(8))
                    .build()
                val start = System.currentTimeMillis()
                val result = runCatching {
                    client.newCall(request).execute().use { response ->
                        response.isSuccessful
                    }
                }
                val latency = System.currentTimeMillis() - start
                if (result.isSuccess && result.getOrDefault(false)) {
                    Log.i("ClashProbe", "Probe success for ${node.name} via $proxyType $clashHost:$clashPort ($latency ms)")
                    return@withContext node.copy(status = NodeStatus.AVAILABLE, latencyMs = latency)
                }
                lastError = result.exceptionOrNull()
                Log.w("ClashProbe", "Probe failed for ${node.name} via $proxyType $clashHost:$clashPort: ${lastError?.message}")
            }
            Log.e("ClashProbe", "All probe attempts failed for ${node.name}: ${lastError?.message}")
            node.copy(status = NodeStatus.UNAVAILABLE, latencyMs = null)
        }
    }
}
