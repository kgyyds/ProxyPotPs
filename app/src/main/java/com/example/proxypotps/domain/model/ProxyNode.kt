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
    val localProxyHost: String = "127.0.0.1",
    val localProxyPort: Int? = null,
    val localProxyType: String = "HTTP",
    val status: NodeStatus = NodeStatus.UNKNOWN,
    val latencyMs: Long? = null
)

enum class NodeStatus {
    AVAILABLE,
    PROBING,
    TIMEOUT,
    UNAVAILABLE,
    UNKNOWN
}
