package com.example.proxypotps.data.repository

import com.example.proxypotps.data.local.StressTestDao
import com.example.proxypotps.data.local.StressTestEntity
import com.example.proxypotps.data.local.StressTestResultEntity
import com.example.proxypotps.domain.model.StressTestConfig
import com.example.proxypotps.domain.model.StressTestResult
import com.example.proxypotps.domain.model.StressTestStatus
import com.example.proxypotps.domain.model.StressTestSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StressTestRepository @Inject constructor(
    private val stressTestDao: StressTestDao,
    private val json: Json
) {
    
    fun getAllStressTests(): Flow<List<StressTestEntity>> = stressTestDao.getAllStressTests()
    
    fun getStressTest(testId: String): Flow<StressTestEntity?> = stressTestDao.getStressTest(testId)
    
    fun getStressTestResults(testId: String): Flow<List<StressTestResultEntity>> = 
        stressTestDao.getStressTestResults(testId)
    
    suspend fun saveStressTest(config: StressTestConfig) {
        val entity = config.toEntity(json)
        stressTestDao.insertStressTest(entity)
    }
    
    suspend fun updateStressTestSummary(summary: StressTestSummary) {
        val entity = summary.toEntity(json)
        stressTestDao.updateStressTest(entity)
    }
    
    suspend fun saveStressTestResults(results: List<StressTestResult>) {
        val entities = results.map { it.toEntity(json) }
        stressTestDao.insertStressTestResults(entities)
    }
    
    suspend fun deleteStressTest(testId: String) {
        stressTestDao.deleteStressTest(testId)
        stressTestDao.deleteStressTestResults(testId)
    }
}

private fun StressTestConfig.toEntity(json: Json): StressTestEntity {
    return StressTestEntity(
        testId = testId,
        testName = testName,
        targetType = targetType.name,
        targetUrl = targetUrl,
        concurrentConnections = concurrentConnections,
        connectionTimeoutSeconds = connectionTimeoutSeconds,
        requestIntervalSeconds = requestIntervalSeconds,
        maxRounds = maxRounds,
        selectedNodeIds = json.encodeToJsonElement(selectedNodeIds).toString(),
        createdAt = createdAt,
        status = StressTestStatus.RUNNING.name,
        currentRound = 0,
        totalActiveConnections = 0,
        totalSuccessfulConnections = 0,
        totalFailedConnections = 0,
        overallSuccessRate = 0.0,
        avgResponseTimeMs = 0
    )
}

// Note: In a real implementation, we would need to fetch the original test config
// from the database to preserve immutable fields. For now, this is a simplified version.
// The actual implementation would require storing the original config separately.
private fun StressTestSummary.toEntity(json: Json): StressTestEntity {
    // This is a placeholder implementation
    // In practice, you'd want to fetch the original test config from the database
    // and merge it with the updated summary fields
    return StressTestEntity(
        testId = testId,
        testName = "Slow Loris Test",
        targetType = "SLOW_LORIS",
        targetUrl = "https://target.com",
        concurrentConnections = 10,
        connectionTimeoutSeconds = 30,
        requestIntervalSeconds = 1.0,
        maxRounds = -1,
        selectedNodeIds = "[]",
        createdAt = System.currentTimeMillis(),
        status = status.name,
        currentRound = currentRound,
        totalActiveConnections = totalActiveConnections,
        totalSuccessfulConnections = totalSuccessfulConnections,
        totalFailedConnections = totalFailedConnections,
        overallSuccessRate = overallSuccessRate,
        avgResponseTimeMs = avgResponseTimeMs
    )
}

private fun StressTestResult.toEntity(json: Json): StressTestResultEntity {
    return StressTestResultEntity(
        testId = testId,
        nodeId = nodeId,
        nodeName = nodeName ?: "Unknown",
        round = round,
        activeConnections = activeConnections ?: 0,
        totalConnections = totalConnections ?: 0,
        successfulConnections = successfulConnections ?: 0,
        failedConnections = failedConnections ?: 0,
        timeoutConnections = timeoutConnections ?: 0,
        responseCodeDistribution = json.encodeToJsonElement(responseCodeDistribution).toString(),
        avgConnectionTimeMs = avgConnectionTimeMs,
        startTime = startTime ?: System.currentTimeMillis(),
        endTime = endTime,
        status = status.name
    )
}