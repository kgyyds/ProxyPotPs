package com.example.proxypotps.domain.usecase

import com.example.proxypotps.data.repository.NodeRepository
import com.example.proxypotps.data.repository.SettingsRepository
import com.example.proxypotps.domain.model.NodeStatus
import com.example.proxypotps.domain.model.ProxyNode
import com.example.proxypotps.network.ClashProbe
import com.example.proxypotps.util.YamlParser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope

@Singleton
class NodeService @Inject constructor(
    private val nodeRepository: NodeRepository,
    private val settingsRepository: SettingsRepository,
    private val clashProbe: ClashProbe
) {
    fun observeNodes(): Flow<List<ProxyNode>> = nodeRepository.observeNodes()

    suspend fun parseAndStore(yamlText: String) {
        val nodes = YamlParser.parseProxyNodes(yamlText)
        nodeRepository.replaceNodes(nodes)
    }

    suspend fun probeAll(): List<ProxyNode> {
        val settings = settingsRepository.settingsFlow.first()
        val nodes = nodeRepository.getNodes()
        val semaphore = Semaphore(10)
        val updated = withContext(Dispatchers.IO) {
            coroutineScope {
                nodes.map { node ->
                    async {
                        semaphore.withPermit {
                            clashProbe.probe(node, settings.clashHost, settings.clashPort, settings.probeUrl)
                        }
                    }
                }.awaitAll()
            }
        }
        updated.forEach { node ->
            nodeRepository.updateStatus(node.id, node.status, node.latencyMs)
        }
        return updated
    }

    suspend fun resetStatuses() {
        val nodes = nodeRepository.getNodes()
        nodes.forEach { node ->
            nodeRepository.updateStatus(node.id, NodeStatus.UNKNOWN, null)
        }
    }
}
