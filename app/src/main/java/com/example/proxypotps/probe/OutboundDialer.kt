package com.example.proxypotps.probe

interface OutboundDialer {
    suspend fun openTunnel(
        node: ProbeNode,
        destHost: String,
        destPort: Int,
        timeoutMs: Long
    ): SocketLike
}
