package com.example.proxypotps.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ProxyNode(
    val id: Long = 0,
    val name: String,
    val type: String,
    val server: String,
    val port: Int,
    val extras: Map<String, String> = emptyMap(),
    val status: NodeStatus = NodeStatus.UNKNOWN,
    val latencyMs: Long? = null
)

enum class NodeStatus {
    AVAILABLE,
    TIMEOUT,
    UNAVAILABLE,
    UNKNOWN
}
