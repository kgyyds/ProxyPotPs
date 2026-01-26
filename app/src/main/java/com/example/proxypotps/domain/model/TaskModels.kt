package com.example.proxypotps.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RunTaskRequest(
    val mainTaskId: String,
    val subTasks: List<SubTaskRequest>
)

@Serializable
data class SubTaskRequest(
    val subTaskId: String,
    val url: String,
    val method: HttpMethod = HttpMethod.GET,
    val params: Map<String, String> = emptyMap()
)

@Serializable
data class RunTaskResponse(
    val mainTaskId: String,
    val results: List<SubTaskResult>
)

@Serializable
data class SubTaskResult(
    val subTaskId: String,
    val status: TaskStatus,
    val httpCode: Int? = null,
    val data: String? = null,
    val nodeName: String? = null,
    val startTime: Long? = null,
    val endTime: Long? = null,
    val durationMs: Long? = null,
    val retryCount: Int = 0,
    val resultSizeBytes: Long = 0,
    val errorMessage: String? = null,
    val responsePreview: String? = null
)

@Serializable
enum class HttpMethod {
    @SerialName("GET")
    GET,
    @SerialName("POST")
    POST
}

@Serializable
enum class TaskStatus {
    @SerialName("ok")
    OK,
    @SerialName("failed")
    FAILED,
    @SerialName("timeout")
    TIMEOUT
}

enum class JobStatus {
    RUNNING,
    SUCCESS,
    PARTIAL,
    FAILED
}

enum class SubTaskStatus {
    SUCCESS,
    FAIL,
    TIMEOUT
}
