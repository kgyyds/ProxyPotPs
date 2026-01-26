package com.example.proxypotps.data.repository

import com.example.proxypotps.data.local.SubTaskEntity
import com.example.proxypotps.data.local.TaskDao
import com.example.proxypotps.data.local.TaskEntity
import com.example.proxypotps.data.local.TaskWithSubTasks
import com.example.proxypotps.domain.model.SubTaskRequest
import com.example.proxypotps.domain.model.SubTaskResult
import com.example.proxypotps.domain.model.JobStatus
import com.example.proxypotps.domain.model.TaskStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement

@Singleton
class TaskRepository @Inject constructor(
    private val taskDao: TaskDao,
    private val json: Json
) {
    fun observeTasks(): Flow<List<TaskWithSubTasks>> = taskDao.observeTasksWithSubTasks()

    fun observeTask(taskId: Long): Flow<TaskWithSubTasks?> = taskDao.observeTaskWithSubTasks(taskId)

    suspend fun createTask(mainTaskId: String, subTasks: List<SubTaskRequest>): TaskCreation {
        val startTime = System.currentTimeMillis()
        val taskId = taskDao.insertTask(
            TaskEntity(
                mainTaskId = mainTaskId,
                startTime = startTime,
                endTime = null,
                totalDuration = null,
                status = JobStatus.RUNNING.name,
                successCount = 0,
                failCount = 0,
                timeoutCount = 0,
                nodeUsedCount = 0,
                totalCount = subTasks.size,
                completedCount = 0
            )
        )
        val subTaskEntities = subTasks.map { request ->
            SubTaskEntity(
                taskId = taskId,
                subTaskId = request.subTaskId,
                url = request.url,
                method = request.method.name,
                paramsJson = json.encodeToJsonElement(request.params).toString(),
                status = TaskStatus.FAILED.name,
                nodeName = null,
                startTime = null,
                endTime = null,
                durationMs = null,
                retryCount = 0,
                resultSizeBytes = 0,
                httpCode = null,
                errorMessage = null,
                responsePreview = null
            )
        }
        val rowIds = taskDao.insertSubTasks(subTaskEntities)
        val mapping = subTasks.mapIndexed { index, subTask -> subTask.subTaskId to rowIds[index] }.toMap()
        return TaskCreation(taskId = taskId, subTaskRowIds = mapping, startTime = startTime)
    }

    suspend fun updateSubTaskResult(subTaskRowId: Long, result: SubTaskResult) {
        taskDao.updateSubTaskResult(
            subTaskRowId = subTaskRowId,
            status = result.status.name,
            nodeName = result.nodeName,
            startTime = result.startTime,
            endTime = result.endTime,
            durationMs = result.durationMs,
            retryCount = result.retryCount,
            resultSizeBytes = result.resultSizeBytes,
            httpCode = result.httpCode,
            errorMessage = result.errorMessage,
            responsePreview = result.responsePreview
        )
    }

    suspend fun updateTaskMetrics(
        taskId: Long,
        completedCount: Int,
        endTime: Long?,
        totalDuration: Long?,
        status: JobStatus,
        successCount: Int,
        failCount: Int,
        timeoutCount: Int,
        nodeUsedCount: Int
    ) {
        taskDao.updateTaskMetrics(
            taskId = taskId,
            completedCount = completedCount,
            endTime = endTime,
            totalDuration = totalDuration,
            status = status.name,
            successCount = successCount,
            failCount = failCount,
            timeoutCount = timeoutCount,
            nodeUsedCount = nodeUsedCount
        )
    }
}

data class TaskCreation(
    val taskId: Long,
    val subTaskRowIds: Map<String, Long>,
    val startTime: Long
)
