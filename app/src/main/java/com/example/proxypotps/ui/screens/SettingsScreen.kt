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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val content = context.contentResolver.openInputStream(it)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).readText()
            } ?: ""
            viewModel.updateSettings { current -> current.copy(yamlText = content) }
            viewModel.parseAndProbe()
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        TopAppBar(title = { Text("设置") })
        val yamlScrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "节点统计: ${nodeCount.total} 个 / 可用 ${nodeCount.available} 个")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                    Text("从文件导入")
                }
                Spacer(modifier = Modifier.size(8.dp))
                TextButton(onClick = { viewModel.parseAndProbe() }) {
                    Text("重新解析/重新测速")
                }
                Spacer(modifier = Modifier.size(8.dp))
                TextButton(onClick = { viewModel.probeDeterministic() }) {
                    Text("固定 URL 测试")
                }
            }
            Column {
                Text(text = "节点配置 YAML", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.size(6.dp))
                OutlinedTextField(
                    value = settings.yamlText,
                    onValueChange = { text -> viewModel.updateSettings { it.copy(yamlText = text) } },
                    label = { Text("粘贴或导入 YAML") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .verticalScroll(yamlScrollState)
                )
            }
            Button(onClick = { viewModel.parseAndProbe() }) {
                Text("保存并解析")
            }
            OutlinedTextField(
                value = settings.apiPort.toString(),
                onValueChange = { value ->
                    viewModel.updateSettings { it.copy(apiPort = value.toIntOrNull() ?: it.apiPort) }
                },
                label = { Text("本地 API 端口") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = settings.probeUrl,
                onValueChange = { value -> viewModel.updateSettings { it.copy(probeUrl = value) } },
                label = { Text("探测 URL") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = settings.nodeStrategy,
                onValueChange = { value -> viewModel.updateSettings { it.copy(nodeStrategy = value) } },
                label = { Text("节点选择策略 (url-test/fallback/current)") },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "提示：探测通过本地代理端口直连每个节点。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}
