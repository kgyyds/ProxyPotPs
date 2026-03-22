package com.example.proxypotps.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.proxypotps.ui.viewmodel.SettingsViewModel
import java.io.BufferedReader
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsState()
    val nodeCount by viewModel.nodeCountState.collectAsState()
    val probeProgress by viewModel.probeProgress.collectAsState()
    val diagnosticLogs by viewModel.diagnosticLogs.collectAsState()

    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val content = context.contentResolver.openInputStream(it)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).readText()
            } ?: ""
            viewModel.updateSettings { current -> current.copy(yamlText = content) }
            viewModel.parseAndProbe(content)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 节点统计卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "节点统计",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "总计：${nodeCount.total} 个 / 可用 ${nodeCount.available} 个",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // 探测进度
            if (probeProgress.total > 0) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "探测进度：${probeProgress.completed}/${probeProgress.total}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (probeProgress.inProgress) {
                                TextButton(onClick = { viewModel.cancelProbe() }) {
                                    Text("取消")
                                }
                            }
                        }
                        if (probeProgress.inProgress) {
                            LinearProgressIndicator(
                                progress = { probeProgress.completed.toFloat() / probeProgress.total.coerceAtLeast(1) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // 操作按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { filePicker.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("导入 YAML")
                }
                Button(
                    onClick = { viewModel.parseAndProbe(settings.yamlText) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("重新测速")
                }
            }

            // 诊断探测按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.probeDiagnostics() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("诊断探测")
                }
                Button(
                    onClick = { viewModel.probeDeterministic() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("固定 URL 测试")
                }
            }

            // 诊断日志卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "节点连接测试日志",
                            style = MaterialTheme.typography.titleMedium
                        )
                        TextButton(onClick = { viewModel.clearDiagnosticLogs() }) {
                            Text("清空")
                        }
                    }
                    if (diagnosticLogs.isEmpty()) {
                        Text(
                            text = "暂无日志，点击「诊断探测」或「重新测速」按钮开始测试",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            diagnosticLogs.forEach { log ->
                                Text(
                                    text = log,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            // 设置选项
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = settings.verboseProbeLogs,
                            onCheckedChange = { value ->
                                viewModel.updateSettings { it.copy(verboseProbeLogs = value) }
                            }
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(text = "详细探测日志")
                    }

                    OutlinedTextField(
                        value = settings.probeUrl,
                        onValueChange = { value -> viewModel.updateSettings { it.copy(probeUrl = value) } },
                        label = { Text("探测 URL") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = settings.nodeStrategy,
                        onValueChange = { value -> viewModel.updateSettings { it.copy(nodeStrategy = value) } },
                        label = { Text("节点选择策略") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // YAML 配置
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "节点配置 YAML",
                        style = MaterialTheme.typography.titleMedium
                    )
                    OutlinedTextField(
                        value = settings.yamlText,
                        onValueChange = { text -> viewModel.updateSettings { it.copy(yamlText = text) } },
                        label = { Text("粘贴 YAML 配置") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                    Button(
                        onClick = { viewModel.parseAndProbe(settings.yamlText) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("保存并解析")
                    }
                }
            }

            Spacer(modifier = Modifier.size(16.dp))
        }
    }
}
