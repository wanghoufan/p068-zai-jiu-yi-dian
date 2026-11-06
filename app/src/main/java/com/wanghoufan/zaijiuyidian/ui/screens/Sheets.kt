package com.wanghoufan.zaijiuyidian.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wanghoufan.zaijiuyidian.data.backup.BackupEntry
import com.wanghoufan.zaijiuyidian.data.backup.BackupSource
import com.wanghoufan.zaijiuyidian.domain.Formatters
import com.wanghoufan.zaijiuyidian.domain.Project
import com.wanghoufan.zaijiuyidian.domain.TrainingMode
import com.wanghoufan.zaijiuyidian.ui.AppViewModel
import com.wanghoufan.zaijiuyidian.ui.EntryDraft
import com.wanghoufan.zaijiuyidian.ui.RepEntryDraft
import com.wanghoufan.zaijiuyidian.ui.Sheet
import com.wanghoufan.zaijiuyidian.ui.UiState
import com.wanghoufan.zaijiuyidian.ui.components.AppCard
import com.wanghoufan.zaijiuyidian.ui.components.AppSwitch
import com.wanghoufan.zaijiuyidian.ui.components.AppTextField
import com.wanghoufan.zaijiuyidian.ui.components.ConfirmDialog
import com.wanghoufan.zaijiuyidian.ui.components.DangerButton
import com.wanghoufan.zaijiuyidian.ui.components.DividerLine
import com.wanghoufan.zaijiuyidian.ui.components.DropdownField
import com.wanghoufan.zaijiuyidian.ui.components.PrimaryButton
import com.wanghoufan.zaijiuyidian.ui.components.SecondaryButton
import com.wanghoufan.zaijiuyidian.ui.components.SectionHeader
import com.wanghoufan.zaijiuyidian.ui.components.TextAction
import com.wanghoufan.zaijiuyidian.ui.theme.AppTheme
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetsHost(
    state: UiState,
    vm: AppViewModel,
    onPickBackupFolder: () -> Unit = {}
) {
    val p = AppTheme.palette
    val sheet = state.sheet

    when (sheet) {
        is Sheet.DeleteConfirm -> {
            ConfirmDialog(
                title = "删除这条训练记录？",
                message = "删除后统计图表会立即重新计算。删除后仍可在提示条中撤销。",
                confirmText = "确定删除",
                danger = true,
                onConfirm = { vm.confirmDeleteSession() },
                onDismiss = { vm.dismissSheet() }
            )
            return
        }
        is Sheet.ImportReplaceConfirm -> {
            ConfirmDialog(
                title = "最后确认覆盖",
                message = "将用备份中的全部数据替换当前记录与项目配置。覆盖前会先保存一份本机安全副本，但此操作不可直接撤销。",
                confirmText = "覆盖恢复",
                danger = true,
                onConfirm = { vm.importReplace() },
                onDismiss = { vm.openSheet(Sheet.ImportPreview) }
            )
            return
        }
        is Sheet.ArchiveConfirm -> {
            val project = state.projects.firstOrNull { it.id == sheet.projectId }
            ConfirmDialog(
                title = "归档「${project?.name ?: "该项目"}」？",
                message = "该项目已有训练记录。归档后不再出现在训练选择中，历史记录仍会保留，可随时恢复。",
                confirmText = "归档项目",
                onConfirm = { vm.archiveProject(sheet.projectId) },
                onDismiss = { vm.dismissSheet() }
            )
            return
        }
        is Sheet.DeleteProjectConfirm -> {
            val project = state.projects.firstOrNull { it.id == sheet.projectId }
            ConfirmDialog(
                title = "删除「${project?.name ?: "该项目"}」？",
                message = "该项目没有训练记录，删除后不可恢复。",
                confirmText = "确定删除",
                danger = true,
                onConfirm = { vm.deleteProject(sheet.projectId) },
                onDismiss = { vm.dismissSheet() }
            )
            return
        }
        is Sheet.DiscardRepDraft -> {
            ConfirmDialog(
                title = "放弃这次草稿？",
                message = "已记录的组数会被放弃，正式历史不受影响。想稍后继续就保留草稿。",
                confirmText = "确认放弃",
                danger = true,
                onConfirm = { vm.discardRepDraft() },
                onDismiss = { vm.dismissSheet() }
            )
            return
        }
        null -> return
        else -> Unit
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { vm.dismissSheet() },
        sheetState = sheetState,
        containerColor = p.card,
        contentColor = p.ink,
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .width(42.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(p.hair)
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 26.dp)
        ) {
            when (sheet) {
                Sheet.ProjectPicker -> ProjectPickerSheet(state, vm)
                is Sheet.ProjectEdit -> ProjectEditSheet(state, vm, sheet.isNew, sheet.mode)
                Sheet.GoalEditor -> GoalEditorSheet(state, vm)
                Sheet.PresetEditor -> PresetEditorSheet(state, vm)
                Sheet.RepsDefaultsEditor -> RepsDefaultsSheet(state, vm)
                Sheet.RepsPlanEditor -> RepsPlanSheet(state, vm)
                is Sheet.RepSetEditor -> RepSetEditSheet(state, vm, sheet.index)
                Sheet.FinishReps -> FinishRepsSheet(state, vm)
                Sheet.FontScalePicker -> FontScaleSheet(state, vm)
                is Sheet.FinishConfirm -> FinishSheet(state, vm, sheet)
                is Sheet.EntryEditor -> EntryEditorSheet(state, vm, sheet.sessionId)
                Sheet.ImportPreview -> ImportPreviewSheet(state, vm)
                Sheet.BackupManager -> BackupManagerSheet(state, vm, onPickBackupFolder)
                is Sheet.BackupRestorePreview -> BackupRestorePreviewSheet(state, vm, sheet.entry)
                Sheet.SortInfo -> SortInfoSheet(vm)
                else -> Unit
            }
        }
    }
}

