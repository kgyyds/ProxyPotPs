package com.example.proxypotps.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.proxypotps.domain.model.HttpMethod
import com.example.proxypotps.domain.model.SubTaskRequest
import com.example.proxypotps.domain.model.TaskStatus
import com.example.proxypotps.ui.viewmodel.WorkViewModel
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkScreen(viewModel: WorkViewModel = hiltViewModel()) {
    val tasks by viewModel.tasks.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    Column {
        TopAppBar(
            title = { Text("工作详情") },
            actions = {
                Button(onClick = { showDialog = true }) {
                    Text("手动添加任务")
                }
            }
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            items(tasks) { taskWithSubTasks ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "主任务: ${taskWithSubTasks.task.mainTaskId}",
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(
                            text = "进度 ${taskWithSubTasks.task.completedCount}/${taskWithSubTasks.task.totalCount}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        taskWithSubTasks.subTasks.forEach { subTask ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = subTask.subTaskId, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = subTask.url,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "节点: ${subTask.nodeName ?: "-"}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    val status = TaskStatus.entries.firstOrNull { it.name == subTask.status } ?: TaskStatus.FAILED
                                    val statusText = when (status) {
                                        TaskStatus.OK -> "ok"
                                        TaskStatus.FAILED -> "failed"
                                        TaskStatus.TIMEOUT -> "timeout"
                                    }
                                    Text(text = statusText)
                                    Text(
                                        text = subTask.httpCode?.toString() ?: "-",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.size(6.dp))
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        ManualTaskDialog(
            onDismiss = { showDialog = false },
            onSubmit = { mainTaskId, subTasks ->
                viewModel.submitManualTask(mainTaskId, subTasks)
                showDialog = false
            }
        )
    }
}

@Composable
private fun ManualTaskDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, List<SubTaskRequest>) -> Unit
) {
    var mainTaskId by remember { mutableStateOf("manual-${System.currentTimeMillis()}") }
    var subTasks by remember { mutableStateOf(listOf(SubTaskInput())) }
    val listState = rememberLazyListState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手动添加任务") },
        text = {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .imePadding()
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = mainTaskId,
                        onValueChange = { mainTaskId = it },
                        label = { Text("mainTaskId") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                items(subTasks.size) { index ->
                    val input = subTasks[index]
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = "子任务 ${index + 1}", fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = input.subTaskId,
                            onValueChange = { value ->
                                subTasks = subTasks.toMutableList().also {
                                    it[index] = input.copy(subTaskId = value)
                                }
                            },
                            label = { Text("subTaskId") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = input.url,
                            onValueChange = { value ->
                                subTasks = subTasks.toMutableList().also {
                                    it[index] = input.copy(url = value)
                                }
                            },
                            label = { Text("url") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = input.method,
                            onValueChange = { value ->
                                subTasks = subTasks.toMutableList().also {
                                    it[index] = input.copy(method = value)
                                }
                            },
                            label = { Text("method (GET/POST)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = input.paramsJson,
                            onValueChange = { value ->
                                subTasks = subTasks.toMutableList().also {
                                    it[index] = input.copy(paramsJson = value)
                                }
                            },
                            label = { Text("params JSON") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                item {
                    OutlinedButton(onClick = { subTasks = subTasks + SubTaskInput() }) {
                        Text("添加子任务")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = subTasks.mapNotNull { input ->
                    if (input.subTaskId.isBlank() || input.url.isBlank()) return@mapNotNull null
                    val method = if (input.method.equals("POST", true)) HttpMethod.POST else HttpMethod.GET
                    val params = try {
                        if (input.paramsJson.isBlank()) {
                            emptyMap()
                        } else {
                            val element = Json.parseToJsonElement(input.paramsJson)
                            element.jsonObject.mapValues { it.value.toString().trim('"') }
                        }
                    } catch (error: Exception) {
                        Log.e("TASK", "params json parse failed", error)
                        throw error
                    }
                    SubTaskRequest(
                        subTaskId = input.subTaskId,
                        url = input.url,
                        method = method,
                        params = params
                    )
                }
                onSubmit(mainTaskId, parsed)
            }) {
                Text("提交")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

private data class SubTaskInput(
    val subTaskId: String = "",
    val url: String = "",
    val method: String = "GET",
    val paramsJson: String = "{}"
)
