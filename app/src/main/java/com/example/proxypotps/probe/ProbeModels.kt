package com.example.proxypotps.probe

sealed class ProbeResult {
    data class Available(val latencyMs: Int, val httpCode: Int) : ProbeResult()
    data class Timeout(val reason: String = "timeout") : ProbeResult()
    data class Unavailable(val reason: String) : ProbeResult()
}

data class ProbeNode(
    val id: Long,
    val name: String,
    val type: String,
    val server: String,
    val port: Int,
    val cipher: String? = null,
    val password: String? = null,
    val sni: String? = null,
    val network: String? = null,
    val grpcServiceName: String? = null
)
