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
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.routing.get
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
            settingsRepository.settingsFlow.collectLatest { settings ->
                restart(settings.apiPort)
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
        if (port !in 1..65535) {
            Log.e("API", "invalid apiPort=$port, skip restart")
            return
        }
        try {
            server?.stop(1000, 2000)
            server = embeddedServer(
                factory = CIO,
                port = port,
                host = "0.0.0.0",
                module = { module(taskDispatcher, json, port) }
            ).start(wait = false)
            Log.i("API", "server started on 0.0.0.0:$port")
        } catch (error: Exception) {
            Log.e("API", "server restart failed port=$port", error)
        }
    }

    private fun module(taskDispatcher: TaskDispatcher, json: Json, port: Int): Application.() -> Unit = {
        install(ContentNegotiation) {
            json(json)
        }
        routing {
            Log.i("API", "routes registered on port=$port")
            get("/health") {
                call.respond(mapOf("status" to "ok"))
            }
            post("/run") {
                Log.i("API", "POST ${call.request.path()}")
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
