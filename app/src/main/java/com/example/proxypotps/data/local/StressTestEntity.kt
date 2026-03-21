package com.example.proxypotps.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.proxypotps.domain.model.StressTestStatus
import com.example.proxypotps.domain.model.StressTestType

@Entity(tableName = "stress_tests")
data class StressTestEntity(
    @PrimaryKey val testId: String,
    @ColumnInfo(name = "test_name") val testName: String,
    @ColumnInfo(name = "target_type") val targetType: String,
    @ColumnInfo(name = "target_url") val targetUrl: String,
    @ColumnInfo(name = "concurrent_connections") val concurrentConnections: Int,
    @ColumnInfo(name = "connection_timeout_seconds") val connectionTimeoutSeconds: Int,
    @ColumnInfo(name = "request_interval_seconds") val requestIntervalSeconds: Double,
    @ColumnInfo(name = "max_rounds") val maxRounds: Int,
    @ColumnInfo(name = "selected_node_ids") val selectedNodeIds: String, // JSON array
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "current_round") val currentRound: Int,
    @ColumnInfo(name = "total_active_connections") val totalActiveConnections: Int,
    @ColumnInfo(name = "total_successful_connections") val totalSuccessfulConnections: Int,
    @ColumnInfo(name = "total_failed_connections") val totalFailedConnections: Int,
    @ColumnInfo(name = "overall_success_rate") val overallSuccessRate: Double,
    @ColumnInfo(name = "avg_response_time_ms") val avgResponseTimeMs: Long
)

@Entity(tableName = "stress_test_results")
data class StressTestResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "test_id") val testId: String,
    @ColumnInfo(name = "node_id") val nodeId: Long,
    @ColumnInfo(name = "node_name") val nodeName: String,
    @ColumnInfo(name = "round") val round: Int,
    @ColumnInfo(name = "active_connections") val activeConnections: Int,
    @ColumnInfo(name = "total_connections") val totalConnections: Int,
    @ColumnInfo(name = "successful_connections") val successfulConnections: Int,
    @ColumnInfo(name = "failed_connections") val failedConnections: Int,
    @ColumnInfo(name = "timeout_connections") val timeoutConnections: Int,
    @ColumnInfo(name = "response_code_distribution") val responseCodeDistribution: String, // JSON
    @ColumnInfo(name = "avg_connection_time_ms") val avgConnectionTimeMs: Long,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long?,
    @ColumnInfo(name = "status") val status: String
)