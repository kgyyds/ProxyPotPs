package com.example.proxypotps.network

import android.util.Log
import com.example.proxypotps.domain.model.HttpMethod
import com.example.proxypotps.domain.model.SubTaskRequest
import com.example.proxypotps.domain.model.SubTaskResult
import com.example.proxypotps.domain.model.TaskStatus
import java.net.InetSocketAddress
import java.net.Proxy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class ApiHttpClient @Inject constructor(
    private val json: Json
) {
    private val mapSerializer = MapSerializer(String.serializer(), String.serializer())

    suspend fun execute(
        request: SubTaskRequest,
        proxyHost: String,
        proxyPort: Int,
        timeoutSeconds: Long
    ): SubTaskResult {
        return withContext(Dispatchers.IO) {
            val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyHost, proxyPort))
            val client = OkHttpClient.Builder()
                .proxy(proxy)
                .callTimeout(java.time.Duration.ofSeconds(timeoutSeconds))
                .build()
            val start = System.currentTimeMillis()
            try {
                val httpRequest = buildRequest(request)
                client.newCall(httpRequest).execute().use { response ->
                    val body = response.body?.string()
                    val end = System.currentTimeMillis()
                    val preview = body?.take(200)
                    SubTaskResult(
                        subTaskId = request.subTaskId,
                        status = if (response.isSuccessful) TaskStatus.OK else TaskStatus.FAILED,
                        httpCode = response.code,
                        data = body,
                        nodeName = null,
                        startTime = start,
                        endTime = end,
                        durationMs = end - start,
                        resultSizeBytes = body?.toByteArray()?.size?.toLong() ?: 0,
                        errorMessage = if (response.isSuccessful) null else body,
                        responsePreview = preview
                    )
                }
            } catch (throwable: Exception) {
                Log.e("TASK", "subTask failed ${request.subTaskId}", throwable)
                val status = if (throwable is java.net.SocketTimeoutException) {
                    TaskStatus.TIMEOUT
                } else {
                    TaskStatus.FAILED
                }
                val end = System.currentTimeMillis()
                SubTaskResult(
                    subTaskId = request.subTaskId,
                    status = status,
                    httpCode = null,
                    data = throwable.message,
                    nodeName = null,
                    startTime = start,
                    endTime = end,
                    durationMs = end - start,
                    resultSizeBytes = 0,
                    errorMessage = throwable.message,
                    responsePreview = throwable.message?.take(200)
                )
            }
        }
    }

    private fun buildRequest(request: SubTaskRequest): Request {
        return when (request.method) {
            HttpMethod.GET -> {
                val url = request.params.entries.fold(request.url) { acc, entry ->
                    val connector = if (acc.contains("?")) "&" else "?"
                    acc + connector + entry.key + "=" + entry.value
                }
                Request.Builder().url(url).get().build()
            }
            HttpMethod.POST -> {
                val bodyJson = json.encodeToString(mapSerializer, request.params)
                val body = bodyJson.toRequestBody("application/json".toMediaType())
                Request.Builder().url(request.url).post(body).build()
            }
        }
    }
}
