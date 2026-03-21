package com.example.proxypotps.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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
        val probeUrl = stringPreferencesKey("probe_url")
        val yamlText = stringPreferencesKey("yaml_text")
        val nodeStrategy = stringPreferencesKey("node_strategy")
        val verboseProbeLogs = booleanPreferencesKey("verbose_probe_logs")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            probeUrl = prefs[Keys.probeUrl] ?: "http://www.gstatic.com/generate_204",
            yamlText = prefs[Keys.yamlText] ?: "",
            nodeStrategy = prefs[Keys.nodeStrategy] ?: "current",
            verboseProbeLogs = prefs[Keys.verboseProbeLogs] ?: false
        )
    }

    suspend fun updateSettings(update: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs: MutablePreferences ->
            val current = AppSettings(
                probeUrl = prefs[Keys.probeUrl] ?: "http://www.gstatic.com/generate_204",
                yamlText = prefs[Keys.yamlText] ?: "",
                nodeStrategy = prefs[Keys.nodeStrategy] ?: "current",
                verboseProbeLogs = prefs[Keys.verboseProbeLogs] ?: false
            )
            val next = update(current)
            prefs[Keys.probeUrl] = next.probeUrl
            prefs[Keys.yamlText] = next.yamlText
            prefs[Keys.nodeStrategy] = next.nodeStrategy
            prefs[Keys.verboseProbeLogs] = next.verboseProbeLogs
        }
    }
}


data class AppSettings(
    val probeUrl: String,
    val yamlText: String,
    val nodeStrategy: String,
    val verboseProbeLogs: Boolean
)
