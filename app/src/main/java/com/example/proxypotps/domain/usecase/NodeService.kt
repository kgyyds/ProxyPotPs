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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
        return probeAllWithUrl(settings.probeUrl)
    }

    suspend fun probeAllWithUrl(probeUrl: String): List<ProxyNode> {
        val nodes = nodeRepository.getNodes()
        val proxiedNodes = localProxyManager.ensureProxies(nodes)
        proxiedNodes.forEach { node ->
            nodeRepository.updateLocalProxy(node.id, node.localProxyHost, node.localProxyPort, node.localProxyType)
        }
        val results = coroutineScope {
            proxiedNodes.map { node ->
                async(Dispatchers.IO) {
                    localProxyProbe.probe(node, probeUrl, 8)
                }
            }.awaitAll()
        }
        results.forEach { result ->
            nodeRepository.updateStatus(result.id, result.status, result.latencyMs)
        }
        return results
    }

    suspend fun resetStatuses() {
        val nodes = nodeRepository.getNodes()
        nodes.forEach { node ->
            nodeRepository.updateStatus(node.id, NodeStatus.UNKNOWN, null)
        }
    }
}
