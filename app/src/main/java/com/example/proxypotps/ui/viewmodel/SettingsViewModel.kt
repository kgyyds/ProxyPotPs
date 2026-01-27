package com.example.proxypotps.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proxypotps.data.datastore.AppSettings
import com.example.proxypotps.data.repository.SettingsRepository
import com.example.proxypotps.domain.model.NodeStatus
import com.example.proxypotps.domain.usecase.NodeService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import com.example.proxypotps.domain.usecase.ProbeProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val nodeService: NodeService
) : ViewModel() {
    private val probeProgressState = MutableStateFlow(ProbeProgress(0, 0, false))
    private var probeJob: Job? = null

    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            AppSettings(9999, "http://www.gstatic.com/generate_204", "", "current", false)
        )

    val nodeCountState: StateFlow<NodeCount> = nodeService.observeNodes()
        .map { nodes ->
            NodeCount(
                total = nodes.size,
                available = nodes.count { it.status == NodeStatus.AVAILABLE }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NodeCount(0, 0))

    val probeProgress: StateFlow<ProbeProgress> = probeProgressState

    fun updateSettings(update: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            settingsRepository.updateSettings(update)
        }
    }

    fun parseAndProbe(yamlText: String = settings.value.yamlText) {
        startProbe {
            nodeService.parseAndStore(yamlText)
            nodeService.probeAllWithUrl(
                probeUrl = settings.value.probeUrl,
                verboseLogs = settings.value.verboseProbeLogs,
                onProgress = ::updateProgress
            )
        }
    }

    fun probeDeterministic() {
        startProbe {
            nodeService.probeAllWithUrl(
                probeUrl = "http://www.gstatic.com/generate_204",
                verboseLogs = settings.value.verboseProbeLogs,
                onProgress = ::updateProgress
            )
        }
    }

    fun probeDiagnostics() {
        startProbe {
            nodeService.probeSampleNodes(
                probeUrl = settings.value.probeUrl,
                verboseLogs = true,
                sampleSize = 3
            )
        }
    }

    fun cancelProbe() {
        probeJob?.cancel()
        probeJob = null
        probeProgressState.update { it.copy(inProgress = false) }
    }

    private fun startProbe(block: suspend () -> Unit) {
        probeJob?.cancel()
        probeJob = viewModelScope.launch(Dispatchers.IO) {
            updateProgress(ProbeProgress(inProgress = true))
            try {
                block()
            } catch (error: Exception) {
                updateProgress(ProbeProgress(inProgress = false))
                android.util.Log.e("PROBE", "probe task failed", error)
            } finally {
                probeProgressState.update { it.copy(inProgress = false) }
            }
        }
    }

    private fun updateProgress(progress: ProbeProgress) {
        probeProgressState.update { current ->
            current.copy(
                total = progress.total.takeIf { it > 0 } ?: current.total,
                completed = progress.completed,
                inProgress = progress.inProgress
            )
        }
    }
}

data class NodeCount(
    val total: Int,
    val available: Int
)
