package com.wanghoufan.zaijiuyidian.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wanghoufan.zaijiuyidian.domain.Formatters
import com.wanghoufan.zaijiuyidian.domain.Project
import com.wanghoufan.zaijiuyidian.domain.TimerMath
import com.wanghoufan.zaijiuyidian.domain.TrainingMode
import com.wanghoufan.zaijiuyidian.domain.TrainingSession
import com.wanghoufan.zaijiuyidian.ui.AppViewModel
import com.wanghoufan.zaijiuyidian.ui.Sheet
import com.wanghoufan.zaijiuyidian.ui.UiState
import com.wanghoufan.zaijiuyidian.ui.components.AppCard
import com.wanghoufan.zaijiuyidian.ui.components.Chip
import com.wanghoufan.zaijiuyidian.ui.components.PrimaryButton
import com.wanghoufan.zaijiuyidian.ui.components.ProjectTabs
import com.wanghoufan.zaijiuyidian.ui.components.SecondaryButton
import com.wanghoufan.zaijiuyidian.ui.components.SectionHeader
import com.wanghoufan.zaijiuyidian.ui.components.TabItem
import com.wanghoufan.zaijiuyidian.ui.components.TextAction
import com.wanghoufan.zaijiuyidian.ui.components.TopTitle
import com.wanghoufan.zaijiuyidian.ui.theme.AppTheme

@Composable
fun TrainScreen(state: UiState, vm: AppViewModel, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    val active = state.active
    val running = active != null && !active.interrupted
    val project = if (active != null) state.activeProject else state.selectedProject
    val targetSec = active?.targetSec ?: project?.defaultTargetSec ?: 0
    val elapsedSec = if (active != null) state.elapsedMs / 1000 else 0L
    val remaining = (targetSec - elapsedSec).coerceAtLeast(0L)
    val achieved = running && elapsedSec >= targetSec
    val paused = active != null && TimerMath.isPaused(active)

    val projectSessions = project?.let { pr ->
        state.sessions.filter { it.projectId == pr.id }
    }.orEmpty()
    val lastSession = projectSessions.maxByOrNull { it.endedAt }
    val bestSec = projectSessions.maxOfOrNull { it.actualSec } ?: 0

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 19.dp, end = 19.dp, top = 6.dp, bottom = 26.dp)
    ) {
        TopTitle("时长", eyebrow = "目标倒计时 · 完整计时训练")

        ProjectTabs(
            items = state.projects
                .filter { !it.isArchived && it.mode == TrainingMode.TIME }
                .map { TabItem(it.id, it.name) },
            selectedId = project?.id,
            enabled = !running,
            onSelect = { vm.selectProject(it) },
            emptyText = "还没有计时项目，点这里新建",
            onManage = { if (!running) vm.openSheet(Sheet.ProjectPicker) }
        )

        if (state.repDraft != null) {
            Spacer(Modifier.height(10.dp))
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(13.dp)) {
                    Text(
                        "次数训练还有未完成草稿，开始计时前请先保存或放弃。",
                        color = p.sub,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                    TextAction("去继续次数草稿") { vm.resumeRepDraft() }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        TimerCard(
            running = running,
            paused = paused,
            interrupted = active?.interrupted == true,
            achieved = achieved,
            elapsedSec = elapsedSec,
            targetSec = targetSec,
            remaining = remaining,
            progress = if (targetSec > 0) (elapsedSec.toFloat() / targetSec).coerceIn(0f, 1f) else 0f
        )

        if (active?.interrupted == true) {
            Spacer(Modifier.height(12.dp))
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("计时已中断", color = p.red, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "设备重启或系统中断了计时。请选择按最后可靠记录保存，或放弃本次训练。",
                        color = p.sub,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        } else if (running) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(
                    text = if (paused) "继续计时" else "暂停",
                    onClick = { vm.togglePause() },
                    modifier = Modifier.weight(1f)
                )
                PrimaryButton(
                    text = "结束训练",
                    onClick = { vm.requestFinish() },
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                "可随时结束。结束后由你决定本次是否计入历史记录。",
                color = p.sub,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
        } else {
            SectionHeader(
                title = "目标时长",
                actionText = "自定义时间 ＋",
                onAction = { vm.openSheet(Sheet.GoalEditor) }
            )
            PresetRow(project = project, onSelect = { vm.setTargetMinutes(it) })
            Spacer(Modifier.height(14.dp))
            PrimaryButton("▶　开始训练", onClick = { vm.startTraining() })
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            SmallStat(
                label = "上次实际",
                value = lastSession?.let { "${Formatters.minutes(it.actualSec)} 分" } ?: "—",
                modifier = Modifier.weight(1f)
            )
            SmallStat(
                label = "个人最佳",
                value = if (bestSec > 0) "${Formatters.minutes(bestSec)} 分" else "—",
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            "目标按项目记忆。计时、记录和统计均不需要联网。",
            color = p.sub,
            fontSize = 11.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

@Composable
private fun TimerCard(
    running: Boolean,
    paused: Boolean,
    interrupted: Boolean,
    achieved: Boolean,
    elapsedSec: Long,
    targetSec: Int,
    remaining: Long,
    progress: Float
) {
    val p = AppTheme.palette
    AppCard(Modifier.fillMaxWidth(), shape = 22) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(214.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 8.dp.toPx()
                    val inset = stroke / 2f
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(
                        color = p.hair,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke)
                    )
                    if (progress > 0f) {
                        drawArc(
                            color = if (achieved) p.accent else p.accent,
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        when {
                            interrupted -> "计时已中断"
                            paused -> "已暂停"
                            achieved -> "目标已达到"
                            running -> "距离目标"
                            else -> "目标倒计时"
                        },
                        color = p.sub,
                        fontSize = 12.sp
                    )
                    Text(
                        Formatters.clock(if (running || paused || interrupted) remaining else targetSec.toLong()),
                        color = p.ink,
                        fontSize = 46.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-2).sp,
                        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")
                    )
                    Text(
                        when {
                            interrupted -> "按最后可靠记录处理"
                            elapsedSec >= targetSec && running -> "已超出 ${Formatters.clock(elapsedSec - targetSec)}"
                            running || paused -> "已坚持 ${Formatters.clock(elapsedSec)}"
                            else -> "准备开始"
                        },
                        color = if (achieved) p.accent else p.sub,
                        fontSize = 12.sp,
                        fontWeight = if (achieved) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                MetaText("目标", "${Formatters.minutes(targetSec)} 分")
                Spacer(Modifier.width(22.dp))
                MetaText("实际", Formatters.clock(elapsedSec))
            }
        }
    }
}

@Composable
private fun MetaText(label: String, value: String) {
    val p = AppTheme.palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = p.sub, fontSize = 12.sp)
        Spacer(Modifier.width(4.dp))
        Text(value, color = p.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PresetRow(project: Project?, onSelect: (Double) -> Unit) {
    val presets = project?.presetsSec.orEmpty().ifEmpty { listOf(15 * 60, 20 * 60, 25 * 60) }
    val selectedSec = project?.defaultTargetSec
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        presets.forEach { sec ->
            Chip(
                text = "${Formatters.minutes(sec)} 分",
                selected = selectedSec == sec,
                onClick = { onSelect(sec / 60.0) }
            )
        }
    }
}

@Composable
private fun SmallStat(label: String, value: String, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(p.card)
            .border(1.dp, p.hair, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 14.dp)
    ) {
        Text(label, color = p.sub, fontSize = 11.sp)
        Text(
            value,
            color = p.ink,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.4).sp,
            modifier = Modifier.padding(top = 7.dp)
        )
    }
}
