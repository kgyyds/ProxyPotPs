package com.example.proxypotps.domain.usecase

import android.util.Log
import com.example.proxypotps.data.repository.NodeRepository
import com.example.proxypotps.data.repository.SettingsRepository
import com.example.proxypotps.domain.model.NodeStatus
import com.example.proxypotps.domain.model.ProxyNode
import com.example.proxypotps.probe.ProbeManager
import com.example.proxypotps.probe.ProbeNode
import com.example.proxypotps.probe.ProbeResult
import com.example.proxypotps.util.YamlParser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

@Singleton
class NodeService @Inject constructor(
    private val nodeRepository: NodeRepository,
    private val settingsRepository: SettingsRepository,
    private val probeManager: ProbeManager
) {
    fun observeNodes(): Flow<List<ProxyNode>> = nodeRepository.observeNodes()

    suspend fun parseAndStore(yamlText: String) {
        val nodes = YamlParser.parseProxyNodes(yamlText)
        nodeRepository.replaceNodes(nodes)
    }

    suspend fun probeAll(): List<ProxyNode> {
        val settings = settingsRepository.settingsFlow.first()
        val nodes = nodeRepository.getNodes()
        val probeNodes = nodes.map { it.toProbeNode() }
        val results = probeManager.probeAll(probeNodes, settings.probeUrl, 8_000)
        results.forEach { (probeNode, result) ->
            val (status, latencyMs) = when (result) {
                is ProbeResult.Available -> NodeStatus.AVAILABLE to result.latencyMs.toLong()
                is ProbeResult.Timeout -> NodeStatus.TIMEOUT to null
                is ProbeResult.Unavailable -> NodeStatus.UNAVAILABLE to null
            }
            if (result is ProbeResult.Unavailable) {
                Log.w("NodeService", "Probe unavailable ${probeNode.name}: ${result.reason}")
            }
            nodeRepository.updateStatus(probeNode.id, status, latencyMs)
        }
        return nodes.map { node ->
            val match = results.firstOrNull { it.first.id == node.id }?.second
            when (match) {
                is ProbeResult.Available -> node.copy(status = NodeStatus.AVAILABLE, latencyMs = match.latencyMs.toLong())
                is ProbeResult.Timeout -> node.copy(status = NodeStatus.TIMEOUT, latencyMs = null)
                is ProbeResult.Unavailable -> node.copy(status = NodeStatus.UNAVAILABLE, latencyMs = null)
                else -> node
            }
        }
    }

    suspend fun resetStatuses() {
        val nodes = nodeRepository.getNodes()
        nodes.forEach { node ->
            nodeRepository.updateStatus(node.id, NodeStatus.UNKNOWN, null)
        }
    }
}

private fun ProxyNode.toProbeNode(): ProbeNode {
    val grpcServiceName = extras["grpc-service-name"] ?: extras["grpc-opts.grpc-service-name"]
        ?: extras["grpc-opts"]?.substringAfter("grpc-service-name=")?.substringBefore(",")
    return ProbeNode(
        id = id,
        name = name,
        type = type,
        server = server,
        port = port,
        cipher = extras["cipher"],
        password = extras["password"],
        sni = extras["sni"],
        network = extras["network"],
        grpcServiceName = grpcServiceName
    )
}
