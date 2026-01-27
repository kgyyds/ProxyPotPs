package com.example.proxypotps.domain.usecase

import com.example.proxypotps.data.repository.NodeRepository
import com.example.proxypotps.data.repository.SettingsRepository
import com.example.proxypotps.domain.model.NodeStatus
import com.example.proxypotps.domain.model.ProxyNode
import com.example.proxypotps.network.LocalProxyManager
import com.example.proxypotps.network.LocalProxyProbe
import com.example.proxypotps.util.YamlParser
import javax.inject.Inject
import javax.inject.Singleton
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

@Singleton
class NodeService @Inject constructor(
    private val nodeRepository: NodeRepository,
    private val settingsRepository: SettingsRepository,
    private val localProxyManager: LocalProxyManager,
    private val localProxyProbe: LocalProxyProbe
) {
    fun observeNodes(): Flow<List<ProxyNode>> = nodeRepository.observeNodes()

    suspend fun parseAndStore(yamlText: String) {
        val nodes = withContext(Dispatchers.IO) {
            YamlParser.parseProxyNodes(yamlText)
        }
        val storedNodes = nodeRepository.replaceNodes(nodes)
        val withProxies = localProxyManager.ensureProxies(storedNodes)
        withProxies.forEach { node ->
            nodeRepository.updateLocalProxy(node.id, node.localProxyHost, node.localProxyPort, node.localProxyType)
        }
    }

    suspend fun probeAll(): List<ProxyNode> {
        val settings = settingsRepository.settingsFlow.first()
        return probeAllWithUrl(settings.probeUrl, settings.verboseProbeLogs)
    }

    suspend fun probeAllWithUrl(
        probeUrl: String,
        verboseLogs: Boolean,
        onProgress: (ProbeProgress) -> Unit = {}
    ): List<ProxyNode> {
        val nodes = nodeRepository.getNodes()
        Log.i("PROBE", "probeAllWithUrl start count=${nodes.size} url=$probeUrl")
        if (nodes.isEmpty()) {
            onProgress(ProbeProgress(total = 0, completed = 0, inProgress = false))
            return emptyList()
        }
        val proxiedNodes = localProxyManager.ensureProxies(nodes)
        proxiedNodes.forEach { node ->
            nodeRepository.updateLocalProxy(node.id, node.localProxyHost, node.localProxyPort, node.localProxyType)
        }
        proxiedNodes.forEach { node ->
            nodeRepository.updateStatus(node.id, NodeStatus.PROBING, null)
        }
        val total = proxiedNodes.size
        val completed = AtomicInteger(0)
        onProgress(ProbeProgress(total = total, completed = 0, inProgress = true))
        val semaphore = Semaphore(8)
        val results = try {
            coroutineScope {
                proxiedNodes.map { node ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            val result = localProxyProbe.probe(node, probeUrl, 8, verboseLogs)
                            nodeRepository.updateStatus(result.id, result.status, result.latencyMs)
                            val done = completed.incrementAndGet()
                            onProgress(ProbeProgress(total = total, completed = done, inProgress = true))
                            result
                        }
                    }
                }.awaitAll()
            }
        } catch (error: CancellationException) {
            Log.w("PROBE", "probe cancelled, restoring pending nodes")
            restorePendingStatuses()
            onProgress(ProbeProgress(total = total, completed = completed.get(), inProgress = false))
            throw error
        } finally {
            onProgress(ProbeProgress(total = total, completed = completed.get(), inProgress = false))
        }
        return results
    }

    suspend fun probeSampleNodes(
        probeUrl: String,
        verboseLogs: Boolean,
        sampleSize: Int
    ): List<ProxyNode> {
        val nodes = nodeRepository.getNodes().take(sampleSize)
        if (nodes.isEmpty()) return emptyList()
        val proxiedNodes = localProxyManager.ensureProxies(nodes)
        proxiedNodes.forEach { node ->
            nodeRepository.updateLocalProxy(node.id, node.localProxyHost, node.localProxyPort, node.localProxyType)
        }
        val start = SystemClock.elapsedRealtime()
        Log.i("PROBE", "diagnostics start count=${proxiedNodes.size} url=$probeUrl")
        val results = coroutineScope {
            proxiedNodes.map { node ->
                async(Dispatchers.IO) { localProxyProbe.probe(node, probeUrl, 8, verboseLogs = true) }
            }.awaitAll()
        }
        val elapsed = SystemClock.elapsedRealtime() - start
        results.forEach { result ->
            Log.i("PROBE", "diagnostics result node=${result.name} status=${result.status} latency=${result.latencyMs}")
        }
        Log.i("PROBE", "diagnostics done elapsed=${elapsed}ms")
        return results
    }

    suspend fun resetStatuses() {
        val nodes = nodeRepository.getNodes()
        nodes.forEach { node ->
            nodeRepository.updateStatus(node.id, NodeStatus.UNKNOWN, null)
        }
    }

    private suspend fun restorePendingStatuses() {
        val nodes = nodeRepository.getNodes()
        nodes.filter { it.status == NodeStatus.PROBING }.forEach { node ->
            nodeRepository.updateStatus(node.id, NodeStatus.UNKNOWN, null)
        }
    }
}

data class ProbeProgress(
    val total: Int = 0,
    val completed: Int = 0,
    val inProgress: Boolean = false
)
