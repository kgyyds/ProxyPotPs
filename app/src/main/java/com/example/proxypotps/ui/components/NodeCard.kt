package com.example.proxypotps.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.proxypotps.domain.model.NodeStatus
import com.example.proxypotps.domain.model.ProxyNode
import com.example.proxypotps.util.extractFlagEmoji

@Composable
fun NodeCard(node: ProxyNode, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = extractFlagEmoji(node.name), fontSize = 20.sp)
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(
                            text = node.name,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = node.type.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                Text(
                    text = when (node.status) {
                        NodeStatus.AVAILABLE -> "${node.latencyMs ?: 0}"
                        NodeStatus.UNAVAILABLE -> "—"
                        NodeStatus.UNKNOWN -> "—"
                    },
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val statusColor = when (node.status) {
                    NodeStatus.AVAILABLE -> Color(0xFF4CAF50)
                    NodeStatus.UNAVAILABLE -> Color(0xFFF44336)
                    NodeStatus.UNKNOWN -> Color(0xFF9E9E9E)
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(statusColor, CircleShape)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = when (node.status) {
                        NodeStatus.AVAILABLE -> "可用"
                        NodeStatus.UNAVAILABLE -> "不可用"
                        NodeStatus.UNKNOWN -> "待检测"
                    },
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
