package com.example.proxypotps.scheduler

import com.example.proxypotps.data.repository.NodeRepository
import com.example.proxypotps.data.repository.SettingsRepository
import com.example.proxypotps.data.repository.TaskRepository
import com.example.proxypotps.domain.model.NodeStatus
import com.example.proxypotps.domain.model.RunTaskRequest
import com.example.proxypotps.domain.model.RunTaskResponse
import com.example.proxypotps.domain.model.SubTaskRequest
import com.example.proxypotps.domain.model.SubTaskResult
import com.example.proxypotps.domain.model.TaskStatus
import com.example.proxypotps.network.ApiHttpClient
import com.example.proxypotps.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger

@Singleton
class TaskDispatcher @Inject constructor(
    private val nodeRepository: NodeRepository,
    private val settingsRepository: SettingsRepository,
    private val taskRepository: TaskRepository,
    private val apiHttpClient: ApiHttpClient,
    @ApplicationScope private val scope: CoroutineScope
) {
    suspend fun runMainTask(request: RunTaskRequest): RunTaskResponse {
        val settings = settingsRepository.settingsFlow.first()
        val nodes = nodeRepository.getNodes().filter { it.status == NodeStatus.AVAILABLE }
        if (nodes.isEmpty()) {
            return RunTaskResponse(
                mainTaskId = request.mainTaskId,
                results = request.subTasks.map {
                    SubTaskResult(
                        subTaskId = it.subTaskId,
                        status = TaskStatus.FAILED,
                        httpCode = null,
                        data = "no_node",
                        nodeName = null,
                        durationMs = null
                    )
                }
            )
        }

        val taskCreation = taskRepository.createTask(request.mainTaskId, request.subTasks)
        val workers = nodes.associateWith { node ->
            NodeWorker(scope, apiHttpClient, node.name)
        }
        val completedCounter = AtomicInteger(0)

        val results = withTimeout(60_000) {
            request.subTasks.mapIndexed { index, subTask ->
                scope.async {
                    val firstNode = nodes[index % nodes.size]
                    val result = executeWithRetry(subTask, firstNode, nodes, workers, settings.clashHost, settings.clashPort)
                    val rowId = taskCreation.subTaskRowIds[subTask.subTaskId]
                    if (rowId != null) {
                        taskRepository.updateSubTaskResult(rowId, result)
                    }
                    val completed = completedCounter.incrementAndGet()
                    taskRepository.updateTaskProgress(taskCreation.taskId, completed, if (completed == request.subTasks.size) System.currentTimeMillis() else null)
                    result
                }
            }.awaitAll()
        }

        return RunTaskResponse(
            mainTaskId = request.mainTaskId,
            results = results
        )
    }

    private suspend fun executeWithRetry(
        subTask: SubTaskRequest,
        firstNode: com.example.proxypotps.domain.model.ProxyNode,
        nodes: List<com.example.proxypotps.domain.model.ProxyNode>,
        workers: Map<com.example.proxypotps.domain.model.ProxyNode, NodeWorker>,
        proxyHost: String,
        proxyPort: Int
    ): SubTaskResult {
        val firstResult = workers.getValue(firstNode).submit(subTask, proxyHost, proxyPort, 8)
        if (firstResult.status == TaskStatus.OK) return firstResult
        val fallbackNode = nodes.firstOrNull { it.id != firstNode.id } ?: firstNode
        if (fallbackNode.id == firstNode.id) return firstResult
        return workers.getValue(fallbackNode).submit(subTask, proxyHost, proxyPort, 8)
    }
}
