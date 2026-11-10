package com.wanghoufan.zaijiuyidian.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wanghoufan.zaijiuyidian.data.db.SessionEntity
import com.wanghoufan.zaijiuyidian.domain.ChartMode
import com.wanghoufan.zaijiuyidian.domain.Formatters
import com.wanghoufan.zaijiuyidian.domain.Stats
import com.wanghoufan.zaijiuyidian.domain.TrainingMode
import com.wanghoufan.zaijiuyidian.domain.TrainingSession
import com.wanghoufan.zaijiuyidian.ui.AppViewModel
import com.wanghoufan.zaijiuyidian.ui.Sheet
import com.wanghoufan.zaijiuyidian.ui.UiState
import com.wanghoufan.zaijiuyidian.ui.components.AppCard
import com.wanghoufan.zaijiuyidian.ui.components.AppSwitch
import com.wanghoufan.zaijiuyidian.ui.components.AppTextField
import com.wanghoufan.zaijiuyidian.ui.components.ChartNote
import com.wanghoufan.zaijiuyidian.ui.components.DropdownField
import com.wanghoufan.zaijiuyidian.ui.components.EmptyBlock
import com.wanghoufan.zaijiuyidian.ui.components.PillTabs
import com.wanghoufan.zaijiuyidian.ui.components.ProjectTabs
import com.wanghoufan.zaijiuyidian.ui.components.SectionHeader
import com.wanghoufan.zaijiuyidian.ui.components.StatCard
import com.wanghoufan.zaijiuyidian.ui.components.TabItem
import com.wanghoufan.zaijiuyidian.ui.components.TextAction
import com.wanghoufan.zaijiuyidian.ui.components.TopTitle
import com.wanghoufan.zaijiuyidian.ui.components.ValueGroupTable
import com.wanghoufan.zaijiuyidian.ui.components.ValueScatterChart
import com.wanghoufan.zaijiuyidian.ui.components.ValueTrendChart
import com.wanghoufan.zaijiuyidian.ui.components.ValueWeeklyChart
import com.wanghoufan.zaijiuyidian.ui.theme.AppTheme
import java.time.ZoneId

@Composable
fun HistoryScreenV11(state: UiState, vm: AppViewModel, modifier: Modifier = Modifier) {
    val zone = ZoneId.systemDefault()
    val projectNames = state.projects.associate { it.id to it.name }
    val projectFilter = state.settings.lastProjectFilter ?: "current"
    val filterProject = when (projectFilter) {
        "all" -> null
        "current" -> state.selectedProject
        else -> state.projects.firstOrNull { it.id == projectFilter }
    }
    val scope: TrainingMode? = if (projectFilter == "all") null else filterProject?.mode ?: TrainingMode.TIME
    val filteredAll = Stats.filter(
        sessions = state.sessions,
        projectFilter = projectFilter,
        currentProjectId = state.selectedProject?.id,
        rangeDays = state.settings.lastRangeDays,
        query = state.searchQuery,
        nowMs = state.nowMs,
        zone = zone,
        projectNames = projectNames
    )
    val filtered = if (scope == null) filteredAll else filteredAll.filter { it.mode == scope }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 19.dp, end = 19.dp, top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            TopTitle(
                title = "历史",
                action = { TextAction("＋ 补录") { vm.openSheet(Sheet.EntryEditor(null)) } }
            )
        }

        item {
            ProjectTabs(
                items = historyTabItems(state),
                selectedId = historyTabSelectedId(projectFilter, state),
                enabled = true,
                onSelect = { id -> vm.setProjectFilter(if (id == ALL_TAB) "all" else id) }
            )
        }

        item {
            DropdownField(
                label = "时间",
                value = rangeLabel(state.settings.lastRangeDays),
                options = rangeOptions,
                onSelect = { key -> vm.setRangeDays(if (key == "all") null else key.toInt()) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            AppTextField(
                value = state.searchQuery,
                onValueChange = { vm.setSearchQuery(it) },
                placeholder = "搜索备注或项目"
            )
        }

        item {
            KpiGrid(scope = scope, sessions = filtered, zone = zone)
        }

        item {
            ChartCard(
                state = state,
                vm = vm,
                filtered = filtered,
                zone = zone,
                scope = scope
            )
        }

        item {
            SectionHeader(
                title = "训练记录 (${filtered.size})",
                actionText = "最新在前",
                onAction = { vm.openSheet(Sheet.SortInfo) }
            )
        }

        if (filtered.isEmpty()) {
            item {
                AppCard {
                    EmptyBlock("还没有记录。完成一次训练或点击右上角「补录」，就会显示在这里。")
                }
            }
        } else {
            items(items = filtered, key = { it.id }) { session ->
                SessionCard(
                    session = session,
                    projectName = projectNames[session.projectId] ?: "已删除项目",
                    vm = vm
                )
            }
        }

        if (state.deletedSessions.isNotEmpty()) {
            item {
                SectionHeader("最近删除 (${state.deletedSessions.size})")
            }
            items(items = state.deletedSessions, key = { "deleted-${it.id}" }) { session ->
                DeletedCard(
                    session = session,
                    projectName = projectNames[session.projectId] ?: "已删除项目",
                    onRestore = { vm.restoreSession(session.id) }
                )
            }
        }
    }
}

