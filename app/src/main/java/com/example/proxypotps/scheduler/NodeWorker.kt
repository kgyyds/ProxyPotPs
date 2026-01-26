package com.example.proxypotps.scheduler

import com.example.proxypotps.domain.model.ProxyNode
import com.example.proxypotps.domain.model.SubTaskRequest
import com.example.proxypotps.domain.model.SubTaskResult
import com.example.proxypotps.network.ApiHttpClient
import java.net.Proxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred

class NodeWorker(
    private val scope: CoroutineScope,
    private val apiHttpClient: ApiHttpClient,
    private val node: ProxyNode
) {
    private val channel = Channel<WorkItem>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (item in channel) {
                val result = apiHttpClient.execute(
                    request = item.request,
                    proxyHost = item.proxyHost,
                    proxyPort = item.proxyPort,
                    proxyType = item.proxyType,
                    timeoutSeconds = item.timeoutSeconds
                ).copy(nodeName = node.name)
                item.result.complete(result)
            }
        }
    }

    suspend fun submit(
        request: SubTaskRequest,
        timeoutSeconds: Long
    ): SubTaskResult {
        val deferred = CompletableDeferred<SubTaskResult>()
        val proxyHost = node.localProxyHost
        val proxyPort = node.localProxyPort ?: error("Missing proxy port for node=${node.name}")
        val proxyType = when (node.localProxyType.uppercase()) {
            "SOCKS" -> Proxy.Type.SOCKS
            else -> Proxy.Type.HTTP
        }
        channel.send(WorkItem(request, proxyHost, proxyPort, proxyType, timeoutSeconds, deferred))
        return deferred.await()
    }

    data class WorkItem(
        val request: SubTaskRequest,
        val proxyHost: String,
        val proxyPort: Int,
        val proxyType: Proxy.Type,
        val timeoutSeconds: Long,
        val result: CompletableDeferred<SubTaskResult>
    )
}
