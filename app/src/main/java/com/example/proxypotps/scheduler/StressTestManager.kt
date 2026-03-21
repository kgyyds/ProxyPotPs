package com.example.proxypotps.scheduler

import android.util.Log
import com.example.proxypotps.data.repository.NodeRepository
import com.example.proxypotps.data.repository.StressTestRepository
import com.example.proxypotps.domain.model.*
import com.example.proxypotps.network.ApiHttpClient
import com.example.proxypotps.network.LocalProxyManager
import com.example.proxypotps.scheduler.attacks.SlowLorisAttack
import com.example.proxypotps.di.ApplicationScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.net.Proxy
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StressTestManager @Inject constructor(
    private val nodeRepository: NodeRepository,
    private val stressTestRepository: StressTestRepository,
    private val apiHttpClient: ApiHttpClient,
    private val localProxyManager: LocalProxyManager,
    @ApplicationScope private val scope: CoroutineScope
) {
    private val _testResults = MutableSharedFlow<StressTestSummary>()
    val testResults = _testResults.asSharedFlow()
    
    private val activeTests = mutableMapOf<String, Job>()
    
    suspend fun startStressTest(config: StressTestConfig): String {
        if (activeTests.containsKey(config.testId)) {
            throw IllegalStateException("Test with ID ${config.testId} is already running")
        }
        
        // Save test configuration to database
        stressTestRepository.saveStressTest(config)
        
        val job = scope.launch {
            runStressTest(config)
        }
        
        activeTests[config.testId] = job
        return config.testId
    }
    
    suspend fun stopStressTest(testId: String) {
        val job = activeTests.remove(testId)
        job?.cancel()
        
        // Update test status in database
        // Send final result
        _testResults.emit(StressTestSummary(
            testId = testId,
            totalActiveConnections = 0,
            totalSuccessfulConnections = 0,
            totalFailedConnections = 0,
            totalTimeoutConnections = 0,
            overallSuccessRate = 0.0,
            avgResponseTimeMs = 0,
            currentRound = 0,
            status = StressTestStatus.CANCELLED,
            nodeResults = emptyList()
        ))
    }
    
    private suspend fun runStressTest(config: StressTestConfig) {
        try {
            Log.d("STRESS_TEST", "Starting stress test: ${config.testId}")
            
            // Get available nodes
            val nodes = localProxyManager.ensureProxies(nodeRepository.getNodes())
                .filter { it.status == NodeStatus.AVAILABLE && it.localProxyPort != null }
                .filter { config.selectedNodeIds.isEmpty() || config.selectedNodeIds.contains(it.id) }
            
            if (nodes.isEmpty()) {
                Log.e("STRESS_TEST", "No available nodes for stress test: ${config.testId}")
                _testResults.emit(StressTestSummary(
                    testId = config.testId,
                    totalActiveConnections = 0,
                    totalSuccessfulConnections = 0,
                    totalFailedConnections = 0,
                    totalTimeoutConnections = 0,
                    overallSuccessRate = 0.0,
                    avgResponseTimeMs = 0,
                    currentRound = 0,
                    status = StressTestStatus.FAILED,
                    nodeResults = emptyList()
                ))
                return
            }
            
            var currentRound = 1
            var shouldContinue = true
            
            while (shouldContinue) {
                Log.d("STRESS_TEST", "Starting round $currentRound for test: ${config.testId}")
                
                // Launch attacks for each node
                val nodeResults = nodes.map { node ->
                    async {
                        try {
                            val proxyHost = node.localProxyHost
                            val proxyPort = node.localProxyPort ?: error("Missing proxy port for node=${node.name}")
                            val proxyType = when (node.localProxyType.uppercase()) {
                                "SOCKS" -> Proxy.Type.SOCKS
                                else -> Proxy.Type.HTTP
                            }
                            
                            val attack = SlowLorisAttack(
                                config = config,
                                httpClient = apiHttpClient,
                                proxyHost = proxyHost,
                                proxyPort = proxyPort,
                                proxyType = proxyType,
                                nodeId = node.id,
                                nodeName = node.name
                            )
                            
                            attack.execute(currentRound)
                        } catch (e: Exception) {
                            Log.e("STRESS_TEST", "Attack failed for node ${node.name}", e)
                            StressTestResult(
                                testId = config.testId,
                                nodeId = node.id,
                                nodeName = node.name,
                                round = currentRound,
                                activeConnections = 0,
                                totalConnections = 0,
                                successfulConnections = 0,
                                failedConnections = 1,
                                timeoutConnections = 0,
                                responseCodeDistribution = emptyMap(),
                                avgConnectionTimeMs = 0,
                                startTime = System.currentTimeMillis(),
                                endTime = System.currentTimeMillis(),
                                status = StressTestStatus.FAILED
                            )
                        }
                    }
                }
                
                // Wait for all attacks to complete
                val results = nodeResults.awaitAll()
                
                // Save results to database
                stressTestRepository.saveStressTestResults(results)
                
                // Calculate summary
                val summary = createSummary(config.testId, currentRound, results)
                // Update summary in database
                stressTestRepository.updateStressTestSummary(summary)
                _testResults.emit(summary)
                
                // Check if we should continue
                if (config.maxRounds > 0 && currentRound >= config.maxRounds) {
                    shouldContinue = false
                }
                
                currentRound++
                
                // Wait before next round if continuing
                if (shouldContinue) {
                    delay((config.requestIntervalSeconds * 1000).toLong())
                }
            }
            
            Log.d("STRESS_TEST", "Stress test completed: ${config.testId}")
            
        } catch (e: CancellationException) {
            Log.d("STRESS_TEST", "Stress test cancelled: ${config.testId}")
            throw e
        } catch (e: Exception) {
            Log.e("STRESS_TEST", "Stress test failed: ${config.testId}", e)
            _testResults.emit(StressTestSummary(
                testId = config.testId,
                totalActiveConnections = 0,
                totalSuccessfulConnections = 0,
                totalFailedConnections = 0,
                totalTimeoutConnections = 0,
                overallSuccessRate = 0.0,
                avgResponseTimeMs = 0,
                currentRound = 0,
                status = StressTestStatus.FAILED,
                nodeResults = emptyList()
            ))
        } finally {
            activeTests.remove(config.testId)
        }
    }
    
    private fun createSummary(
        testId: String,
        currentRound: Int,
        nodeResults: List<StressTestResult>
    ): StressTestSummary {
        val totalActive = nodeResults.sumOf { it.activeConnections ?: 0 }
        val totalSuccessful = nodeResults.sumOf { it.successfulConnections ?: 0 }
        val totalFailed = nodeResults.sumOf { it.failedConnections ?: 0 }
        val totalTimeout = nodeResults.sumOf { it.timeoutConnections ?: 0 }
        val totalConnections = nodeResults.sumOf { it.totalConnections ?: 0 }
        
        val successRate = if (totalConnections > 0) {
            totalSuccessful.toDouble() / totalConnections.toDouble()
        } else {
            0.0
        }
        
        val avgResponseTime = if (nodeResults.isNotEmpty()) {
            nodeResults.sumOf { it.avgConnectionTimeMs } / nodeResults.size
        } else {
            0L
        }
        
        val status = if (nodeResults.all { it.status == StressTestStatus.COMPLETED }) {
            StressTestStatus.COMPLETED
        } else if (nodeResults.any { it.status == StressTestStatus.FAILED }) {
            StressTestStatus.FAILED
        } else {
            StressTestStatus.RUNNING
        }
        
        return StressTestSummary(
            testId = testId,
            totalActiveConnections = totalActive,
            totalSuccessfulConnections = totalSuccessful,
            totalFailedConnections = totalFailed,
            totalTimeoutConnections = totalTimeout,
            overallSuccessRate = successRate,
            avgResponseTimeMs = avgResponseTime,
            currentRound = currentRound,
            status = status,
            nodeResults = nodeResults
        )
    }
}