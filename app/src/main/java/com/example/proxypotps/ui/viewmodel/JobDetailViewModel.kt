package com.example.proxypotps.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proxypotps.data.repository.TaskRepository
import com.example.proxypotps.domain.model.JobStatus
import com.example.proxypotps.domain.model.SubTaskStatus
import com.example.proxypotps.domain.model.TaskStatus
import com.example.proxypotps.ui.util.JobChartData
import com.example.proxypotps.ui.util.JobStatsCalculator
import com.example.proxypotps.ui.util.JobStatsResult
import com.example.proxypotps.ui.util.SubTaskDisplay
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

@HiltViewModel
class JobDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val taskRepository: TaskRepository,
    private val statsCalculator: JobStatsCalculator
) : ViewModel() {
    private val taskId: Long = checkNotNull(savedStateHandle["taskId"])

    val uiState: StateFlow<JobDetailUiState> = taskRepository.observeTask(taskId)
        .map { taskWithSubTasks ->
            withContext(Dispatchers.IO) {
                if (taskWithSubTasks == null) {
                    JobDetailUiState(isLoading = false, errorMessage = "任务不存在")
                } else {
                    val stats = statsCalculator.calculate(taskWithSubTasks)
                    val subTasks = taskWithSubTasks.subTasks.map { entity ->
                        SubTaskDisplay(
                            subId = entity.subTaskId,
                            url = entity.url,
                            method = entity.method,
                            assignedNode = entity.nodeName,
                            startTime = entity.startTime,
                            endTime = entity.endTime,
                            durationMs = entity.durationMs,
                            retryCount = entity.retryCount,
                            resultSizeBytes = entity.resultSizeBytes,
                            status = when (entity.status) {
                                TaskStatus.OK.name -> SubTaskStatus.SUCCESS
                                TaskStatus.TIMEOUT.name -> SubTaskStatus.TIMEOUT
                                else -> SubTaskStatus.FAIL
                            },
                            errorMessage = entity.errorMessage,
                            responsePreview = entity.responsePreview
                        )
                    }
                    JobDetailUiState(
                        isLoading = false,
                        jobSummary = JobSummary(
                            jobId = taskWithSubTasks.task.mainTaskId,
                            startTime = taskWithSubTasks.task.startTime,
                            endTime = taskWithSubTasks.task.endTime,
                            totalDuration = taskWithSubTasks.task.totalDuration,
                            status = stats.jobStatus,
                            successCount = stats.successCount,
                            failCount = stats.failCount,
                            timeoutCount = stats.timeoutCount,
                            nodeUsedCount = stats.nodeUsedCount,
                            totalCount = stats.totalCount,
                            successRate = stats.successRate
                        ),
                        chartData = stats.chartData,
                        subTasks = subTasks.sortedByDescending { it.startTime ?: 0L }
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), JobDetailUiState())
}

data class JobDetailUiState(
    val isLoading: Boolean = true,
    val jobSummary: JobSummary? = null,
    val chartData: JobChartData = JobChartData(),
    val subTasks: List<SubTaskDisplay> = emptyList(),
    val errorMessage: String? = null
)

data class JobSummary(
    val jobId: String,
    val startTime: Long,
    val endTime: Long?,
    val totalDuration: Long?,
    val status: JobStatus,
    val successCount: Int,
    val failCount: Int,
    val timeoutCount: Int,
    val nodeUsedCount: Int,
    val totalCount: Int,
    val successRate: Float
)
