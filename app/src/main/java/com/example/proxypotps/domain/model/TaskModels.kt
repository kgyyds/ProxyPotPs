package com.example.proxypotps.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class HttpMethod {
    @SerialName("GET")
    GET,
    @SerialName("POST")
    POST
}

@Serializable
data class SubTaskRequest(
    val subTaskId: String,
    val url: String,
    val method: HttpMethod = HttpMethod.GET,
    val params: Map<String, String> = emptyMap(),
    val taskType: TaskType = TaskType.NORMAL
)

@Serializable
enum class TaskType {
    @SerialName("normal")
    NORMAL,

    @SerialName("stress_test")
    STRESS_TEST
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
    val responsePreview: String? = null,
    val taskType: TaskType = TaskType.NORMAL
)
