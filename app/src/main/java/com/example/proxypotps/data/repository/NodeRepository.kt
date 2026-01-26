package com.example.proxypotps.data.repository

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

    suspend fun replaceNodes(nodes: List<ProxyNode>) {
        nodeDao.clearAll()
        nodeDao.insertAll(nodes.map { it.toEntity(json) })
    }

    suspend fun updateStatus(nodeId: Long, status: NodeStatus, latencyMs: Long?) {
        nodeDao.updateStatus(nodeId, status.name, latencyMs)
    }
}

private fun NodeEntity.toDomain(json: Json): ProxyNode {
    val extrasMap = runCatching {
        json.parseToJsonElement(extrasJson).jsonObject.mapValues { it.value.toString().trim('"') }
    }.getOrDefault(emptyMap())
    val parsedStatus = runCatching { NodeStatus.valueOf(status) }.getOrDefault(NodeStatus.UNKNOWN)
    return ProxyNode(
        id = id,
        name = name,
        type = type,
        server = server,
        port = port,
        extras = extrasMap,
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
        extrasJson = extrasJson,
        status = status.name,
        latencyMs = latencyMs
    )
}
