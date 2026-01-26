package com.example.proxypotps.data.repository

import com.example.proxypotps.data.datastore.AppSettings
import com.example.proxypotps.data.datastore.SettingsDataStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: SettingsDataStore
) {
    val settingsFlow: Flow<AppSettings> = dataStore.settingsFlow

    suspend fun updateSettings(update: (AppSettings) -> AppSettings) {
        dataStore.updateSettings(update)
    }
}
