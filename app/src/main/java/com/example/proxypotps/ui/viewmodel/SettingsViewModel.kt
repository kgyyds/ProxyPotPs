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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val nodeService: NodeService
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            AppSettings(9999, "127.0.0.1", 7890, "http://www.gstatic.com/generate_204", "", "current")
        )

    val nodeCountState: StateFlow<NodeCount> = nodeService.observeNodes()
        .map { nodes ->
            NodeCount(
                total = nodes.size,
                available = nodes.count { it.status == NodeStatus.AVAILABLE }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NodeCount(0, 0))

    fun updateSettings(update: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            settingsRepository.updateSettings(update)
        }
    }

    fun parseAndProbe() {
        viewModelScope.launch(Dispatchers.IO) {
            val yamlText = settings.value.yamlText
            nodeService.parseAndStore(yamlText)
            nodeService.probeAll()
        }
    }
}

data class NodeCount(
    val total: Int,
    val available: Int
)
