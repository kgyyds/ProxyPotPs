package com.example.proxypotps.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proxypotps.data.local.TaskWithSubTasks
import com.example.proxypotps.data.repository.TaskRepository
import com.example.proxypotps.domain.model.RunTaskRequest
import com.example.proxypotps.domain.model.SubTaskRequest
import com.example.proxypotps.scheduler.TaskDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class WorkViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val taskDispatcher: TaskDispatcher
) : ViewModel() {
    val tasks: StateFlow<List<TaskWithSubTasks>> = taskRepository.observeTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun submitManualTask(mainTaskId: String, subTasks: List<SubTaskRequest>) {
        viewModelScope.launch {
            taskDispatcher.runMainTask(RunTaskRequest(mainTaskId = mainTaskId, subTasks = subTasks))
        }
    }
}
