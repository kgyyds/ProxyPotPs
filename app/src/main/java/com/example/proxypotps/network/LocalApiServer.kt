package com.example.proxypotps.network

import android.util.Log
import com.example.proxypotps.data.repository.SettingsRepository
import com.example.proxypotps.domain.model.RunTaskRequest
import com.example.proxypotps.scheduler.TaskDispatcher
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import com.example.proxypotps.di.ApplicationScope

@Singleton
class LocalApiServer @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val taskDispatcher: TaskDispatcher,
    private val json: Json,
    @ApplicationScope private val scope: CoroutineScope
) {
    private var server: ApplicationEngine? = null
    private var watcherJob: Job? = null
    fun start() {
    if (watcherJob != null) return
    watcherJob = scope.launch {
        settingsRepository.settingsFlow
            .map { it.apiPort }
            .distinctUntilChanged()
            .debounce(400) // ✅ 防止输入时疯狂重启
            .collectLatest { port ->
                safeRestart(port)
            }
    }
    }
    private suspend fun safeRestart(port: Int) {
    // ✅ 端口校验，避免 0 / 负数 / 超范围
    if (port !in 1..65535) {
        Log.e("LocalApiServer", "Invalid port=$port, skip restart")
        return
    }

    restartMutex.withLock {
        try {
            withContext(Dispatchers.IO) {
                server?.stop(1000, 2000)
                server = embeddedServer(
                    factory = CIO,
                    port = port,
                    host = "0.0.0.0", // ✅ 让 Termux/局域网能访问
                    module = { module(taskDispatcher, json) }
                ).start(wait = false)
            }
            Log.i("LocalApiServer", "Server restarted on port=$port")
        } catch (t: Throwable) {
            Log.e("LocalApiServer", "Restart failed on port=$port", t)
            // ✅ 不要让异常冒泡导致闪退
        }
    }
    }

    fun stop() {
        watcherJob?.cancel()
        watcherJob = null
        server?.stop(1000, 2000)
        server = null
    }

    private fun restart(port: Int) {
        server?.stop(1000, 2000)
        server = embeddedServer(
            factory = CIO,
            port = port,
            host = "0.0.0.0",
           
            module = module(taskDispatcher, json)
        ).start(wait = false)
    }

    private fun module(taskDispatcher: TaskDispatcher, json: Json): Application.() -> Unit = {
        install(ContentNegotiation) {
            json(json)
        }
        routing {
            post("/run") {
                val request = try {
                    call.receive<RunTaskRequest>()
                } catch (error: Exception) {
                    Log.e("TASK", "invalid request", error)
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid_request"))
                    return@post
                }
                val response = taskDispatcher.runMainTask(request)
                call.respond(response)
            }
        }
    }
}
