package com.example.proxypotps.scheduler.attacks

import android.util.Log
import com.example.proxypotps.domain.model.StressTestConfig
import com.example.proxypotps.domain.model.StressTestResult
import com.example.proxypotps.domain.model.StressTestStatus
import com.example.proxypotps.network.ApiHttpClient
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.InetSocketAddress
import java.net.Proxy
import java.nio.charset.Charset
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean

class SlowLorisAttack(
    private val config: StressTestConfig,
    private val httpClient: ApiHttpClient,
    private val proxyHost: String,
    private val proxyPort: Int,
    private val proxyType: Proxy.Type,
    private val nodeId: Long,
    private val nodeName: String
) {
    private val activeConnections = AtomicInteger(0)
    private val totalConnections = AtomicInteger(0)
    private val successfulConnections = AtomicInteger(0)
    private val failedConnections = AtomicInteger(0)
    private val timeoutConnections = AtomicInteger(0)
    private val responseCodeDistribution = ConcurrentHashMap<Int, AtomicInteger>()
    private val totalConnectionTime = AtomicLong(0)
    
    private val isRunning = AtomicBoolean(true)
    
    suspend fun execute(round: Int): StressTestResult {
        // For slow loris, we want to maintain connections for the duration
        // rather than making discrete requests
        val jobs = mutableListOf<Job>()
        
        repeat(config.concurrentConnections) { connectionId ->
            val job = CoroutineScope(Dispatchers.IO).launch {
                maintainSlowConnection(connectionId, round)
            }
            jobs.add(job)
        }
        
        // Keep connections alive for the specified duration
        delay(config.connectionTimeoutSeconds * 1000L)
        
        // Cancel all connections
        jobs.forEach { it.cancel() }
        
        // Wait a bit for cleanup
        delay(500)
        
        return createResult(round)
    }
    
    private suspend fun maintainSlowConnection(connectionId: Int, round: Int) {
        if (!isRunning.get()) return
        
        val start = System.currentTimeMillis()
        activeConnections.incrementAndGet()
        totalConnections.incrementAndGet()
        
        try {
            // Create a slow HTTP request that keeps the connection open
            val url = config.targetUrl
            
            // Use the existing ApiHttpClient with custom timeout
            val result = httpClient.execute(
                request = createSubTaskRequest(url, connectionId),
                proxyHost = proxyHost,
                proxyPort = proxyPort,
                proxyType = proxyType,
                timeoutSeconds = config.connectionTimeoutSeconds.toLong()
            )
            
            // Record the result
            if (result.status.name == "OK") {
                successfulConnections.incrementAndGet()
                result.httpCode?.let { code ->
                    responseCodeDistribution.computeIfAbsent(code) { AtomicInteger(0) }
                        .incrementAndGet()
                }
            } else {
                failedConnections.incrementAndGet()
                if (result.errorMessage?.contains("timeout") == true) {
                    timeoutConnections.incrementAndGet()
                }
            }
            
        } catch (e: Exception) {
            Log.e("SLOW_LORIS", "Connection failed for node=$nodeName, connection=$connectionId", e)
            failedConnections.incrementAndGet()
            if (e is java.net.SocketTimeoutException) {
                timeoutConnections.incrementAndGet()
            }
        } finally {
            activeConnections.decrementAndGet()
            val duration = System.currentTimeMillis() - start
            totalConnectionTime.addAndGet(duration)
        }
    }
    
    private fun createSubTaskRequest(url: String, connectionId: Int): com.example.proxypotps.domain.model.SubTaskRequest {
        // Create parameters that simulate a slow request
        val params = mutableMapOf<String, String>()
        params["X-Client-ID"] = "slow-loris-$connectionId"
        params["X-Round"] = "$connectionId"
        params["X-Test-ID"] = config.testId
        
        return com.example.proxypotps.domain.model.SubTaskRequest(
            subTaskId = "slow_loris_conn_$connectionId",
            url = url,
            method = com.example.proxypotps.domain.model.HttpMethod.GET,
            params = params,
            taskType = com.example.proxypotps.domain.model.TaskType.STRESS_TEST
        )
    }
    
    private fun createResult(round: Int): StressTestResult {
        val totalConn = totalConnections.get()
        val successRate = if (totalConn > 0) {
            successfulConnections.get().toDouble() / totalConn.toDouble()
        } else {
            0.0
        }
        
        val avgConnectionTime = if (totalConn > 0) {
            totalConnectionTime.get() / totalConn
        } else {
            0L
        }
        
        return StressTestResult(
            testId = config.testId,
            nodeId = nodeId,
            nodeName = nodeName,
            round = round,
            activeConnections = activeConnections.get(),
            totalConnections = totalConn,
            successfulConnections = successfulConnections.get(),
            failedConnections = failedConnections.get(),
            timeoutConnections = timeoutConnections.get(),
            responseCodeDistribution = responseCodeDistribution.mapValues { it.value.get() },
            avgConnectionTimeMs = avgConnectionTime,
            startTime = System.currentTimeMillis() - (config.connectionTimeoutSeconds * 1000L),
            endTime = System.currentTimeMillis(),
            status = if (isRunning.get()) StressTestStatus.RUNNING else StressTestStatus.COMPLETED
        )
    }
    
    fun stop() {
        isRunning.set(false)
    }
}