package com.example.proxypotps.network

import java.net.InetSocketAddress
import java.net.Proxy
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Singleton
class OkHttpClientProvider @Inject constructor() {
    private val cache = ConcurrentHashMap<ClientKey, OkHttpClient>()

    fun getClient(proxyType: Proxy.Type, host: String, port: Int, timeoutSeconds: Long): OkHttpClient {
        val key = ClientKey(proxyType, host, port, timeoutSeconds)
        return cache.getOrPut(key) {
            OkHttpClient.Builder()
                .proxy(Proxy(proxyType, InetSocketAddress(host, port)))
                .callTimeout(Duration.ofSeconds(timeoutSeconds))
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .readTimeout(Duration.ofSeconds(timeoutSeconds))
                .writeTimeout(Duration.ofSeconds(timeoutSeconds))
                .build()
        }
    }
}

private data class ClientKey(
    val proxyType: Proxy.Type,
    val host: String,
    val port: Int,
    val timeoutSeconds: Long
)
