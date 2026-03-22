package com.example.proxypotps.data.repository

import android.util.Log
import com.example.proxypotps.data.local.NodeDao
import com.example.proxypotps.data.local.NodeEntity
import com.example.proxypotps.domain.model.NodeStatus
import com.example.proxypotps.domain.model.ProxyNode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

@Singleton
class NodeRepository @Inject constructor(
    private val nodeDao: NodeDao,
    private val json: Json
) {
    fun observeNodes(): Flow<List<ProxyNode>> = nodeDao.observeNodes().map { entities ->
        entities.map { it.toDomain(json) }
    }

    suspend fun getNodes(): List<ProxyNode> = nodeDao.getNodes().map { it.toDomain(json) }

    suspend fun replaceNodes(nodes: List<ProxyNode>): List<ProxyNode> {
        nodeDao.clearAll()
        nodeDao.insertAll(nodes.map { it.toEntity(json) })
        return getNodes()
    }

    suspend fun updateStatus(nodeId: Long, status: NodeStatus, latencyMs: Long?) {
        nodeDao.updateStatus(nodeId, status.name, latencyMs)
    }

    suspend fun updateLocalProxy(nodeId: Long, host: String, port: Int?, type: String) {
        nodeDao.updateLocalProxy(nodeId, host, port, type)
    }

    suspend fun getNode(nodeId: Long): ProxyNode? {
        return nodeDao.getNode(nodeId)?.toDomain(json)
    }
}

private fun NodeEntity.toDomain(json: Json): ProxyNode {
    val extrasMap = try {
        json.parseToJsonElement(extrasJson).jsonObject.mapValues { it.value.toString().trim('"') }
    } catch (error: Exception) {
        Log.e("YAML", "extras parse failed", error)
        throw error
    }
    val parsedStatus = NodeStatus.entries.firstOrNull { it.name == status } ?: NodeStatus.UNKNOWN
    return ProxyNode(
        id = id,
        name = name,
        type = type,
        server = server,
        port = port,
        extras = extrasMap,
        localProxyHost = localProxyHost,
        localProxyPort = localProxyPort,
        localProxyType = localProxyType,
        status = parsedStatus,
        latencyMs = latencyMs
    )
}

private fun ProxyNode.toEntity(json: Json): NodeEntity {
    val extrasJson = json.encodeToJsonElement(extras).toString()
    return NodeEntity(
        id = id,
        name = name,
        type = type,
        server = server,
        port = port,
        localProxyHost = localProxyHost,
        localProxyPort = localProxyPort,
        localProxyType = localProxyType,
        extrasJson = extrasJson,
        status = status.name,
        latencyMs = latencyMs
    )
}
