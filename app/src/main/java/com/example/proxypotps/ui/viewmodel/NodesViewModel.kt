package com.example.proxypotps.ui.viewmodel

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proxypotps.data.repository.SettingsRepository
import com.example.proxypotps.domain.model.ProxyNode
import com.example.proxypotps.domain.usecase.NodeService
import com.example.proxypotps.domain.usecase.ProbeProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

@HiltViewModel
class NodesViewModel @Inject constructor(
    private val nodeService: NodeService,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    private val loggedInitial = AtomicBoolean(false)
    private val startMs = SystemClock.elapsedRealtime()
    private var probeJob: Job? = null

    val nodes: StateFlow<List<ProxyNode>> = nodeService.observeNodes()
        .map { it.sortedBy { node -> node.name } }
        .onEach {
            if (loggedInitial.compareAndSet(false, true)) {
                val elapsed = SystemClock.elapsedRealtime() - startMs
                Log.d("PERF", "nodes first emission cost=${elapsed}ms size=${it.size}")
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _probingNodeIds = MutableStateFlow<Set<Long>>(emptySet())
    val probingNodeIds: StateFlow<Set<Long>> = _probingNodeIds.asStateFlow()

    private val _probeProgress = MutableStateFlow(ProbeProgress(0, 0, false))
    val probeProgress: StateFlow<ProbeProgress> = _probeProgress.asStateFlow()

    fun probeNode(nodeId: Long) {
        viewModelScope.launch {
            _probingNodeIds.value = _probingNodeIds.value + nodeId
            try {
                val settings = settingsRepository.settingsFlow.first()
                nodeService.probeNode(nodeId, settings.probeUrl, settings.verboseProbeLogs)
            } catch (e: Exception) {
                Log.e("NodesViewModel", "probeNode failed for nodeId=$nodeId", e)
            } finally {
                _probingNodeIds.value = _probingNodeIds.value - nodeId
            }
        }
    }

    fun probeAllNodes() {
        probeJob?.cancel()
        probeJob = viewModelScope.launch {
            try {
                val settings = settingsRepository.settingsFlow.first()
                nodeService.probeAllWithUrl(
                    probeUrl = settings.probeUrl,
                    verboseLogs = settings.verboseProbeLogs,
                    onProgress = { progress ->
                        _probeProgress.value = progress
                    }
                )
            } catch (e: Exception) {
                Log.e("NodesViewModel", "probeAllNodes failed", e)
            } finally {
                _probeProgress.value = _probeProgress.value.copy(inProgress = false)
            }
        }
    }

    fun cancelProbe() {
        probeJob?.cancel()
        probeJob = null
        _probeProgress.value = _probeProgress.value.copy(inProgress = false)
    }
}
