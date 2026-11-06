package com.wanghoufan.zaijiuyidian.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wanghoufan.zaijiuyidian.domain.Formatters
import com.wanghoufan.zaijiuyidian.domain.Stats
import com.wanghoufan.zaijiuyidian.domain.TrainingSession
import com.wanghoufan.zaijiuyidian.ui.theme.AppTheme
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.max

private const val PLOT_TOP_DP = 24f
private const val PLOT_BOTTOM_DP = 34f
private const val PLOT_LEFT_DP = 38f
private const val PLOT_RIGHT_DP = 15f

@Composable
fun ChartLegend(primary: String, secondary: String? = null) {
    val p = AppTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(p.accent))
            Spacer(Modifier.width(5.dp))
            Text(primary, color = p.sub, fontSize = 10.sp)
        }
        if (secondary != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFD9A45D)))
                Spacer(Modifier.width(5.dp))
                Text(secondary, color = p.sub, fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun ChartNote(text: String) {
    val p = AppTheme.palette
    Text(
        text,
        color = p.sub,
        fontSize = 11.sp,
        lineHeight = 17.sp,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
    )
}

@Composable
fun TrendChart(sessions: List<TrainingSession>, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    val measurer = rememberTextMeasurer()
    if (sessions.isEmpty()) {
        ChartEmpty()
        return
    }
    val data = sessions.sortedBy { it.endedAt }
    val maxMinutes = data.maxOf { max(it.targetSec, it.actualSec) / 60.0 }
    val yMax = max(10.0, ceil(maxMinutes / 5.0) * 5.0)
    val axisStyle = TextStyle(fontSize = 9.sp, color = p.sub)
    val left = PLOT_LEFT_DP
    val right = PLOT_RIGHT_DP
    val top = PLOT_TOP_DP
    val bottom = PLOT_BOTTOM_DP

    Column(modifier) {
        ChartLegend("实际时长", "目标标记")
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(340f / 205f)
        ) {
            val leftPx = left.dp.toPx()
            val rightPx = right.dp.toPx()
            val topPx = top.dp.toPx()
            val bottomPx = bottom.dp.toPx()
            val plotW = size.width - leftPx - rightPx
            val plotH = size.height - topPx - bottomPx
            val baseline = size.height - bottomPx

            for (i in 0..5) {
                val value = yMax * i / 5.0
                val y = baseline - (plotH * i / 5f)
                drawLine(p.hair, Offset(leftPx, y), Offset(size.width - rightPx, y), strokeWidth = 1f)
                val label = if (value == value.toLong().toDouble()) value.toLong().toString() else String.format("%.1f", value)
                val measured = measurer.measure(label, axisStyle)
                drawText(measured, topLeft = Offset(leftPx - measured.size.width - 4.dp.toPx(), y - measured.size.height / 2f))
            }
            val unit = measurer.measure("分钟", axisStyle)
            drawText(unit, topLeft = Offset(0f, 2.dp.toPx()))

            val step = plotW / data.size
            val barWidth = minOf(23.dp.toPx(), max(10.dp.toPx(), plotW / data.size * 0.55f))
            val labelEvery = max(1, ceil(data.size / 7.0).toInt())

            data.forEachIndexed { index, session ->
                val x = leftPx + (index + 0.5f) * step
                val barHeight = (session.actualSec / 60.0 / yMax * plotH).toFloat()
                val y = baseline - barHeight
                drawRoundRect(
                    color = p.accent.copy(alpha = 0.9f),
                    topLeft = Offset(x - barWidth / 2f, y),
                    size = Size(barWidth, barHeight.coerceAtLeast(0.5f)),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
                val targetY = baseline - (session.targetSec / 60.0 / yMax * plotH).toFloat()
                drawLine(
                    color = Color(0xFFDCA758),
                    start = Offset(x - barWidth / 2f - 1.dp.toPx(), targetY),
                    end = Offset(x + barWidth / 2f + 1.dp.toPx(), targetY),
                    strokeWidth = 2.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
                if (index % labelEvery == 0 || index == data.lastIndex) {
                    val label = measurer.measure(Formatters.dateShort(session.endedAt), axisStyle)
                    drawText(
                        label,
                        topLeft = Offset(x - label.size.width / 2f, baseline + 5.dp.toPx())
                    )
                }
            }
        }
    }
}

@Composable
fun WeeklyChart(points: List<Stats.WeekPoint>, nowMs: Long, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    val measurer = rememberTextMeasurer()
    if (points.isEmpty()) {
        ChartEmpty()
        return
    }
    val maxCount = max(5, (ceil(points.maxOf { it.count }.toDouble() / 5.0) * 5).toInt())
    val maxAvg = max(15.0, ceil((points.mapNotNull { it.avgSec }.maxOrNull() ?: 0.0) / 60.0 / 5.0) * 5.0)
    val axisStyle = TextStyle(fontSize = 9.sp, color = p.sub)
    val currentWeek = Stats.weekStart(nowMs, ZoneId.systemDefault())

    Column(modifier) {
        ChartLegend("每周有效次数（左轴）", "周平均分钟（右轴）")
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(340f / 205f)
        ) {
            val leftPx = PLOT_LEFT_DP.dp.toPx()
            val rightPx = 34.dp.toPx()
            val topPx = PLOT_TOP_DP.dp.toPx()
            val bottomPx = PLOT_BOTTOM_DP.dp.toPx()
            val plotW = size.width - leftPx - rightPx
            val plotH = size.height - topPx - bottomPx
            val baseline = size.height - bottomPx

            for (i in 0..5) {
                val y = baseline - (plotH * i / 5f)
                drawLine(p.hair, Offset(leftPx, y), Offset(size.width - rightPx, y), strokeWidth = 1f)
                val countValue = maxCount * i / 5
                val leftLabel = measurer.measure(countValue.toString(), axisStyle)
                drawText(leftLabel, topLeft = Offset(leftPx - leftLabel.size.width - 4.dp.toPx(), y - leftLabel.size.height / 2f))
                val avgValue = maxAvg * i / 5.0
                val rightText = if (avgValue == avgValue.toLong().toDouble()) avgValue.toLong().toString() else String.format("%.1f", avgValue)
                val rightLabel = measurer.measure(rightText, axisStyle)
                drawText(rightLabel, topLeft = Offset(size.width - rightPx + 4.dp.toPx(), y - rightLabel.size.height / 2f))
            }
            val leftUnit = measurer.measure("次数", axisStyle)
            drawText(leftUnit, topLeft = Offset(2.dp.toPx(), 2.dp.toPx()))
            val rightUnit = measurer.measure("分钟", axisStyle)
            drawText(rightUnit, topLeft = Offset(size.width - rightUnit.size.width - 2.dp.toPx(), 2.dp.toPx()))

            val step = plotW / points.size
            val barWidth = minOf(24.dp.toPx(), step * 0.5f)
            val dots = mutableListOf<Offset?>()
            points.forEachIndexed { index, point ->
                val x = leftPx + (index + 0.5f) * step
                val barHeight = point.count.toFloat() / maxCount * plotH
                drawRoundRect(
                    color = p.accent.copy(alpha = 0.9f),
                    topLeft = Offset(x - barWidth / 2f, baseline - barHeight),
                    size = Size(barWidth, barHeight.coerceAtLeast(if (point.count == 0) 0f else 2f)),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
                val avg = point.avgSec
                dots.add(if (avg == null) null else Offset(x, baseline - (avg / 60.0 / maxAvg * plotH).toFloat()))
                val isCurrent = point.weekStartMs == currentWeek
                val label = measurer.measure(
                    if (isCurrent) "本周" else Formatters.dateShort(point.weekStartMs),
                    TextStyle(fontSize = 9.sp, color = if (isCurrent) p.accent else p.sub, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal)
                )
                drawText(
                    label,
                    topLeft = Offset((x - label.size.width / 2f).coerceIn(leftPx - 8.dp.toPx(), size.width - rightPx), baseline + 5.dp.toPx())
                )
            }

            val lineColor = Color(0xFFD9A45D)
            dots.forEachIndexed { index, dot ->
                if (dot == null) return@forEachIndexed
                val prev = dots.getOrNull(index - 1)
                if (prev != null) {
                    drawLine(lineColor, prev, dot, strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
                }
            }
            dots.filterNotNull().forEach { dot ->
                drawCircle(lineColor, radius = 4.5.dp.toPx(), center = dot)
                drawCircle(p.card, radius = 2.6.dp.toPx(), center = dot)
            }
        }
    }
}

@Composable
fun ScatterChart(points: List<Stats.WeekPoint>, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    val measurer = rememberTextMeasurer()
    if (points.isEmpty()) {
        ChartEmpty()
        return
    }
    val maxCount = max(3, points.maxOf { it.count } + 1)
    val maxAvg = max(15.0, ceil((points.mapNotNull { it.avgSec }.maxOrNull() ?: 0.0) / 60.0 / 5.0) * 5.0)
    val axisStyle = TextStyle(fontSize = 9.sp, color = p.sub)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(340f / 205f)
    ) {
        val leftPx = 43.dp.toPx()
        val rightPx = 26.dp.toPx()
        val topPx = PLOT_TOP_DP.dp.toPx()
        val bottomPx = PLOT_BOTTOM_DP.dp.toPx()
        val plotW = size.width - leftPx - rightPx
        val plotH = size.height - topPx - bottomPx
        val baseline = size.height - bottomPx

        for (i in 0..5) {
            val y = baseline - (plotH * i / 5f)
            drawLine(p.hair, Offset(leftPx, y), Offset(size.width - rightPx, y), strokeWidth = 1f)
            val value = maxAvg * i / 5.0
            val text = if (value == value.toLong().toDouble()) value.toLong().toString() else String.format("%.1f", value)
            val label = measurer.measure(text, axisStyle)
            drawText(label, topLeft = Offset(leftPx - label.size.width - 4.dp.toPx(), y - label.size.height / 2f))
        }
        val unit = measurer.measure("分钟", axisStyle)
        drawText(unit, topLeft = Offset(2.dp.toPx(), 2.dp.toPx()))

        for (x in 1..maxCount) {
            val xPos = leftPx + x.toFloat() / maxCount * plotW
            drawLine(p.hair.copy(alpha = 0.5f), Offset(xPos, topPx), Offset(xPos, baseline), strokeWidth = 1f)
            val label = measurer.measure(x.toString(), axisStyle)
            drawText(label, topLeft = Offset(xPos - label.size.width / 2f, baseline + 5.dp.toPx()))
        }
        val xTitle = measurer.measure("每周训练次数", axisStyle)
        drawText(xTitle, topLeft = Offset(leftPx + plotW / 2f - xTitle.size.width / 2f, size.height - xTitle.size.height))

        points.forEach { point ->
            val x = leftPx + point.count.toFloat() / maxCount * plotW
            val y = baseline - ((point.avgSec ?: 0.0) / 60.0 / maxAvg * plotH).toFloat()
            drawCircle(p.accent.copy(alpha = 0.83f), radius = 6.dp.toPx(), center = Offset(x, y))
            drawCircle(p.card, radius = 6.dp.toPx(), center = Offset(x, y), style = Stroke(width = 2.dp.toPx()))
        }
    }
}

@Composable
fun GroupTable(rows: List<Stats.GroupRow>) {
    val p = AppTheme.palette
    if (rows.isEmpty()) {
        ChartEmpty()
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, p.hair, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(p.field)
                .padding(horizontal = 10.dp, vertical = 9.dp)
        ) {
            listOf("训练频率" to 1.2f, "周数" to 0.7f, "次数" to 0.7f, "平均单次" to 1f, "达标率" to 0.8f).forEach {
                Text(
                    it.first,
                    color = p.sub,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(it.second),
                    textAlign = if (it.first == "训练频率") TextAlign.Start else TextAlign.End
                )
            }
        }
        rows.forEachIndexed { index, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp)
            ) {
                Text("${row.perWeek} 次/周", color = p.ink, fontSize = 11.sp, modifier = Modifier.weight(1.2f))
                Text("${row.weeks}", color = p.ink, fontSize = 11.sp, modifier = Modifier.weight(0.7f), textAlign = TextAlign.End)
                Text("${row.sessions}", color = p.ink, fontSize = 11.sp, modifier = Modifier.weight(0.7f), textAlign = TextAlign.End)
                Text(row.avgSec?.let { "${Formatters.minutes(it.toLong())} 分" } ?: "—", color = p.ink, fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                Text(row.ratePercent?.let { "$it%" } ?: "—", color = p.ink, fontSize = 11.sp, modifier = Modifier.weight(0.8f), textAlign = TextAlign.End)
            }
            if (index != rows.lastIndex) DividerLine()
        }
    }
}

@Composable
private fun ChartEmpty() {
    val p = AppTheme.palette
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("完成训练后，趋势和频率图表会自动生成。", color = p.sub, fontSize = 13.sp)
    }
}

@Composable
fun LegendDot(color: Color) {
    Box(Modifier.size(9.dp).clip(CircleShape).background(color))
}

@Composable
fun ChartSpacer() {
    Spacer(Modifier.height(4.dp))
}
