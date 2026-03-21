package com.example.proxypotps.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class StressTestType {
    @SerialName("slow_loris")
    SLOW_LORIS,
    
    @SerialName("http_flood") 
    HTTP_FLOOD,
    
    @SerialName("post_flood")
    POST_FLOOD
}

@Serializable
data class StressTestConfig(
    val testId: String,
    val testName: String,
    val targetType: StressTestType,
    val targetUrl: String,
    val concurrentConnections: Int = 10,
    val connectionTimeoutSeconds: Int = 30,
    val requestIntervalSeconds: Double = 1.0,
    val maxRounds: Int = -1, // -1 for infinite
    val selectedNodeIds: List<Long> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class StressTestResult(
    val testId: String,
    val nodeId: Long,
    val nodeName: String,
    val round: Int,
    val activeConnections: Int,
    val totalConnections: Int,
    val successfulConnections: Int,
    val failedConnections: Int,
    val timeoutConnections: Int,
    val responseCodeDistribution: Map<Int, Int>,
    val avgConnectionTimeMs: Long,
    val startTime: Long,
    val endTime: Long? = null,
    val status: StressTestStatus = StressTestStatus.RUNNING
)

@Serializable
enum class StressTestStatus {
    @SerialName("running")
    RUNNING,
    
    @SerialName("completed")
    COMPLETED,
    
    @SerialName("cancelled")
    CANCELLED,
    
    @SerialName("failed")
    FAILED
}

@Serializable
data class SlowLorisAttackConfig(
    val headersToSend: List<String> = listOf(
        "Host",
        "User-Agent", 
        "Accept",
        "Accept-Language",
        "Accept-Encoding",
        "Connection"
    ),
    val bytesPerSecond: Int = 1, // Send 1 byte per second for slow loris
    val keepAlive: Boolean = true
)

@Serializable
data class StressTestSummary(
    val testId: String,
    val totalActiveConnections: Int,
    val totalSuccessfulConnections: Int,
    val totalFailedConnections: Int,
    val totalTimeoutConnections: Int,
    val overallSuccessRate: Double,
    val avgResponseTimeMs: Long,
    val currentRound: Int,
    val status: StressTestStatus,
    val nodeResults: List<StressTestResult>
)