@Composable
private fun KpiGrid(scope: TrainingMode?, sessions: List<TrainingSession>, zone: ZoneId) {
    if (scope == TrainingMode.REPS) {
        val kpi = Stats.repsKpi(sessions)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("有效记录", kpi.count.toString(), "次", Modifier.weight(1f))
                StatCard(
                    "最高单次",
                    if (kpi.hasData) kpi.bestSessionTotal.toString() else "—",
                    if (kpi.hasData) "次" else null,
                    Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(
                    "最佳单组",
                    if (kpi.hasData) kpi.bestSet.toString() else "—",
                    if (kpi.hasData) "次" else null,
                    Modifier.weight(1f)
                )
                StatCard(
                    "最多组数",
                    if (kpi.hasData) kpi.maxSets.toString() else "—",
                    if (kpi.hasData) "组" else null,
                    Modifier.weight(1f)
                )
            }
        }
        return
    }
    if (scope == TrainingMode.TIME) {
        val kpi = Stats.kpi(sessions)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("有效记录", kpi.count.toString(), "次", Modifier.weight(1f))
                StatCard(
                    "平均实际",
                    if (kpi.hasData) Formatters.minutes(kpi.avgSec) else "—",
                    if (kpi.hasData) "分钟" else null,
                    Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(
                    "最佳时长",
                    if (kpi.hasData) Formatters.minutes(kpi.bestSec) else "—",
                    if (kpi.hasData) "分钟" else null,
                    Modifier.weight(1f)
                )
                StatCard(
                    "目标达成率",
                    if (kpi.hasData) Formatters.percent(kpi.ratePercent) else "—",
                    null,
                    Modifier.weight(1f)
                )
            }
        }
        return
    }
    val mixed = Stats.mixedKpi(sessions, zone)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("训练次数", mixed.count.toString(), "次", Modifier.weight(1f))
            StatCard("训练天数", mixed.days.toString(), "天", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("计时完成", mixed.timeCount.toString(), "次", Modifier.weight(1f))
            StatCard("计次完成", mixed.repsCount.toString(), "次", Modifier.weight(1f))
        }
    }
}

private val rangeOptions = listOf(
    "all" to "全部时间",
    "7" to "近 7 天",
    "30" to "近 30 天",
    "90" to "近 90 天"
)

private fun rangeLabel(days: Int?): String =
    if (days == null) "全部时间" else "近 $days 天"

private const val ALL_TAB = "__all__"

private fun historyTabItems(state: UiState): List<TabItem> =
    listOf(TabItem(ALL_TAB, "全部项目")) +
        state.projects.filter { !it.isArchived }.map { TabItem(it.id, it.name) }

private fun historyTabSelectedId(projectFilter: String, state: UiState): String? =
    when (projectFilter) {
        "all" -> ALL_TAB
        "current" -> state.selectedProject?.id
        else -> projectFilter
    }