@Composable
private fun SheetTitle(title: String, subtitle: String? = null) {
    val p = AppTheme.palette
    Text(title, color = p.ink, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp)
    if (subtitle != null) {
        Text(
            subtitle,
            color = p.sub,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 7.dp, bottom = 4.dp)
        )
    }
}

@Composable
private fun SheetActions(
    cancelText: String? = null,
    cancel: () -> Unit = {},
    confirmText: String,
    confirm: () -> Unit,
    danger: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (cancelText != null) {
            SecondaryButton(cancelText, cancel, Modifier.weight(1f))
        }
        if (danger) {
            DangerButton(confirmText, confirm, Modifier.weight(1f))
        } else {
            PrimaryButton(confirmText, confirm, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ProjectPickerSheet(state: UiState, vm: AppViewModel) {
    val p = AppTheme.palette
    val current = state.selectedProject
    val mode = state.tab.trainMode
    val activeProjects = state.projects.filter {
        !it.isArchived && (mode == null || it.mode == mode)
    }
    val archived = state.projects.filter {
        it.isArchived && (mode == null || it.mode == mode)
    }
    SheetTitle(
        if (mode == TrainingMode.REPS) "选择计次项目" else if (mode == TrainingMode.TIME) "选择计时项目" else "管理训练项目",
        "每个项目记住自己的记录方式；训练项目之间历史互不混用。"
    )
    Spacer(Modifier.height(8.dp))
    activeProjects.forEach { project ->
        ProjectChoice(project, project.id == current?.id) { vm.selectProject(project.id) }
    }
    if (archived.isNotEmpty()) {
        Text(
            "已归档项目",
            color = p.sub,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
        )
        archived.forEach { project ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(p.field)
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(project.name, color = p.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("已归档 · ${project.modeLabel}", color = p.sub, fontSize = 11.sp)
                }
                TextAction("恢复") { vm.restoreProject(project.id) }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SecondaryButton(
            "编辑当前项目",
            { vm.openSheet(Sheet.ProjectEdit(false, current?.mode)) },
            Modifier.weight(1f)
        )
        PrimaryButton(
            "＋ 新建${if (mode == TrainingMode.REPS) "计次" else "计时"}项目",
            { vm.openSheet(Sheet.ProjectEdit(true, mode)) },
            Modifier.weight(1f)
        )
    }
    if (current != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextAction("归档当前项目") { vm.openSheet(Sheet.ArchiveConfirm(current.id)) }
            TextAction("删除当前项目", color = p.red) { vm.requestDeleteProject(current.id) }
        }
    }
}

@Composable
private fun ProjectChoice(project: Project, selected: Boolean, onClick: () -> Unit) {
    val p = AppTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) p.accent2 else p.card)
            .border(1.dp, if (selected) p.accent else p.hair, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(project.name, color = p.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (project.isReps) {
                    "计次 · ${project.plannedSets} 组 × ${project.targetRepsPerSet} 次"
                } else {
                    "计时 · 目标 ${Formatters.minutes(project.defaultTargetSec)} 分钟"
                },
                color = p.sub,
                fontSize = 11.sp
            )
        }
        Text(if (selected) "✓" else "›", color = if (selected) p.accent else p.sub, fontSize = 15.sp)
    }
}

