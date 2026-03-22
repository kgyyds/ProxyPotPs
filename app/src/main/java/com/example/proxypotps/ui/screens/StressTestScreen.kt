package com.example.proxypotps.ui.screens

import android.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.proxypotps.ui.viewmodel.StressTestViewModel
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StressTestScreen(
    viewModel: StressTestViewModel = hiltViewModel()
) {
    val testState by viewModel.testState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("压测") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!testState.isRunning) {
                TestConfigurationForm(
                    viewModel = viewModel,
                    onStartTest = { viewModel.startStressTest() }
                )
            } else {
                TestExecutionView(
                    testState = testState,
                    onStopTest = { viewModel.stopStressTest() }
                )
            }
        }
    }
}

@Composable
private fun TestConfigurationForm(
    viewModel: StressTestViewModel,
    onStartTest: () -> Unit
) {
    var targetUrl by remember { mutableStateOf("https://example.com") }
    var concurrentConnections by remember { mutableStateOf("10") }
    var connectionTimeout by remember { mutableStateOf("30") }
    var requestInterval by remember { mutableStateOf("1.0") }
    var maxRounds by remember { mutableStateOf("-1") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "压测配置",
            style = MaterialTheme.typography.titleMedium
        )

        OutlinedTextField(
            value = targetUrl,
            onValueChange = { targetUrl = it },
            label = { Text("目标 URL") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = concurrentConnections,
                onValueChange = { concurrentConnections = it },
                label = { Text("并发连接数") },
                modifier = Modifier.weight(1f)
            )

            OutlinedTextField(
                value = connectionTimeout,
                onValueChange = { connectionTimeout = it },
                label = { Text("连接超时 (秒)") },
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = requestInterval,
                onValueChange = { requestInterval = it },
                label = { Text("请求间隔 (秒)") },
                modifier = Modifier.weight(1f)
            )

            OutlinedTextField(
                value = maxRounds,
                onValueChange = { maxRounds = it },
                label = { Text("最大轮数 (-1 无限)") },
                modifier = Modifier.weight(1f)
            )
        }

        Button(
            onClick = {
                viewModel.configureTest(
                    targetUrl = targetUrl,
                    concurrentConnections = concurrentConnections.toIntOrNull() ?: 10,
                    connectionTimeoutSeconds = connectionTimeout.toIntOrNull() ?: 30,
                    requestIntervalSeconds = requestInterval.toDoubleOrNull() ?: 1.0,
                    maxRounds = maxRounds.toIntOrNull() ?: -1
                )
                onStartTest()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("开始压测")
        }
    }
}

@Composable
private fun TestExecutionView(
    testState: StressTestViewModel.TestState,
    onStopTest: () -> Unit
) {
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "活跃连接数",
                    style = MaterialTheme.typography.titleSmall
                )
                AndroidView(
                    factory = { context ->
                        LineChart(context).apply {
                            description = Description().apply {
                                text = ""
                            }
                            setTouchEnabled(false)
                            isDragEnabled = false
                            xAxis.position = XAxis.XAxisPosition.BOTTOM
                            axisLeft.setDrawGridLines(false)
                            axisRight.isEnabled = false
                        }
                    },
                    update = { chart ->
                        val entries = listOf(
                            Entry(0f, testState.totalActiveConnections.toFloat())
                        )
                        val dataSet = LineDataSet(entries, "活跃连接").apply {
                            color = Color.BLUE
                            setCircleColor(Color.BLUE)
                            lineWidth = 2f
                            circleRadius = 4f
                            setDrawValues(false)
                        }
                        chart.data = LineData(dataSet)
                        chart.invalidate()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "压测状态",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "当前轮数：${testState.currentRound}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "总活跃连接：${testState.totalActiveConnections}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "成功连接：${testState.totalSuccessfulConnections}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "失败连接：${testState.totalFailedConnections}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "成功率：${String.format("%.2f", testState.overallSuccessRate * 100)}%",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "状态：${testState.status.name}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Button(
            onClick = onStopTest,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        ) {
            Text("停止压测")
        }
    }
}
