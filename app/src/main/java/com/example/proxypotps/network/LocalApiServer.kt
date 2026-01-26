package com.example.proxypotps.network

import android.util.Log
import com.example.proxypotps.data.repository.SettingsRepository
import com.example.proxypotps.domain.model.RunTaskRequest
import com.example.proxypotps.scheduler.TaskDispatcher
import com.example.proxypotps.di.ApplicationScope

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.cio.CIO
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class LocalApiServer @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val taskDispatcher: TaskDispatcher,
    private val json: Json,
    @ApplicationScope private val scope: CoroutineScope
) {

    private var server: ApplicationEngine? = null
    private var watcherJob: Job? = null
    private val restartMutex = Mutex()


    fun start() {
        if (watcherJob != null) return

        watcherJob = scope.launch {
            settingsRepository.settingsFlow
                .map { it.apiPort }
                .distinctUntilChanged()
                .debounce(400)
                .collectLatest { port ->
                    safeRestart(port)
                }
        }
    }


    private suspend fun safeRestart(port: Int) {

        if (port !in 1..65535) return

        restartMutex.withLock {
            withContext(Dispatchers.IO) {

                server?.stop(1000, 2000)

                server = embeddedServer(
                    CIO,
                    port = port,
                    host = "0.0.0.0",
                    module = module(taskDispatcher, json) // ✅ 直接传
                ).start(wait = false)
            }

            Log.i("LocalApiServer", "Server started on $port")
        }
    }


    fun stop() {
        watcherJob?.cancel()
        watcherJob = null
        server?.stop(1000, 2000)
        server = null
    }


    /**
     * ⭐⭐ 核心修复在这里 ⭐⭐
     * 只保留一层 lambda
     */
    private fun module(
        taskDispatcher: TaskDispatcher,
        json: Json
    ): Application.() -> Unit = {

        install(ContentNegotiation) {
            json(json)
        }

        routing {

            post("/run") {

                val request = try {
                    call.receive<RunTaskRequest>()
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "invalid_request")
                    )
                    return@post
                }

                val response = taskDispatcher.runMainTask(request)

                call.respond(response)
            }
        }
    }
}