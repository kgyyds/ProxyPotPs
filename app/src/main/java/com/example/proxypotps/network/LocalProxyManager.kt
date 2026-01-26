package com.example.proxypotps.network

import android.util.Log
import com.example.proxypotps.domain.model.ProxyNode
import com.example.proxypotps.probe.SsOutboundDialer
import com.example.proxypotps.probe.TrojanOutboundDialer
import com.example.proxypotps.di.ApplicationScope
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class LocalProxyManager @Inject constructor(
    private val ssDialer: SsOutboundDialer,
    private val trojanDialer: TrojanOutboundDialer,
    @ApplicationScope private val scope: CoroutineScope
) {
    private val servers = ConcurrentHashMap<Long, LocalProxyServer>()

    suspend fun ensureProxies(nodes: List<ProxyNode>): List<ProxyNode> = withContext(Dispatchers.IO) {
        val activeIds = nodes.map { it.id }.toSet()
        val removeIds = servers.keys.filter { it !in activeIds }
        removeIds.forEach { id ->
            servers.remove(id)?.let { server ->
                Log.i("LOCAL_PROXY", "Stopping proxy for removed node id=${server.nodeId}")
                server.stop()
            }
        }

        nodes.map { node ->
            val existing = servers[node.id]
            if (existing != null && existing.isRunning) {
                node.copy(
                    localProxyHost = existing.host,
                    localProxyPort = existing.port,
                    localProxyType = existing.proxyType
                )
            } else {
                val server = LocalProxyServer(node, ssDialer, trojanDialer, scope)
                server.start()
                servers[node.id] = server
                Log.i("LOCAL_PROXY", "Started proxy node=${node.name} port=${server.port}")
                node.copy(
                    localProxyHost = server.host,
                    localProxyPort = server.port,
                    localProxyType = server.proxyType
                )
            }
        }
    }

    fun stopAll() {
        servers.values.forEach { it.stop() }
        servers.clear()
    }
}
