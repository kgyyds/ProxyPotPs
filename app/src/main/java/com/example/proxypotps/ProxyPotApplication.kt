package com.example.proxypotps

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ProxyPotApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
