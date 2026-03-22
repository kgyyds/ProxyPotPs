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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
fun NodeCard(
    node: ProxyNode,
    isProbing: Boolean,
    onProbeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                if (isProbing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(26.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = when (node.status) {
                            NodeStatus.AVAILABLE -> "${node.latencyMs ?: 0}"
                            NodeStatus.PROBING -> "…"
                            NodeStatus.TIMEOUT -> "—"
                            NodeStatus.UNAVAILABLE -> "—"
                            NodeStatus.UNKNOWN -> "—"
                        },
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                val statusColor = when (node.status) {
                    NodeStatus.AVAILABLE -> Color(0xFF4CAF50)
                    NodeStatus.PROBING -> Color(0xFF2196F3)
                    NodeStatus.TIMEOUT -> Color(0xFFFF9800)
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
                        NodeStatus.PROBING -> "检测中"
                        NodeStatus.TIMEOUT -> "timeout"
                        NodeStatus.UNAVAILABLE -> "不可用"
                        NodeStatus.UNKNOWN -> "待检测"
                    },
                    style = MaterialTheme.typography.labelSmall
                )
            }
            IconButton(
                onClick = onProbeClick,
                enabled = !isProbing,
                modifier = Modifier
                    .align(Alignment.End)
                    .size(32.dp)
            ) {
                val iconColor = when {
                    isProbing -> MaterialTheme.colorScheme.primary
                    node.status == NodeStatus.AVAILABLE -> Color(0xFF4CAF50)
                    node.status == NodeStatus.UNAVAILABLE || node.status == NodeStatus.TIMEOUT -> Color(0xFFF44336)
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                }
                val icon = when {
                    isProbing -> Icons.Default.Refresh
                    node.status == NodeStatus.AVAILABLE -> Icons.Default.CheckCircle
                    node.status == NodeStatus.UNAVAILABLE || node.status == NodeStatus.TIMEOUT -> Icons.Default.Error
                    node.status == NodeStatus.UNKNOWN -> Icons.Default.Refresh
                    else -> Icons.Default.Warning
                }
                Icon(
                    imageVector = icon,
                    contentDescription = "探测节点",
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
