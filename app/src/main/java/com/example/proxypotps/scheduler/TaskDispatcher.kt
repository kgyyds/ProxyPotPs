package com.example.proxypotps.scheduler

import android.os.SystemClock
import android.util.Log
import com.example.proxypotps.data.repository.NodeRepository
import com.example.proxypotps.data.repository.TaskRepository
import com.example.proxypotps.domain.model.NodeStatus
import com.example.proxypotps.domain.model.JobStatus
import com.example.proxypotps.domain.model.RunTaskRequest
import com.example.proxypotps.domain.model.RunTaskResponse
import com.example.proxypotps.domain.model.SubTaskRequest
import com.example.proxypotps.domain.model.SubTaskResult
import com.example.proxypotps.domain.model.TaskStatus
import com.example.proxypotps.network.ApiHttpClient
import com.example.proxypotps.network.LocalProxyManager
import com.example.proxypotps.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Singleton
class TaskDispatcher @Inject constructor(
    private val nodeRepository: NodeRepository,
    private val taskRepository: TaskRepository,
    private val apiHttpClient: ApiHttpClient,
    private val localProxyManager: LocalProxyManager,
    @ApplicationScope private val scope: CoroutineScope
) {
    suspend fun runMainTask(request: RunTaskRequest): RunTaskResponse {
        val start = SystemClock.elapsedRealtime()
        Log.d("TASK", "start mainTask=${request.mainTaskId} subTasks=${request.subTasks.size}")
        val nodesWithProxy = localProxyManager.ensureProxies(nodeRepository.getNodes())
        nodesWithProxy.forEach { node ->
            nodeRepository.updateLocalProxy(node.id, node.localProxyHost, node.localProxyPort, node.localProxyType)
        }
        val nodes = nodesWithProxy.filter { it.status == NodeStatus.AVAILABLE && it.localProxyPort != null }
        if (nodes.isEmpty()) {
            Log.e("TASK", "no available nodes for mainTask=${request.mainTaskId}")
            val taskCreation = taskRepository.createTask(request.mainTaskId, request.subTasks)
            val endTime = System.currentTimeMillis()
            val results = request.subTasks.map { subTask ->
                SubTaskResult(
                    subTaskId = subTask.subTaskId,
                    status = TaskStatus.FAILED,
                    httpCode = null,
                    data = "no_node",
                    nodeName = null,
                    startTime = endTime,
                    endTime = endTime,
                    durationMs = 0,
                    retryCount = 0,
                    resultSizeBytes = 0,
                    errorMessage = "no_node",
                    responsePreview = "no_node"
                )
            }
            results.forEach { result ->
                val rowId = taskCreation.subTaskRowIds[result.subTaskId]
                if (rowId != null) {
                    taskRepository.updateSubTaskResult(rowId, result)
                }
            }
            taskRepository.updateTaskMetrics(
                taskId = taskCreation.taskId,
                completedCount = request.subTasks.size,
                endTime = endTime,
                totalDuration = endTime - taskCreation.startTime,
                status = JobStatus.FAILED,
                successCount = 0,
                failCount = request.subTasks.size,
                timeoutCount = 0,
                nodeUsedCount = 0
            )
            return RunTaskResponse(mainTaskId = request.mainTaskId, results = results)
        }

        val taskCreation = taskRepository.createTask(request.mainTaskId, request.subTasks)
        val workers = nodes.associateWith { node ->
            NodeWorker(scope, apiHttpClient, node)
        }
        val completedCounter = AtomicInteger(0)
        val successCounter = AtomicInteger(0)
        val failCounter = AtomicInteger(0)
        val timeoutCounter = AtomicInteger(0)
        val nodeUsage = ConcurrentHashMap.newKeySet<String>()

        val results = withTimeout(60_000) {
            request.subTasks.mapIndexed { index, subTask ->
                scope.async {
                    val firstNode = nodes[index % nodes.size]
                    val result = executeWithRetry(subTask, firstNode, nodes, workers, index)
                    val rowId = taskCreation.subTaskRowIds[subTask.subTaskId]
                    if (rowId != null) {
                        taskRepository.updateSubTaskResult(rowId, result)
                    }
                    val usedNode = nodes.firstOrNull { it.name == result.nodeName }
                    if (usedNode != null) {
                        Log.d(
                            "TASK",
                            "subtask=${subTask.subTaskId} node=${usedNode.name} proxy=${usedNode.localProxyHost}:${usedNode.localProxyPort}"
                        )
                    }
                    when (result.status) {
                        TaskStatus.OK -> successCounter.incrementAndGet()
                        TaskStatus.FAILED -> failCounter.incrementAndGet()
                        TaskStatus.TIMEOUT -> timeoutCounter.incrementAndGet()
                    }
                    result.nodeName?.let { nodeUsage.add(it) }
                    val completed = completedCounter.incrementAndGet()
                    val endTime = if (completed == request.subTasks.size) System.currentTimeMillis() else null
                    val totalDuration = endTime?.let { it - taskCreation.startTime }
                    val status = calculateJobStatus(
                        completed = completed,
                        total = request.subTasks.size,
                        successCount = successCounter.get(),
                        failCount = failCounter.get(),
                        timeoutCount = timeoutCounter.get()
                    )
                    taskRepository.updateTaskMetrics(
                        taskId = taskCreation.taskId,
                        completedCount = completed,
                        endTime = endTime,
                        totalDuration = totalDuration,
                        status = status,
                        successCount = successCounter.get(),
                        failCount = failCounter.get(),
                        timeoutCount = timeoutCounter.get(),
                        nodeUsedCount = nodeUsage.size
                    )
                    result
                }
            }.awaitAll()
        }

        val elapsed = SystemClock.elapsedRealtime() - start
        Log.d("TASK", "finish mainTask=${request.mainTaskId} cost=${elapsed}ms")
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
        index: Int
    ): SubTaskResult {
        val firstResult = workers.getValue(firstNode)
            .submit(subTask, 8)
            .copy(retryCount = 0)
        if (firstResult.status == TaskStatus.OK) return firstResult
        val fallbackNode = if (nodes.size > 1) {
            nodes[(index + 1) % nodes.size]
        } else {
            firstNode
        }
        if (fallbackNode.id == firstNode.id) return firstResult
        return workers.getValue(fallbackNode)
            .submit(subTask, 8)
            .copy(retryCount = 1)
    }

    private fun calculateJobStatus(
        completed: Int,
        total: Int,
        successCount: Int,
        failCount: Int,
        timeoutCount: Int
    ): JobStatus {
        if (completed < total) return JobStatus.RUNNING
        if (successCount == total) return JobStatus.SUCCESS
        if (successCount > 0 && (failCount > 0 || timeoutCount > 0)) return JobStatus.PARTIAL
        return JobStatus.FAILED
    }
}
