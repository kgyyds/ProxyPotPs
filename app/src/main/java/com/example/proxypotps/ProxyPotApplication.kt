package com.example.proxypotps

import android.app.Application
import com.example.proxypotps.network.LocalApiServer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ProxyPotApplication : Application() {
    @Inject lateinit var localApiServer: LocalApiServer

    override fun onCreate() {
        super.onCreate()
        localApiServer.start()
    }
}
