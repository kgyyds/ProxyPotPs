package com.example.proxypotps.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.proxypotps.ui.components.NodeCard
import com.example.proxypotps.ui.viewmodel.NodesViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NodesScreen(viewModel: NodesViewModel = hiltViewModel()) {
    val nodes by viewModel.nodes.collectAsState()
    val probingNodeIds by viewModel.probingNodeIds.collectAsState()
    val probeProgress by viewModel.probeProgress.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("节点") },
                actions = {
                    IconButton(
                        onClick = { viewModel.probeAllNodes() },
                        enabled = !probeProgress.inProgress
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "全部测试"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Text(
                text = "探测会使用每个节点的本地代理端口进行访问",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(16.dp)
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(nodes.size) { index ->
                    val node = nodes[index]
                    val isProbing = probingNodeIds.contains(node.id)
                    NodeCard(
                        node = node,
                        isProbing = isProbing,
                        onProbeClick = { viewModel.probeNode(node.id) }
                    )
                }
            }
        }
    }
}
