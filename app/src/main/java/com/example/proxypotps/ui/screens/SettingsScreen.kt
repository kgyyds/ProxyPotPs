package com.example.proxypotps.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
            }
            OutlinedTextField(
                value = settings.yamlText,
                onValueChange = { text -> viewModel.updateSettings { it.copy(yamlText = text) } },
                label = { Text("Clash 配置 YAML") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 6
            )
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
                value = settings.clashHost,
                onValueChange = { value -> viewModel.updateSettings { it.copy(clashHost = value) } },
                label = { Text("Clash 本地代理地址") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = settings.clashPort.toString(),
                onValueChange = { value ->
                    viewModel.updateSettings { it.copy(clashPort = value.toIntOrNull() ?: it.clashPort) }
                },
                label = { Text("Clash 端口") },
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
                text = "提示：当前探测仅基于 Clash 当前出口。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}
