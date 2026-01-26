package com.example.proxypotps.probe

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

@Singleton
class ProbeManager @Inject constructor(
    private val nodeProber: NodeProber
) {
    private val semaphore = Semaphore(8)

    suspend fun probeAll(nodes: List<ProbeNode>, probeUrl: String, timeoutMs: Long): List<Pair<ProbeNode, ProbeResult>> {
        return withContext(Dispatchers.IO) {
            coroutineScope {
                nodes.map { node ->
                    async {
                        semaphore.withPermit {
                            node to nodeProber.probe(node, probeUrl, timeoutMs)
                        }
                    }
                }.awaitAll()
            }
        }
    }
}