@Composable
private fun ChartCard(
    state: UiState,
    vm: AppViewModel,
    filtered: List<TrainingSession>,
    zone: ZoneId,
    scope: TrainingMode?
) {
    val p = AppTheme.palette
    val mode = state.settings.lastChart
    val title = when (mode) {
        ChartMode.TREND -> when (scope) {
            TrainingMode.TIME -> "实际时长趋势"
            TrainingMode.REPS -> "每次训练总次数趋势"
            null -> "全项目训练趋势"
        }
        ChartMode.WEEKLY -> when (scope) {
            TrainingMode.TIME -> "训练频率与平均时长"
            TrainingMode.REPS -> "训练频率与平均次数"
            null -> "训练次数与活跃天数"
        }
        ChartMode.SCATTER -> "每周次数与表现关系"
    }
    val chartKey = when (mode) {
        ChartMode.TREND -> "trend"
        ChartMode.WEEKLY -> "weekly"
        ChartMode.SCATTER -> "scatter"
    }
    val trendLabel = when (scope) {
        TrainingMode.TIME -> "时长趋势"
        TrainingMode.REPS -> "次数趋势"
        null -> "记录趋势"
    }

    AppCard {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    when (scope) {
                        TrainingMode.TIME -> "分钟"
                        TrainingMode.REPS -> "次"
                        null -> "训练频率"
                    },
                    color = p.sub,
                    fontSize = 10.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            PillTabs(
                items = listOf(
                    "trend" to trendLabel,
                    "weekly" to "周频率",
                    "scatter" to "频率关系"
                ),
                selected = chartKey,
                onSelect = { key ->
                    vm.setChartMode(
                        when (key) {
                            "weekly" -> ChartMode.WEEKLY
                            "scatter" -> ChartMode.SCATTER
                            else -> ChartMode.TREND
                        }
                    )
                }
            )

            if (scope == null) {
                ChartNote("全部项目只汇总训练次数、天数与类型数量；分钟和次数不合并成一条成绩轴。")
            }

            when (mode) {
                ChartMode.TREND -> {
                    Spacer(Modifier.height(8.dp))
                    PillTabs(
                        items = listOf(
                            "10" to "最近 10 次",
                            "30" to "最近 30 次",
                            "all" to "全部"
                        ),
                        selected = when (state.trendCount) {
                            10 -> "10"
                            30 -> "30"
                            else -> "all"
                        },
                        onSelect = { key -> vm.setTrendCount(if (key == "all") 0 else key.toInt()) }
                    )
                    Spacer(Modifier.height(4.dp))
                    val sortedAsc = filtered.sortedBy { it.endedAt }
                    val shown = if (state.trendCount <= 0) sortedAsc else sortedAsc.takeLast(state.trendCount)
                    ValueTrendChart(shown, scope)
                    ChartNote(
                        when (scope) {
                            TrainingMode.TIME -> "每根柱子代表一次训练；黄色短线是那次设定的目标。修改默认目标不会改变过去的数据。"
                            TrainingMode.REPS -> "每根柱子是一次练习的总次数；黄色短线是该次计划总数。最佳单组看上方指标，不等于总次数。"
                            null -> "这里只比较训练记录数量，不合并分钟与次数；选择单个项目可查看具体成绩。"
                        }
                    )
                }

                ChartMode.WEEKLY -> {
                    Spacer(Modifier.height(4.dp))
                    val weeks = if (filtered.isEmpty()) {
                        emptyList()
                    } else {
                        Stats.valueWeeklyWithGaps(filtered, scope, zone, state.nowMs, 8)
                    }
                    ValueWeeklyChart(weeks, state.nowMs, scope)
                    ChartNote(
                        when (scope) {
                            TrainingMode.TIME -> "柱子是每周训练次数，折线是每次训练平均分钟；两条纵轴单位不同。"
                            TrainingMode.REPS -> "柱子是每周训练次数，折线是该周每次练习平均完成的总次数；单组最佳另看上方。"
                            null -> "同一日多次训练只算一个活跃日；柱子和折线分别表示次数与天数，不相加。"
                        } + " " + weeklyNote(filtered, scope, zone)
                    )
                }

                ChartMode.SCATTER -> {
                    Spacer(Modifier.height(6.dp))
                    if (scope == TrainingMode.TIME) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("排除含暂停的记录", color = p.ink, fontSize = 12.sp)
                            AppSwitch(
                                checked = state.excludePaused,
                                onChange = { vm.setExcludePaused(it) }
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                    }
                    val eligible = if (scope == TrainingMode.TIME && state.excludePaused) {
                        filtered.filter { it.pauseCount == 0 }
                    } else {
                        filtered
                    }
                    val points = Stats.valueWeekly(eligible, scope, zone).filter { it.count > 0 }
                    val rows = Stats.valueGroups(eligible, scope, zone)
                    ChartNote(
                        when (scope) {
                            TrainingMode.TIME -> "每个点是一周。横轴是当周训练次数，纵轴是该周每次训练的平均分钟。"
                            TrainingMode.REPS -> "每个点是一周。横轴是当周训练次数，纵轴是该周每次训练的平均总次数。"
                            null -> "每个点是一周。横轴是当周训练次数，纵轴是当周活跃天数。"
                        }
                    )
                    ValueScatterChart(points, scope)
                    ValueGroupTable(rows, scope)
                    ChartNote("只展示已有数据的关联，不代表训练更频繁一定使成绩提高；样本不足时不可下结论。")
                }
            }
        }
    }
}

