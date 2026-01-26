package com.example.proxypotps.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val apiPort = intPreferencesKey("api_port")
        val probeUrl = stringPreferencesKey("probe_url")
        val yamlText = stringPreferencesKey("yaml_text")
        val nodeStrategy = stringPreferencesKey("node_strategy")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            apiPort = prefs[Keys.apiPort] ?: 9999,
            probeUrl = prefs[Keys.probeUrl] ?: "http://www.gstatic.com/generate_204",
            yamlText = prefs[Keys.yamlText] ?: "",
            nodeStrategy = prefs[Keys.nodeStrategy] ?: "current"
        )
    }

    suspend fun updateSettings(update: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs: MutablePreferences ->
            val current = AppSettings(
                apiPort = prefs[Keys.apiPort] ?: 9999,
                probeUrl = prefs[Keys.probeUrl] ?: "http://www.gstatic.com/generate_204",
                yamlText = prefs[Keys.yamlText] ?: "",
                nodeStrategy = prefs[Keys.nodeStrategy] ?: "current"
            )
            val next = update(current)
            prefs[Keys.apiPort] = next.apiPort
            prefs[Keys.probeUrl] = next.probeUrl
            prefs[Keys.yamlText] = next.yamlText
            prefs[Keys.nodeStrategy] = next.nodeStrategy
        }
    }
}


data class AppSettings(
    val apiPort: Int,
    val probeUrl: String,
    val yamlText: String,
    val nodeStrategy: String
)