@Composable
private fun ProjectEditSheet(
    state: UiState,
    vm: AppViewModel,
    isNew: Boolean,
    initialMode: TrainingMode?
) {
    val p = AppTheme.palette
    val current = state.selectedProject
    var name by remember(isNew, current?.id) {
        mutableStateOf(if (isNew) "" else current?.name.orEmpty())
    }
    var mode by remember(isNew, initialMode, current?.id) {
        mutableStateOf(initialMode ?: current?.mode ?: TrainingMode.TIME)
    }
    var minutes by remember(isNew, current?.id) {
        mutableStateOf(current?.let { Formatters.minutes(it.defaultTargetSec) } ?: "15")
    }
    var sets by remember(isNew, current?.id) {
        mutableStateOf((current?.plannedSets ?: 3).toString())
    }
    var reps by remember(isNew, current?.id) {
        mutableStateOf((current?.targetRepsPerSet ?: 10).toString())
    }

    SheetTitle(
        if (isNew) "新建训练项目" else "编辑项目",
        if (isNew) "按时间或次数记录，创建后记录方式不再改变。" else "已有项目不能改记录方式；可修改名称与默认计划。"
    )
    Spacer(Modifier.height(8.dp))
    Text("项目名称", color = p.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    AppTextField(name, { if (it.length <= 24) name = it }, placeholder = "例如：引体向上")

    Text("记录方式", color = p.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ModeChoice("按时间", mode == TrainingMode.TIME, enabled = isNew, modifier = Modifier.weight(1f)) { mode = TrainingMode.TIME }
        ModeChoice("按次数", mode == TrainingMode.REPS, enabled = isNew, modifier = Modifier.weight(1f)) { mode = TrainingMode.REPS }
    }

    if (mode == TrainingMode.TIME) {
        Text("默认目标分钟（1～300）", color = p.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
        Spacer(Modifier.height(6.dp))
        AppTextField(minutes, { minutes = it }, keyboardType = KeyboardType.Decimal, placeholder = "15")
    } else {
        Row(
            modifier = Modifier.padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text("默认组数（1～40）", color = p.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                AppTextField(sets, { sets = it }, keyboardType = KeyboardType.Number, placeholder = "3")
            }
            Column(Modifier.weight(1f)) {
                Text("每组目标（1～999）", color = p.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                AppTextField(reps, { reps = it }, keyboardType = KeyboardType.Number, placeholder = "10")
            }
        }
    }

    SheetActions(
        cancelText = "取消",
        cancel = { vm.openSheet(Sheet.ProjectPicker) },
        confirmText = if (isNew) "创建项目" else "保存",
        confirm = {
            val min = minutes.replace(',', '.').toDoubleOrNull() ?: 0.0
            val setCount = sets.toIntOrNull() ?: 0
            val repCount = reps.toIntOrNull() ?: 0
            if (isNew) {
                vm.createProject(name, mode, min, setCount, repCount)
            } else {
                vm.updateProjectSettings(name, min, setCount, repCount)
            }
        }
    )
}

@Composable
private fun ModeChoice(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val p = AppTheme.palette
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) p.accent2 else p.field)
            .border(1.dp, if (selected) p.accent else p.hair, RoundedCornerShape(11.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) p.accentDark else p.sub,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun GoalEditorSheet(state: UiState, vm: AppViewModel) {
    val p = AppTheme.palette
    val current = state.selectedProject
    var value by remember {
        mutableStateOf(current?.let { Formatters.minutes(it.defaultTargetSec) } ?: "25")
    }
    SheetTitle("自定义目标时长", "设置后自动记忆，下次打开仍使用这个目标。")
    Spacer(Modifier.height(8.dp))
    Text("分钟（1～300）", color = p.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    AppTextField(value, { value = it }, placeholder = "例如：25", keyboardType = KeyboardType.Decimal)
    Text("支持最多 1 位小数", color = p.sub, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
    SheetActions(
        cancelText = "取消",
        cancel = { vm.openSheet(Sheet.ProjectPicker) },
        confirmText = "保存目标",
        confirm = { value.replace(',', '.').trim().toDoubleOrNull()?.let { vm.setTargetMinutes(it) } ?: vm.showToast("请输入 1～300 分钟") }
    )
}

@Composable
private fun PresetEditorSheet(state: UiState, vm: AppViewModel) {
    val p = AppTheme.palette
    val current = state.selectedProject
    var value by remember {
        mutableStateOf(current?.presetsSec?.joinToString(", ") { Formatters.minutes(it) } ?: "10, 15, 20, 25")
    }
    SheetTitle("编辑预设档位", "输入分钟数，以逗号隔开。支持 1～8 个档位，每个档位 1～300 分钟。")
    Spacer(Modifier.height(8.dp))
    Text("预设分钟", color = p.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    AppTextField(value, { value = it }, placeholder = "例如：10, 15, 20, 25", keyboardType = KeyboardType.Decimal)
    Text("保存后会按从小到大排序并去重", color = p.sub, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
    SheetActions(
        cancelText = "取消",
        cancel = { vm.openSheet(Sheet.ProjectPicker) },
        confirmText = "保存档位",
        confirm = {
            val list = value.split(',', '，', '、', ' ').filter { it.isNotBlank() }
                .mapNotNull { it.trim().replace(',', '.').toDoubleOrNull() }
            if (list.isEmpty()) vm.showToast("请设置 1～8 个有效档位") else vm.setPresets(list)
        }
    )
}

@Composable
private fun RepsDefaultsSheet(state: UiState, vm: AppViewModel) {
    val project = state.selectedProject
    var sets by remember(project?.id) { mutableStateOf((project?.plannedSets ?: 3).toString()) }
    var reps by remember(project?.id) { mutableStateOf((project?.targetRepsPerSet ?: 10).toString()) }
    SheetTitle("计次默认计划", "以后开始新训练时使用这个组数和每组目标。")
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Text("默认组数（1～40）", color = AppTheme.palette.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            AppTextField(sets, { sets = it }, keyboardType = KeyboardType.Number, placeholder = "3")
        }
        Column(Modifier.weight(1f)) {
            Text("每组目标（1～999）", color = AppTheme.palette.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            AppTextField(reps, { reps = it }, keyboardType = KeyboardType.Number, placeholder = "10")
        }
    }
    SheetActions(
        cancelText = "取消",
        cancel = { vm.dismissSheet() },
        confirmText = "保存默认计划",
        confirm = { vm.setRepsDefaults(sets.toIntOrNull() ?: 0, reps.toIntOrNull() ?: 0) }
    )
}

@Composable
private fun RepsPlanSheet(state: UiState, vm: AppViewModel) {
    val draft = state.repDraft
    var sets by remember(draft?.touchedAt) { mutableStateOf((draft?.plannedSets ?: 3).toString()) }
    var reps by remember(draft?.touchedAt) { mutableStateOf((draft?.targetRepsPerSet ?: 10).toString()) }
    SheetTitle("调整这次计划", "只影响本次训练，不会修改旧成绩。")
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Text("计划组数（1～40）", color = AppTheme.palette.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            AppTextField(sets, { sets = it }, keyboardType = KeyboardType.Number, placeholder = "3")
        }
        Column(Modifier.weight(1f)) {
            Text("每组目标（1～999）", color = AppTheme.palette.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            AppTextField(reps, { reps = it }, keyboardType = KeyboardType.Number, placeholder = "10")
        }
    }
    Text(
        "已经完成的组数和实际次数不会因修改计划而变化。",
        color = AppTheme.palette.sub,
        fontSize = 11.sp,
        lineHeight = 17.sp,
        modifier = Modifier.padding(top = 10.dp)
    )
    SheetActions(
        cancelText = "取消",
        cancel = { vm.dismissSheet() },
        confirmText = "保存本次计划",
        confirm = { vm.updateDraftPlan(sets.toIntOrNull() ?: 0, reps.toIntOrNull() ?: 0) }
    )
}

@Composable
private fun RepSetEditSheet(state: UiState, vm: AppViewModel, index: Int) {
    val current = state.repDraft?.sets?.getOrNull(index) ?: 0
    var value by remember(index, current) { mutableStateOf(current.toString()) }
    SheetTitle("修改第 ${index + 1} 组", "只修改这组的实际完成次数。")
    Spacer(Modifier.height(10.dp))
    Text("实际次数（1～999）", color = AppTheme.palette.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    AppTextField(value, { value = it }, keyboardType = KeyboardType.Number, placeholder = current.toString())
    Row(
        modifier = Modifier.padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SecondaryButton("删除本组", { vm.removeRepSet(index) }, Modifier.weight(1f))
        PrimaryButton(
            "确认修改",
            { vm.editRepSet(index, value.toIntOrNull() ?: 0) },
            Modifier.weight(1f)
        )
    }
}

@Composable
private fun FinishRepsSheet(state: UiState, vm: AppViewModel) {
    val p = AppTheme.palette
    val draft = state.repDraft ?: return
    var note by remember(draft.touchedAt) { mutableStateOf("") }
    val total = draft.sets.sum()
    val best = draft.sets.maxOrNull() ?: 0
    SheetTitle("结束并保存", "保存真实完成的组数，不必做满原计划。")
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        MiniMetric("完成组数", draft.sets.size.toString(), "组", Modifier.weight(1f))
        MiniMetric("总次数", total.toString(), "次", Modifier.weight(1f))
        MiniMetric("最佳单组", best.toString(), "次", Modifier.weight(1f))
    }
    Text(
        "计划 ${draft.plannedSets} 组 × ${draft.targetRepsPerSet} 次 · 实际 ${draft.sets.joinToString(" + ")} = $total 次",
        color = p.sub,
        fontSize = 11.sp,
        lineHeight = 17.sp,
        modifier = Modifier.padding(top = 12.dp)
    )
    Text("备注（选填）", color = p.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
    Spacer(Modifier.height(6.dp))
    AppTextField(note, { if (it.length <= 300) note = it }, placeholder = "今天的感觉、动作状态等", singleLine = false, minHeight = 70)
    SheetActions(
        cancelText = "继续训练",
        cancel = { vm.dismissSheet() },
        confirmText = "完成保存",
        confirm = { vm.finishReps(note) }
    )
}

@Composable
private fun MiniMetric(label: String, value: String, suffix: String, modifier: Modifier = Modifier) {
    val p = AppTheme.palette
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(p.field)
            .padding(horizontal = 10.dp, vertical = 11.dp)
    ) {
        Text(label, color = p.sub, fontSize = 10.sp)
        Text(
            "$value $suffix",
            color = p.ink,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun FinishSheet(state: UiState, vm: AppViewModel, sheet: Sheet.FinishConfirm) {
    val p = AppTheme.palette
    var note by remember(sheet) { mutableStateOf("") }
    val achieved = sheet.actualSec >= sheet.targetSec
    SheetTitle("本次训练结束")
    Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("实际完成 ", color = p.sub, fontSize = 13.sp)
        Text(
            Formatters.clock(sheet.actualSec.toLong()),
            color = p.accent,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold
        )
        Text("　目标 ${Formatters.clock(sheet.targetSec.toLong())}", color = p.sub, fontSize = 13.sp)
    }
    Text(
        if (achieved) "已达到目标，表现不错。" else "未达到目标，也可以如实记录。",
        color = p.sub,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 6.dp)
    )
    Text("本次备注（选填）", color = p.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp))
    Spacer(Modifier.height(6.dp))
    AppTextField(note, { if (it.length <= 300) note = it }, placeholder = "例如：今天调整了呼吸，感觉更稳定", singleLine = false, minHeight = 70)
    SheetActions(
        cancelText = "不计入",
        cancel = { vm.discardFinish() },
        confirmText = "计入历史",
        confirm = { vm.saveFinish(note) }
    )
    Text("仅点「计入历史」才会保存有效训练记录。", color = p.sub, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
}

@Composable
private fun EntryEditorSheet(state: UiState, vm: AppViewModel, sessionId: String?) {
    val p = AppTheme.palette
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val draft = remember(sessionId, state.projects) { vm.entryDraftFor(sessionId) }
    var projectId by remember(draft) { mutableStateOf(draft.projectId) }
    var endedAt by remember(draft) { mutableLongStateOf(draft.endedAtMs) }
    var targetText by remember(draft) { mutableStateOf(minutesInput(draft.targetSec)) }
    var actualText by remember(draft) { mutableStateOf(minutesInput(draft.actualSec)) }
    var note by remember(draft) { mutableStateOf(draft.note) }

    val existing = sessionId?.let { id -> state.sessions.firstOrNull { it.id == id } }
    val project = state.projects.firstOrNull { it.id == projectId } ?: state.selectedProject
    val isReps = project?.isReps == true
    val repBaseline = remember(sessionId, projectId) {
        if (existing?.isReps == true && existing.projectId == projectId) {
            Triple(existing.plannedSets, existing.targetRepsPerSet, existing.sets)
        } else {
            val pr = project
            Triple(pr?.plannedSets ?: 3, pr?.targetRepsPerSet ?: 10, emptyList())
        }
    }
    var setTexts by remember(sessionId, projectId) {
        mutableStateOf(
            if (repBaseline.third.isNotEmpty()) repBaseline.third.map { it.toString() }
            else List(repBaseline.first) { repBaseline.second.toString() }
        )
    }

    fun localDateTime(): LocalDateTime =
        Instant.ofEpochMilli(endedAt).atZone(zone).toLocalDateTime()

    SheetTitle(if (sessionId == null) "补录训练记录" else "编辑训练记录", "保存后所有图表和统计会立即重新计算。")

    Text("所属项目", color = p.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
    Spacer(Modifier.height(6.dp))
    DropdownField(
        label = "项目",
        value = project?.let { "${it.name} · ${it.modeLabel}" } ?: "请选择",
        options = state.projects.map { it.id to "${it.name} · ${it.modeLabel}" },
        onSelect = { projectId = it }
    )

    Text("完成时间", color = p.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton(
            "日期 ${Formatters.dateShort(endedAt)}",
            {
                val dt = localDateTime()
                DatePickerDialog(context, { _, y, m, d ->
                    val updated = localDateTime().withYear(y).withMonth(m + 1).withDayOfMonth(d)
                    endedAt = updated.atZone(zone).toInstant().toEpochMilli()
                }, dt.year, dt.monthValue - 1, dt.dayOfMonth).show()
            },
            Modifier.weight(1f)
        )
        SecondaryButton(
            "时间 ${String.format(Locale.CHINA, "%02d:%02d", localDateTime().hour, localDateTime().minute)}",
            {
                val dt = localDateTime()
                TimePickerDialog(context, { _, h, min ->
                    val updated = localDateTime().withHour(h).withMinute(min).withSecond(0).withNano(0)
                    endedAt = updated.atZone(zone).toInstant().toEpochMilli()
                }, dt.hour, dt.minute, true).show()
            },
            Modifier.weight(1f)
        )
    }

    if (isReps) {
        Text(
            "计划 ${repBaseline.first} 组 × ${repBaseline.second} 次 · 逐组填写实际次数",
            color = p.ink,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 14.dp)
        )
        Spacer(Modifier.height(8.dp))
        setTexts.forEachIndexed { index, text ->
            Row(
                modifier = Modifier.padding(bottom = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${index + 1} 组",
                    color = p.sub,
                    fontSize = 11.sp,
                    modifier = Modifier.width(34.dp)
                )
                AppTextField(
                    value = text,
                    onValueChange = { newValue ->
                        if (newValue.length <= 3 && newValue.all(Char::isDigit)) {
                            setTexts = setTexts.toMutableList().also { it[index] = newValue }
                        }
                    },
                    keyboardType = KeyboardType.Number,
                    placeholder = repBaseline.second.toString(),
                    modifier = Modifier.weight(1f)
                )
                TextAction("删除", color = p.red) {
                    if (setTexts.size > 1) {
                        setTexts = setTexts.toMutableList().also { it.removeAt(index) }
                    } else {
                        vm.showToast("至少保留 1 组")
                    }
                }
            }
        }
        if (setTexts.size < 40) {
            TextAction("＋ 添加一组") {
                setTexts = setTexts + repBaseline.second.toString()
            }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 14.dp)) {
            Column(Modifier.weight(1f)) {
                Text("目标分钟", color = p.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                AppTextField(targetText, { targetText = it }, keyboardType = KeyboardType.Decimal, placeholder = "25")
            }
            Column(Modifier.weight(1f)) {
                Text("实际分钟", color = p.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                AppTextField(actualText, { actualText = it }, keyboardType = KeyboardType.Decimal, placeholder = "10")
            }
        }
    }

    Text("备注（选填）", color = p.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
    Spacer(Modifier.height(6.dp))
    AppTextField(note, { if (it.length <= 300) note = it }, placeholder = "这次的感受或方法", singleLine = false, minHeight = 66)

    SheetActions(
        cancelText = "取消",
        cancel = { vm.dismissSheet() },
        confirmText = "保存记录",
        confirm = {
            if (isReps) {
                vm.saveRepEntry(
                    RepEntryDraft(
                        id = sessionId,
                        projectId = projectId,
                        endedAtMs = endedAt,
                        plannedSets = repBaseline.first,
                        targetRepsPerSet = repBaseline.second,
                        sets = setTexts.mapNotNull { it.toIntOrNull() },
                        note = note
                    )
                )
            } else {
                val targetSec = targetText.replace(',', '.').toDoubleOrNull()?.let { Math.round(it * 60).toInt() }
                val actualSec = actualText.replace(',', '.').toDoubleOrNull()?.let { Math.round(it * 60).toInt() }
                if (targetSec == null || actualSec == null) {
                    vm.showToast("请填写正确的时长")
                } else {
                    vm.saveEntry(
                        EntryDraft(
                            id = sessionId,
                            projectId = projectId,
                            endedAtMs = endedAt,
                            targetSec = targetSec,
                            actualSec = actualSec,
                            note = note
                        )
                    )
                }
            }
        }
    )
}

private fun minutesInput(sec: Int): String {
    val minutes = sec / 60.0
    val text = String.format(Locale.CHINA, "%.2f", minutes).trimEnd('0').trimEnd('.')
    return text.ifEmpty { "0" }
}

@Composable
private fun ImportPreviewSheet(state: UiState, vm: AppViewModel) {
    val p = AppTheme.palette
    val preview = state.importPreview
    SheetTitle("准备恢复备份", "已检查文件格式与校验摘要。请选择恢复方式。")
    if (preview != null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(p.field)
                .padding(12.dp)
        ) {
            PreviewLine("备份包含项目", "${preview.projectCount} 个（新增 ${preview.newProjects}，冲突 ${preview.conflictProjects}）")
            PreviewLine("备份包含记录", "${preview.sessionCount} 条（新增 ${preview.newSessions}，冲突 ${preview.conflictSessions}）")
            if (preview.repsSessions > 0) {
                PreviewLine("其中计次记录", "${preview.repsSessions} 条（含每组明细）")
            }
            if (preview.hasDraft) {
                PreviewLine("未完成草稿", "1 份（仅覆盖恢复会替换）")
            }
            if (preview.deletedSessions > 0) {
                PreviewLine("已删除标记", "${preview.deletedSessions} 条（不会复活）")
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    ChoiceRow(
        title = "合并记录",
        detail = "相同记录 ID 只保留现有版本，避免重复",
        onClick = { vm.importMerge() }
    )
    ChoiceRow(
        title = "覆盖当前数据",
        detail = "先保存本机安全副本，再用备份替换",
        onClick = { vm.openSheet(Sheet.ImportReplaceConfirm) }
    )
    Text(
        "覆盖模式会替换个人记录及项目设置。建议先导出当前备份。",
        color = p.red,
        fontSize = 11.sp,
        lineHeight = 17.sp,
        modifier = Modifier.padding(top = 10.dp)
    )
    TextAction("取消") { vm.clearImport() }
}

@Composable
private fun PreviewLine(label: String, value: String) {
    val p = AppTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = p.sub, fontSize = 12.sp)
        Text(value, color = p.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    detail: String,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val p = AppTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) p.accent2 else p.card)
            .border(1.dp, if (selected) p.accent else p.hair, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = p.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = p.sub, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
        }
        Text(if (selected) "✓" else "›", color = if (selected) p.accent else p.sub, fontSize = 15.sp)
    }
}

@Composable
private fun FontScaleSheet(state: UiState, vm: AppViewModel) {
    val p = AppTheme.palette
    val current = state.settings.fontScalePercent
    SheetTitle("字体大小", "只调整应用内文字，不改变手机系统设置；选择后立即生效。")
    Spacer(Modifier.height(8.dp))
    com.wanghoufan.zaijiuyidian.domain.FontScaleOptions.levels.forEach { (percent, label) ->
        ChoiceRow(
            title = label,
            detail = "文字大小 ${percent}%",
            selected = current == percent,
            onClick = { vm.setFontScalePercent(percent) }
        )
    }
    Spacer(Modifier.height(8.dp))
    Text("预览", color = p.sub, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    AppCard(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Column(Modifier.padding(13.dp)) {
            Text("目标 25 分钟", color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                "训练记录 · 今天 20:15 · 已达标",
                color = p.sub,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                "00:25:00",
                color = p.accent,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    SecondaryButton("完成", { vm.dismissSheet() }, Modifier.fillMaxWidth())
    Text(
        "设置会保存在本机；系统字体大小仍会叠加生效。",
        color = p.sub,
        fontSize = 11.sp,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun SortInfoSheet(vm: AppViewModel) {
    SheetTitle("历史记录顺序", "按完成时间从新到旧排列；完成时间相同时按记录 ID 稳定排序。修改时间后，列表与趋势图会一同更新。")
    Spacer(Modifier.height(14.dp))
    SecondaryButton("知道了", { vm.dismissSheet() }, Modifier.fillMaxWidth())
}

@Composable
private fun BackupManagerSheet(
    state: UiState,
    vm: AppViewModel,
    onPickBackupFolder: () -> Unit
) {
    val p = AppTheme.palette
    val settings = state.settings
    val hasFolder = !settings.autoBackupTreeUri.isNullOrBlank()
    val enabled = settings.autoBackupEnabled && hasFolder
    val autoEntries = state.backupEntries.filter { it.source == BackupSource.AUTO }
    val safetyEntries = state.backupEntries.filter { it.source == BackupSource.SAFETY }

    SheetTitle("备份与恢复", "自动备份写入你选择的文件夹；卸载后文件仍在。")

    AppCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(13.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("自动本地备份", color = p.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (enabled) "已开启 · 最近 7 个成功版本" else "未开启",
                        color = if (enabled) p.accent else p.sub,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                AppSwitch(enabled) {
                    if (!hasFolder) onPickBackupFolder() else vm.setAutoBackupEnabled(it)
                }
            }
            Text(
                when {
                    settings.autoBackupLastError != null -> "上次失败：${settings.autoBackupLastError}"
                    settings.autoBackupLastSuccessAt != null ->
                        "上次成功：${Formatters.dateTime(settings.autoBackupLastSuccessAt)}"
                    hasFolder -> "等待首次自动备份"
                    else -> "尚未选择备份文件夹"
                },
                color = if (settings.autoBackupLastError != null) p.red else p.sub,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SecondaryButton(
                    if (hasFolder) "更换文件夹" else "选择文件夹",
                    onPickBackupFolder,
                    Modifier.weight(1f)
                )
                SecondaryButton("立即备份", { vm.backupNow() }, Modifier.weight(1f))
            }
        }
    }

    Text(
        "建议选择 Documents 下的独立文件夹；Android 11+ 不允许选择 Download 或存储根目录。共享文件夹等同于把备份放在别人也可能看到的位置。",
        color = p.sub,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        modifier = Modifier.padding(top = 9.dp)
    )

    SectionHeader("自动备份（最多 7 个）", "刷新", { vm.loadBackupEntries() })
    if (state.backupEntriesLoading) {
        Text("正在读取备份列表…", color = p.sub, fontSize = 12.sp)
    } else if (autoEntries.isEmpty()) {
        Text(
            if (hasFolder) "还没有自动备份；授权后发生一次数据变动就会生成。" else "选择文件夹并开启后，会在这里显示。",
            color = p.sub,
            fontSize = 12.sp
        )
    } else {
        autoEntries.forEach { entry ->
            BackupEntryRow(entry, vm)
        }
    }

    if (safetyEntries.isNotEmpty()) {
        SectionHeader("恢复前保护点")
        safetyEntries.forEach { entry ->
            BackupEntryRow(entry, vm)
        }
    }

    Text(
        "手动备份文件不在这个列表里时，用设置中的数据管理 → 导入与恢复选择文件；所有恢复都会先写保护点。",
        color = p.sub,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        modifier = Modifier.padding(top = 12.dp)
    )
    TextAction("关闭") { vm.dismissSheet() }
}

@Composable
private fun BackupEntryRow(entry: BackupEntry, vm: AppViewModel) {
    val p = AppTheme.palette
    AppCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    Formatters.dateTime(entry.timeMs),
                    color = p.ink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (entry.source == BackupSource.SAFETY) "恢复前保护点" else "自动备份",
                    color = if (entry.source == BackupSource.SAFETY) p.amber else p.accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                if (entry.valid) {
                    "v${entry.version} · ${entry.projectCount} 个项目 · ${entry.sessionCount} 条记录" +
                        if (entry.deletedCount > 0) "（含删除 ${entry.deletedCount}）" else ""
                } else {
                    "损坏或无法读取：${entry.error ?: "未知错误"}"
                },
                color = if (entry.valid) p.sub else p.red,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 5.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (entry.sizeBytes < 1024) "${entry.sizeBytes} B" else "${entry.sizeBytes / 1024} KB",
                    color = p.sub,
                    fontSize = 10.sp
                )
                if (entry.valid) {
                    TextAction("预览") { vm.requestRestoreBackup(entry) }
                }
            }
        }
    }
}

@Composable
private fun BackupRestorePreviewSheet(state: UiState, vm: AppViewModel, entry: BackupEntry) {
    val p = AppTheme.palette
    SheetTitle("恢复备份预览", "已校验文件格式与摘要；确认前不会改动当前数据。")
    AppCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(13.dp)) {
            PreviewLine("备份时间", Formatters.dateTime(entry.timeMs))
            PreviewLine("来源", if (entry.source == BackupSource.SAFETY) "恢复前保护点" else "自动备份")
            PreviewLine("数据版本", "v${entry.version}")
            PreviewLine("项目", "${entry.projectCount} 个")
            PreviewLine("记录", "${entry.sessionCount} 条" + if (entry.deletedCount > 0) "（含删除 ${entry.deletedCount}）" else "")
            PreviewLine("校验状态", if (entry.valid) "通过" else "失败")
        }
    }
    Text(
        "恢复会替换当前项目、历史与设置。开始前会先在备份文件夹写入并校验一个新的保护点；若保护点失败，恢复会取消。",
        color = p.red,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        modifier = Modifier.padding(top = 10.dp)
    )
    SheetActions(
        cancelText = "取消",
        cancel = { vm.openSheet(Sheet.BackupManager) },
        confirmText = "确认恢复",
        confirm = { vm.confirmRestoreBackup(entry) }
    )
}
