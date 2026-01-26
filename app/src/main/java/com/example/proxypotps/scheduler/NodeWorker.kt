package com.example.proxypotps.scheduler

import com.example.proxypotps.domain.model.SubTaskRequest
import com.example.proxypotps.domain.model.SubTaskResult
import com.example.proxypotps.network.ApiHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred

class NodeWorker(
    private val scope: CoroutineScope,
    private val apiHttpClient: ApiHttpClient,
    val nodeName: String
) {
    private val channel = Channel<WorkItem>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (item in channel) {
                val result = apiHttpClient.execute(
                    request = item.request,
                    proxyHost = item.proxyHost,
                    proxyPort = item.proxyPort,
                    timeoutSeconds = item.timeoutSeconds
                ).copy(nodeName = nodeName)
                item.result.complete(result)
            }
        }
    }

    suspend fun submit(
        request: SubTaskRequest,
        proxyHost: String,
        proxyPort: Int,
        timeoutSeconds: Long
    ): SubTaskResult {
        val deferred = CompletableDeferred<SubTaskResult>()
        channel.send(WorkItem(request, proxyHost, proxyPort, timeoutSeconds, deferred))
        return deferred.await()
    }

    data class WorkItem(
        val request: SubTaskRequest,
        val proxyHost: String,
        val proxyPort: Int,
        val timeoutSeconds: Long,
        val result: CompletableDeferred<SubTaskResult>
    )
}
