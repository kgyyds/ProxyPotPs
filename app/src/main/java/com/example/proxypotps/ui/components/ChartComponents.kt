package com.example.proxypotps.ui.components

import android.graphics.Color
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.proxypotps.ui.util.JobChartData
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter

@Composable
fun JobPieChart(data: JobChartData, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp),
        factory = { context ->
            PieChart(context).apply {
                description.isEnabled = false
                setUsePercentValues(true)
                setDrawEntryLabels(false)
                legend.isEnabled = false
                setHoleColor(Color.TRANSPARENT)
            }
        },
        update = { chart ->
            val entries = listOf(
                PieEntry(data.successCount.toFloat(), "Success"),
                PieEntry(data.failCount.toFloat(), "Fail"),
                PieEntry(data.timeoutCount.toFloat(), "Timeout")
            ).filter { it.value > 0f }
            val colors = listOf(Color.parseColor("#2E7D32"), Color.parseColor("#C62828"), Color.parseColor("#EF6C00"))
            val dataSet = PieDataSet(entries, "").apply {
                setDrawValues(true)
                sliceSpace = 2f
                this.colors = colors
            }
            chart.data = PieData(dataSet)
            chart.invalidate()
        }
    )
}

@Composable
fun JobBarChart(data: JobChartData, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp),
        factory = { context ->
            BarChart(context).apply {
                description.isEnabled = false
                axisRight.isEnabled = false
                legend.isEnabled = false
                xAxis.position = XAxis.XAxisPosition.BOTTOM
                xAxis.granularity = 1f
                xAxis.setDrawGridLines(false)
            }
        },
        update = { chart ->
            val entries = data.nodeLatency.mapIndexed { index, entry ->
                BarEntry(index.toFloat(), entry.avgLatencyMs)
            }
            val labels = data.nodeLatency.map { it.node }
            chart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            val dataSet = BarDataSet(entries, "节点平均延迟(ms)").apply {
                color = Color.parseColor("#1565C0")
                valueTextColor = Color.WHITE
            }
            chart.data = BarData(dataSet).apply { barWidth = 0.6f }
            chart.invalidate()
        }
    )
}

@Composable
fun JobLineChart(data: JobChartData, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp),
        factory = { context ->
            LineChart(context).apply {
                description.isEnabled = false
                axisRight.isEnabled = false
                legend.isEnabled = false
                xAxis.position = XAxis.XAxisPosition.BOTTOM
                xAxis.granularity = 1f
                xAxis.setDrawGridLines(false)
            }
        },
        update = { chart ->
            val entries = data.timeline.map { Entry(it.offsetMs, it.durationMs) }
            val dataSet = LineDataSet(entries, "子任务耗时").apply {
                color = Color.parseColor("#00ACC1")
                setCircleColor(Color.parseColor("#00ACC1"))
                lineWidth = 2f
                circleRadius = 3f
                valueTextColor = Color.WHITE
                setDrawValues(false)
            }
            chart.data = LineData(dataSet)
            chart.invalidate()
        }
    )
}
