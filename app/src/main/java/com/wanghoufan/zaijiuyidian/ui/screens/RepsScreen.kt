package com.wanghoufan.zaijiuyidian.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wanghoufan.zaijiuyidian.domain.Formatters
import com.wanghoufan.zaijiuyidian.domain.Project
import com.wanghoufan.zaijiuyidian.domain.TrainingMode
import com.wanghoufan.zaijiuyidian.ui.AppViewModel
import com.wanghoufan.zaijiuyidian.ui.Sheet
import com.wanghoufan.zaijiuyidian.ui.Tab
import com.wanghoufan.zaijiuyidian.ui.UiState
import com.wanghoufan.zaijiuyidian.ui.components.AppCard
import com.wanghoufan.zaijiuyidian.ui.components.AppTextField
import com.wanghoufan.zaijiuyidian.ui.components.PrimaryButton
import com.wanghoufan.zaijiuyidian.ui.components.SecondaryButton
import com.wanghoufan.zaijiuyidian.ui.components.SectionHeader
import com.wanghoufan.zaijiuyidian.ui.components.TextAction
import com.wanghoufan.zaijiuyidian.ui.components.TopTitle
import com.wanghoufan.zaijiuyidian.ui.theme.AppTheme

@Composable
fun RepsScreen(state: UiState, vm: AppViewModel, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    val project = state.selectedProject?.takeIf { it.isReps }
    val draft = state.repDraft?.takeIf { project != null && it.projectId == project.id }
    val otherDraft = state.repDraft?.takeIf { project != null && it.projectId != project.id }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 19.dp, end = 19.dp, top = 6.dp, bottom = 26.dp)
    ) {
        TopTitle("次数", eyebrow = "逐组记录 · 每一组都算数")

        if (project == null) {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "还没有计次项目。先新建一个，例如引体向上或俯卧撑。",
                        color = p.sub,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(
                        "＋ 新建计次项目",
                        onClick = { vm.openSheet(Sheet.ProjectEdit(true, TrainingMode.REPS)) }
                    )
                }
            }
            return@Column
        }

        ProjectSelector(
            project = project,
            enabled = draft == null && state.active == null,
            onClick = { vm.openSheet(Sheet.ProjectPicker) }
        )

        if (otherDraft != null) {
            val otherName = state.projects.firstOrNull { it.id == otherDraft.projectId }?.name ?: "其他项目"
            Spacer(Modifier.height(10.dp))
            NoticeCard(
                text = "$otherName 还有未完成草稿。开始新训练前，先继续或放弃它。",
                action = "去继续",
                onAction = { vm.resumeRepDraft() }
            )
        }

        if (state.active != null) {
            Spacer(Modifier.height(10.dp))
            NoticeCard(
                text = "时长训练正在进行。结束计时后才能开始计次训练。",
                action = null,
                onAction = {}
            )
        }

        Spacer(Modifier.height(14.dp))

        val plannedSets = draft?.plannedSets ?: project.plannedSets
        val targetReps = draft?.targetRepsPerSet ?: project.targetRepsPerSet
        val sets = draft?.sets.orEmpty()
        val total = sets.sum()
        val best = sets.maxOrNull() ?: 0
        val progress = if (plannedSets > 0) (sets.size.toFloat() / plannedSets).coerceIn(0f, 1f) else 0f

        AppCard(Modifier.fillMaxWidth(), shape = 22) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 17.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (draft != null) "正在进行" else "今天的练习",
                        color = p.ink,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (draft != null) "草稿已保存" else "按组记录",
                        color = if (draft != null) p.accentDark else p.sub,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (draft != null) p.accent2 else p.field)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Text(
                    "计划 $plannedSets 组 × $targetReps 次",
                    color = p.sub,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        total.toString(),
                        color = p.ink,
                        fontSize = 45.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-2).sp
                    )
                    Text(
                        " 次",
                        color = p.sub,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Spacer(Modifier.weight(1f))
                    Text("累计完成", color = p.sub, fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
                }
                Spacer(Modifier.height(10.dp))
                BoxProgress(progress)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "已完成 ${sets.size} / $plannedSets 组",
                        color = p.sub,
                        fontSize = 11.sp
                    )
                    TextAction(if (draft != null) "修改当次计划" else "修改默认计划") {
                        vm.openSheet(if (draft != null) Sheet.RepsPlanEditor else Sheet.RepsDefaultsEditor)
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            RepsMetric("完成组数", sets.size.toString(), "组", Modifier.weight(1f))
            RepsMetric("总次数", total.toString(), "次", Modifier.weight(1f))
            RepsMetric("最佳单组", if (sets.isEmpty()) "—" else best.toString(), if (sets.isEmpty()) "" else "次", Modifier.weight(1f))
        }

        Spacer(Modifier.height(14.dp))

        if (draft == null) {
            PrimaryButton("▶　开始训练", onClick = { vm.startRepsTraining() })
            Text(
                "逐组记录实际次数；离开页面、锁屏或进程重开后，草稿仍会保留。",
                color = p.sub,
                fontSize = 11.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
        } else {
            NextSetCard(
                next = draft.nextReps,
                setNumber = sets.size + 1,
                onAdd = { vm.addRepSet(it) }
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(
                    "放弃草稿",
                    { vm.openSheet(Sheet.DiscardRepDraft) },
                    Modifier.weight(1f)
                )
                SecondaryButton(
                    "结束并保存",
                    { vm.openSheet(Sheet.FinishReps) },
                    Modifier.weight(1f),
                    enabled = sets.isNotEmpty()
                )
            }

            if (sets.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                SectionHeader(
                    title = "已完成的组",
                    actionText = "撤销上一组",
                    onAction = { vm.undoLastRepSet() }
                )
                sets.forEachIndexed { index, reps ->
                    SetRow(
                        index = index,
                        reps = reps,
                        onEdit = { vm.openSheet(Sheet.RepSetEditor(index)) }
                    )
                }
            } else {
                Text(
                    "还没有记录 · 做完一组后点「完成一组」",
                    color = p.sub,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionHeader(
            title = "最近一次成绩",
            actionText = "查看历史",
            onAction = {
                vm.setProjectFilter("current")
                vm.selectTab(Tab.HISTORY)
            }
        )
        LastRepsCard(project = project, state = state)
    }
}

@Composable
private fun ProjectSelector(project: Project, enabled: Boolean, onClick: () -> Unit) {
    val p = AppTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(p.card)
            .border(1.dp, p.hair, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(project.name, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                "计次训练 · 默认 ${project.plannedSets} 组 × ${project.targetRepsPerSet} 次",
                color = p.sub,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        Text("⌄", color = p.sub, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun NextSetCard(next: Int, setNumber: Int, onAdd: (Int) -> Unit) {
    val p = AppTheme.palette
    var text by remember(setNumber, next) { mutableStateOf(next.toString()) }
    var error by remember(setNumber, next) { mutableStateOf<String?>(null) }

    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(15.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "下一组实际做了多少次？",
                    color = p.ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "第 $setNumber 组",
                    color = p.sub,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(p.field)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StepButton("−") {
                    val value = ((text.toIntOrNull() ?: next) - 1).coerceAtLeast(1)
                    text = value.toString()
                    error = null
                }
                AppTextField(
                    value = text,
                    onValueChange = {
                        if (it.length <= 3 && it.all(Char::isDigit)) {
                            text = it
                            error = null
                        }
                    },
                    placeholder = next.toString(),
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
                StepButton("+") {
                    val value = ((text.toIntOrNull() ?: next) + 1).coerceAtMost(999)
                    text = value.toString()
                    error = null
                }
            }
            if (error != null) {
                Text(error!!, color = p.red, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                "＋　完成一组",
                onClick = {
                    val value = text.toIntOrNull()
                    if (value == null || value !in 1..999) {
                        error = "请输入 1～999 的实际次数"
                    } else {
                        onAdd(value)
                    }
                }
            )
            Text(
                "只记录实际做完的次数；计划外多做的组也能继续添加。",
                color = p.sub,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 9.dp)
            )
        }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    val p = AppTheme.palette
    Column(
        modifier = Modifier
            .width(48.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(p.field)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(label, color = p.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SetRow(index: Int, reps: Int, onEdit: () -> Unit) {
    val p = AppTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 7.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(p.card)
            .border(1.dp, p.hair, RoundedCornerShape(11.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${index + 1}",
            color = p.sub,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(24.dp)
        )
        Text("第 ${index + 1} 组", color = p.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        Text("$reps 次", color = p.ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        TextAction("修改", onClick = onEdit)
    }
}

@Composable
private fun LastRepsCard(project: Project, state: UiState) {
    val p = AppTheme.palette
    val last = state.sessions.filter { it.projectId == project.id && it.isReps }.maxByOrNull { it.endedAt }
    AppCard(Modifier.fillMaxWidth()) {
        if (last == null) {
            Text(
                "还没有完成的计次练习，第一条记录即将诞生。",
                color = p.sub,
                fontSize = 13.sp,
                modifier = Modifier.padding(15.dp)
            )
        } else {
            Column(Modifier.padding(15.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(Formatters.dateTime(last.endedAt), color = p.sub, fontSize = 11.sp)
                    Text("${last.repSetCount} 组", color = p.sub, fontSize = 11.sp)
                }
                Row(
                    modifier = Modifier.padding(top = 7.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        "${last.repTotal}",
                        color = p.ink,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(" 次", color = p.sub, fontSize = 12.sp, modifier = Modifier.padding(bottom = 3.dp))
                    Spacer(Modifier.weight(1f))
                    Text(
                        "最佳单组 ${last.repBestSet} 次",
                        color = p.sub,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NoticeCard(text: String, action: String?, onAction: () -> Unit) {
    val p = AppTheme.palette
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp)) {
            Text(text, color = p.sub, fontSize = 12.sp, lineHeight = 18.sp)
            if (action != null) {
                TextAction(action, onClick = onAction)
            }
        }
    }
}

@Composable
private fun RepsMetric(label: String, value: String, suffix: String, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(p.card)
            .border(1.dp, p.hair, RoundedCornerShape(14.dp))
            .padding(horizontal = 11.dp, vertical = 13.dp)
    ) {
        Text(label, color = p.sub, fontSize = 10.sp)
        Row(
            modifier = Modifier.padding(top = 6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                value,
                color = p.ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp
            )
            if (suffix.isNotBlank()) {
                Text(
                    suffix,
                    color = p.sub,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun BoxProgress(progress: Float) {
    val p = AppTheme.palette
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(p.hair)
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(7.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(p.accent)
        )
    }
}
