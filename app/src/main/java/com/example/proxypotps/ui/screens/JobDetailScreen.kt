package com.example.proxypotps.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.proxypotps.domain.model.JobStatus
import com.example.proxypotps.domain.model.SubTaskStatus
import com.example.proxypotps.ui.components.JobBarChart
import com.example.proxypotps.ui.components.JobLineChart
import com.example.proxypotps.ui.components.JobPieChart
import com.example.proxypotps.ui.viewmodel.JobDetailUiState
import com.example.proxypotps.ui.viewmodel.JobDetailViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull

private const val PageSize = 20
private const val HeaderItemCount = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDetailScreen(
    onBack: () -> Unit,
    viewModel: JobDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var pageSize by remember { mutableIntStateOf(PageSize) }
    val expandedItems = remember { mutableStateMapOf<String, Boolean>() }

    val pagedSubTasks by remember(uiState.subTasks, pageSize) {
        derivedStateOf { uiState.subTasks.take(pageSize) }
    }

    LaunchedEffect(listState, uiState.subTasks.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .filterNotNull()
            .collectLatest { index ->
                if (index >= pagedSubTasks.size + HeaderItemCount - 1 && pageSize < uiState.subTasks.size) {
                    pageSize += PageSize
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("工作详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                JobSummaryCard(uiState)
            }
            item {
                ChartSection(uiState)
            }
            item {
                Text(
                    text = "子任务列表",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            itemsIndexed(pagedSubTasks, key = { _, item -> item.subId }) { _, item ->
                val expanded = expandedItems[item.subId] == true
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = item.subId, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "节点 ${item.assignedNode ?: "-"}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    text = "延迟 ${item.durationMs ?: 0}ms",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                StatusChip(item.status)
                                Text(
                                    text = "${item.resultSizeBytes} bytes",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = if (expanded) "收起详情" else "展开详情",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(bottom = 4.dp)
                                .clickable { expandedItems[item.subId] = !expanded }
                        )
                        AnimatedVisibility(visible = expanded) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(text = "URL: ${item.url}", style = MaterialTheme.typography.bodySmall)
                                Text(text = "Method: ${item.method}", style = MaterialTheme.typography.bodySmall)
                                Text(text = "Retry: ${item.retryCount}", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = "Preview: ${item.responsePreview ?: "-"}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "Error: ${item.errorMessage ?: "-"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JobSummaryCard(uiState: JobDetailUiState) {
    val summary = uiState.jobSummary
    if (summary == null) {
        Text(text = uiState.errorMessage ?: "加载中...", style = MaterialTheme.typography.bodyMedium)
        return
    }
    val totalDuration = summary.totalDuration ?: (System.currentTimeMillis() - summary.startTime)
    val statusColor = when (summary.status) {
        JobStatus.SUCCESS -> Color(0xFF2E7D32)
        JobStatus.PARTIAL -> Color(0xFFEF6C00)
        JobStatus.FAILED -> Color(0xFFC62828)
        JobStatus.RUNNING -> MaterialTheme.colorScheme.primary
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "JobId: ${summary.jobId}", fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "总耗时 ${totalDuration}ms", style = MaterialTheme.typography.bodySmall)
                Text(text = "成功率 ${(summary.successRate * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "子任务 ${summary.totalCount}", style = MaterialTheme.typography.bodySmall)
                Text(text = "节点 ${summary.nodeUsedCount}", style = MaterialTheme.typography.bodySmall)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "成功 ${summary.successCount} / 失败 ${summary.failCount} / 超时 ${summary.timeoutCount}",
                    style = MaterialTheme.typography.labelSmall
                )
                Text(text = summary.status.name, color = statusColor, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ChartSection(uiState: JobDetailUiState) {
    val chartData = uiState.chartData
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = "成功/失败/超时占比", fontWeight = FontWeight.SemiBold)
                JobPieChart(data = chartData)
            }
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = "节点平均延迟", fontWeight = FontWeight.SemiBold)
                JobBarChart(data = chartData)
            }
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = "子任务执行时间线", fontWeight = FontWeight.SemiBold)
                JobLineChart(data = chartData)
            }
        }
    }
}

@Composable
private fun StatusChip(status: SubTaskStatus) {
    val color = when (status) {
        SubTaskStatus.SUCCESS -> Color(0xFF2E7D32)
        SubTaskStatus.FAIL -> Color(0xFFC62828)
        SubTaskStatus.TIMEOUT -> Color(0xFFEF6C00)
    }
    Text(text = status.name, color = color, style = MaterialTheme.typography.labelMedium)
}
