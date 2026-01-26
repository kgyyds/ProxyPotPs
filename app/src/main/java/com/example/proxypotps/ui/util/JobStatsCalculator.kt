package com.example.proxypotps.ui.util

import com.example.proxypotps.data.local.TaskWithSubTasks
import com.example.proxypotps.domain.model.JobStatus
import com.example.proxypotps.domain.model.SubTaskStatus
import com.example.proxypotps.domain.model.TaskStatus
import javax.inject.Inject

class JobStatsCalculator @Inject constructor() {
    fun calculate(taskWithSubTasks: TaskWithSubTasks): JobStatsResult {
        val subTasks = taskWithSubTasks.subTasks
        val successCount = subTasks.count { it.status == TaskStatus.OK.name }
        val failCount = subTasks.count { it.status == TaskStatus.FAILED.name }
        val timeoutCount = subTasks.count { it.status == TaskStatus.TIMEOUT.name }
        val totalCount = taskWithSubTasks.task.totalCount
        val successRate = if (totalCount == 0) 0f else successCount.toFloat() / totalCount.toFloat()

        val jobStatus = JobStatus.entries.firstOrNull { it.name == taskWithSubTasks.task.status }
            ?: calculateJobStatus(successCount, failCount, timeoutCount, totalCount, taskWithSubTasks.task.completedCount)

        val jobStart = taskWithSubTasks.task.startTime
        val timeline = subTasks.mapNotNull { subTask ->
            val startTime = subTask.startTime
            val duration = subTask.durationMs
            if (startTime != null && duration != null) {
                TimeLineEntry(
                    offsetMs = (startTime - jobStart).toFloat(),
                    durationMs = duration.toFloat()
                )
            } else {
                null
            }
        }.sortedBy { it.offsetMs }

        val nodeLatency = subTasks
            .filter { it.nodeName != null && it.durationMs != null }
            .groupBy { it.nodeName.orEmpty() }
            .map { (node, items) ->
                val avg = items.mapNotNull { it.durationMs }.average().toFloat()
                NodeLatencyEntry(node = node, avgLatencyMs = avg)
            }
            .sortedByDescending { it.avgLatencyMs }

        return JobStatsResult(
            jobStatus = jobStatus,
            successRate = successRate,
            successCount = successCount,
            failCount = failCount,
            timeoutCount = timeoutCount,
            nodeUsedCount = nodeLatency.size,
            totalCount = totalCount,
            totalDuration = taskWithSubTasks.task.totalDuration,
            chartData = JobChartData(
                successCount = successCount,
                failCount = failCount,
                timeoutCount = timeoutCount,
                nodeLatency = nodeLatency,
                timeline = timeline
            )
        )
    }

    private fun calculateJobStatus(
        successCount: Int,
        failCount: Int,
        timeoutCount: Int,
        totalCount: Int,
        completedCount: Int
    ): JobStatus {
        if (completedCount < totalCount) return JobStatus.RUNNING
        if (successCount == totalCount) return JobStatus.SUCCESS
        if (successCount > 0 && (failCount > 0 || timeoutCount > 0)) return JobStatus.PARTIAL
        return JobStatus.FAILED
    }
}

data class JobStatsResult(
    val jobStatus: JobStatus,
    val successRate: Float,
    val successCount: Int,
    val failCount: Int,
    val timeoutCount: Int,
    val nodeUsedCount: Int,
    val totalCount: Int,
    val totalDuration: Long?,
    val chartData: JobChartData
)

data class JobChartData(
    val successCount: Int = 0,
    val failCount: Int = 0,
    val timeoutCount: Int = 0,
    val nodeLatency: List<NodeLatencyEntry> = emptyList(),
    val timeline: List<TimeLineEntry> = emptyList()
)

data class NodeLatencyEntry(
    val node: String,
    val avgLatencyMs: Float
)

data class TimeLineEntry(
    val offsetMs: Float,
    val durationMs: Float
)

data class SubTaskDisplay(
    val subId: String,
    val url: String,
    val method: String,
    val assignedNode: String?,
    val startTime: Long?,
    val endTime: Long?,
    val durationMs: Long?,
    val retryCount: Int,
    val resultSizeBytes: Long,
    val status: SubTaskStatus,
    val errorMessage: String?,
    val responsePreview: String?
)
