package com.example.proxypotps.ui.viewmodel

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proxypotps.domain.model.ProxyNode
import com.example.proxypotps.domain.usecase.NodeService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.atomic.AtomicBoolean

@HiltViewModel
class NodesViewModel @Inject constructor(
    nodeService: NodeService
) : ViewModel() {
    private val loggedInitial = AtomicBoolean(false)
    private val startMs = SystemClock.elapsedRealtime()

    val nodes: StateFlow<List<ProxyNode>> = nodeService.observeNodes()
        .map { it.sortedBy { node -> node.name } }
        .onEach {
            if (loggedInitial.compareAndSet(false, true)) {
                val elapsed = SystemClock.elapsedRealtime() - startMs
                Log.d("PERF", "nodes first emission cost=${elapsed}ms size=${it.size}")
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
