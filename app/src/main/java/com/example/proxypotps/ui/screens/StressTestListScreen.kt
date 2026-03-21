package com.example.proxypotps.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StressTestListScreen(
    navController: NavController
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("压测工具") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "选择压测方式",
                style = MaterialTheme.typography.titleMedium
            )
            
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(stressTestTypes) { testType ->
                    StressTestCard(
                        testType = testType,
                        onClick = {
                            when (testType.type) {
                                com.example.proxypotps.domain.model.StressTestType.SLOW_LORIS -> {
                                    navController.navigate("slow_loris_test")
                                }
                                com.example.proxypotps.domain.model.StressTestType.HTTP_FLOOD,
                                com.example.proxypotps.domain.model.StressTestType.POST_FLOOD -> {
                                    // TODO: add destinations for these stress tests
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StressTestCard(
    testType: StressTestTypeInfo,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = testType.name,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = testType.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

data class StressTestTypeInfo(
    val type: com.example.proxypotps.domain.model.StressTestType,
    val name: String,
    val description: String
)

private val stressTestTypes = listOf(
    StressTestTypeInfo(
        type = com.example.proxypotps.domain.model.StressTestType.SLOW_LORIS,
        name = "HTTP/HTTPS慢连接压测",
        description = "通过缓慢发送HTTP请求头来消耗服务器连接资源"
    )
    // Add more stress test types here in the future
)
