package com.example.proxypotps.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.proxypotps.domain.model.StressTestResult

@Composable
fun ProxyStatusList(
    nodeResults: List<StressTestResult>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(nodeResults) { result ->
            ProxyStatusCard(result = result)
        }
    }
}

@Composable
private fun ProxyStatusCard(result: StressTestResult) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = result.nodeName ?: "Unknown Node",
                style = MaterialTheme.typography.titleSmall
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "活跃连接: ${result.activeConnections ?: 0}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "总连接: ${result.totalConnections ?: 0}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    val successRate = if (result.totalConnections ?: 0 > 0) {
                        (result.successfulConnections ?: 0).toDouble() / (result.totalConnections ?: 1).toDouble()
                    } else {
                        0.0
                    }
                    Text(
                        text = "成功率: ${String.format("%.1f", successRate * 100)}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "平均响应: ${result.avgConnectionTimeMs}ms",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            // Response code distribution
            if (result.responseCodeDistribution.isNotEmpty()) {
                Text(
                    text = "响应码: ${result.responseCodeDistribution.entries.joinToString(", ") { "${it.key}(${it.value})" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}