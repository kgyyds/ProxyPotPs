package com.example.proxypotps.data.repository

import com.example.proxypotps.data.local.SubTaskEntity
import com.example.proxypotps.data.local.TaskDao
import com.example.proxypotps.data.local.TaskEntity
import com.example.proxypotps.data.local.TaskWithSubTasks
import com.example.proxypotps.domain.model.SubTaskRequest
import com.example.proxypotps.domain.model.SubTaskResult
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

    suspend fun createTask(mainTaskId: String, subTasks: List<SubTaskRequest>): TaskCreation {
        val taskId = taskDao.insertTask(
            TaskEntity(
                mainTaskId = mainTaskId,
                startedAt = System.currentTimeMillis(),
                finishedAt = null,
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
                durationMs = null,
                httpCode = null,
                responseData = null
            )
        }
        val rowIds = taskDao.insertSubTasks(subTaskEntities)
        val mapping = subTasks.mapIndexed { index, subTask -> subTask.subTaskId to rowIds[index] }.toMap()
        return TaskCreation(taskId = taskId, subTaskRowIds = mapping)
    }

    suspend fun updateSubTaskResult(subTaskRowId: Long, result: SubTaskResult) {
        taskDao.updateSubTaskResult(
            subTaskRowId = subTaskRowId,
            status = result.status.name,
            nodeName = result.nodeName,
            durationMs = result.durationMs,
            httpCode = result.httpCode,
            responseData = result.data
        )
    }

    suspend fun updateTaskProgress(taskId: Long, completedCount: Int, finishedAt: Long?) {
        taskDao.updateTaskProgress(taskId, completedCount, finishedAt)
    }
}

data class TaskCreation(
    val taskId: Long,
    val subTaskRowIds: Map<String, Long>
)