private fun weeklyNote(
    sessions: List<TrainingSession>,
    scope: TrainingMode?,
    zone: ZoneId
): String {
    val currentWeek = Stats.weekStart(System.currentTimeMillis(), zone)
    val weekSessions = sessions.filter { Stats.weekStart(it.endedAt, zone) == currentWeek }
    val days = weekSessions.map { Stats.dayStartFor(it.endedAt, zone) }.toSet().size
    return when (scope) {
        TrainingMode.REPS -> "本周（周一开始） · ${weekSessions.size} 次练习 · $days 天 · ${weekSessions.sumOf { it.repTotal }} 次累计"
        TrainingMode.TIME -> "本周（周一开始） · ${weekSessions.size} 次训练 · $days 天 · ${Math.round(weekSessions.sumOf { it.actualSec } / 60.0)} 分钟"
        null -> "本周（周一开始） · ${weekSessions.size} 次训练 · $days 天"
    }
}

@Composable
private fun SessionCard(session: TrainingSession, projectName: String, vm: AppViewModel) {
    val p = AppTheme.palette
    AppCard {
        Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(Formatters.dateTime(session.endedAt), color = p.sub, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    TagChip(if (session.isReps) "计次" else "计时", p.pale, p.accentDark)
                    if (session.achieved) {
                        TagChip("已达标", p.accent2, p.accentDark)
                    } else {
                        TagChip("未达标", p.field, p.sub)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    if (session.isReps) session.repTotal.toString() else Formatters.minutes(session.actualSec),
                    color = p.ink,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1).sp
                )
                Text(
                    if (session.isReps) "次" else "分钟",
                    color = p.sub,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 3.dp, bottom = 4.dp)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (session.isReps) {
                        "${session.repSetCount} 组 · 目标 ${session.plannedTotalReps} 次 · ${Formatters.percent(session.percentOfTarget)}"
                    } else {
                        "目标 ${Formatters.minutes(session.targetSec)} 分 · ${Formatters.percent(session.percentOfTarget)}"
                    },
                    color = p.sub,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            if (session.isReps && session.sets.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    session.setDetail,
                    color = p.sub,
                    fontSize = 11.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (session.note.isBlank()) projectName else "$projectName · ${session.note}",
                color = p.sub,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (session.pauseCount > 0 || session.isManual || session.source == SessionEntity.SOURCE_IMPORT) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (session.pauseCount > 0) {
                        TagChip(
                            if (session.pausedSec > 0) "含暂停 ${session.pauseCount} 次" else "含暂停",
                            p.pale,
                            p.amber
                        )
                    }
                    if (session.isManual) {
                        TagChip("手动补录", p.pale, p.accentDark)
                    }
                    if (session.source == SessionEntity.SOURCE_IMPORT) {
                        TagChip("导入", p.pale, p.accentDark)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("已保存", color = p.sub, fontSize = 10.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextAction("编辑") { vm.openSheet(Sheet.EntryEditor(session.id)) }
                    TextAction("删除", color = p.red) { vm.requestDeleteSession(session.id) }
                }
            }
        }
    }
}

@Composable
private fun DeletedCard(session: TrainingSession, projectName: String, onRestore: () -> Unit) {
    val p = AppTheme.palette
    AppCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(Formatters.dateTime(session.endedAt), color = p.sub, fontSize = 11.sp)
                Text(
                    if (session.isReps) {
                        "$projectName · ${session.repTotal} 次（${session.repSetCount} 组）"
                    } else {
                        "$projectName · ${Formatters.minutes(session.actualSec)} 分钟（目标 ${Formatters.minutes(session.targetSec)} 分）"
                    },
                    color = p.ink,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextAction("恢复", onClick = onRestore)
        }
    }
}

@Composable
private fun TagChip(text: String, background: Color, foreground: Color) {
    Text(
        text,
        color = foreground,
        fontSize = 10.sp,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 6.dp, vertical = 3.dp)
    )
}
