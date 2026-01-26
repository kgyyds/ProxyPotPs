package com.example.proxypotps.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proxypotps.domain.model.ProxyNode
import com.example.proxypotps.domain.usecase.NodeService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class NodesViewModel @Inject constructor(
    nodeService: NodeService
) : ViewModel() {
    val nodes: StateFlow<List<ProxyNode>> = nodeService.observeNodes()
        .map { it.sortedBy { node -> node.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
