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
import com.wanghoufan.zaijiuyidian.ui.components.GroupTable
import com.wanghoufan.zaijiuyidian.ui.components.PillTabs
import com.wanghoufan.zaijiuyidian.ui.components.ScatterChart
import com.wanghoufan.zaijiuyidian.ui.components.SectionHeader
import com.wanghoufan.zaijiuyidian.ui.components.StatCard
import com.wanghoufan.zaijiuyidian.ui.components.TextAction
import com.wanghoufan.zaijiuyidian.ui.components.TopTitle
import com.wanghoufan.zaijiuyidian.ui.components.TrendChart
import com.wanghoufan.zaijiuyidian.ui.components.WeeklyChart
import com.wanghoufan.zaijiuyidian.ui.theme.AppTheme
import java.time.ZoneId

@Composable
fun HistoryScreen(state: UiState, vm: AppViewModel, modifier: Modifier = Modifier) {
    val zone = ZoneId.systemDefault()
    val projectNames = state.projects.associate { it.id to it.name }
    val projectFilter = state.settings.lastProjectFilter ?: "current"
    val filtered = Stats.filter(
        sessions = state.sessions,
        projectFilter = projectFilter,
        currentProjectId = state.selectedProject?.id,
        rangeDays = state.settings.lastRangeDays,
        query = state.searchQuery,
        nowMs = state.nowMs,
        zone = zone,
        projectNames = projectNames
    )
    val kpi = Stats.kpi(filtered)

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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownField(
                    label = "项目",
                    value = projectFilterLabel(projectFilter, state),
                    options = projectFilterOptions(state, projectFilter),
                    onSelect = { vm.setProjectFilter(it) },
                    modifier = Modifier.weight(1f)
                )
                DropdownField(
                    label = "时间",
                    value = rangeLabel(state.settings.lastRangeDays),
                    options = rangeOptions,
                    onSelect = { key -> vm.setRangeDays(if (key == "all") null else key.toInt()) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            AppTextField(
                value = state.searchQuery,
                onValueChange = { vm.setSearchQuery(it) },
                placeholder = "搜索备注或项目"
            )
        }

        item {
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
        }

        item {
            ChartCard(state = state, vm = vm, filtered = filtered, zone = zone)
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
                    "${projectName} · ${Formatters.minutes(session.actualSec)} 分钟（目标 ${Formatters.minutes(session.targetSec)} 分）",
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

private val rangeOptions = listOf(
    "all" to "全部时间",
    "7" to "近 7 天",
    "30" to "近 30 天",
    "90" to "近 90 天"
)

private fun rangeLabel(days: Int?): String =
    if (days == null) "全部时间" else "近 $days 天"

private fun projectFilterOptions(state: UiState, projectFilter: String): List<Pair<String, String>> {
    val currentId = state.selectedProject?.id
    val options = mutableListOf<Pair<String, String>>()
    options += "current" to "当前项目 · ${state.selectedProject?.name ?: "未选择"}"
    options += "all" to "全部项目"
    state.projects.forEach { project ->
        if (project.id != currentId) {
            val name = if (project.isArchived) "${project.name}（已归档）" else project.name
            options += project.id to name
        }
    }
    if (options.none { it.first == projectFilter }) {
        options += projectFilter to (state.projects.firstOrNull { it.id == projectFilter }?.name ?: "当前项目")
    }
    return options
}

private fun projectFilterLabel(projectFilter: String, state: UiState): String =
    projectFilterOptions(state, projectFilter).firstOrNull { it.first == projectFilter }?.second
        ?: "当前项目 · ${state.selectedProject?.name ?: "未选择"}"

@Composable
private fun ChartCard(
    state: UiState,
    vm: AppViewModel,
    filtered: List<TrainingSession>,
    zone: ZoneId
) {
    val p = AppTheme.palette
    val mode = state.settings.lastChart
    val title = when (mode) {
        ChartMode.TREND -> "实际时长趋势"
        ChartMode.WEEKLY -> "训练频率与平均时长"
        ChartMode.SCATTER -> "每周次数与表现关系"
    }
    val chartKey = when (mode) {
        ChartMode.TREND -> "trend"
        ChartMode.WEEKLY -> "weekly"
        ChartMode.SCATTER -> "scatter"
    }

    AppCard {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            PillTabs(
                items = listOf(
                    "trend" to "时长趋势",
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

            if ((state.settings.lastProjectFilter ?: "current") == "all" && mode != ChartMode.TREND) {
                ChartNote("当前包含多个项目：次数等总量可以比较，但不同练习的时长表现不宜合并推断。")
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
                    TrendChart(shown)
                    ChartNote("每根柱子代表一次训练；黄色短线表示该次目标，修改默认目标不会改变过去的数据。")
                }

                ChartMode.WEEKLY -> {
                    Spacer(Modifier.height(4.dp))
                    val weeks = if (filtered.isEmpty()) {
                        emptyList()
                    } else {
                        Stats.weeklyWithGaps(filtered, zone, state.nowMs, 8)
                    }
                    WeeklyChart(weeks, state.nowMs)
                    ChartNote("两条纵轴单位不同：观察训练次数变化时，平均时长是否也发生变化。")
                }

                ChartMode.SCATTER -> {
                    Spacer(Modifier.height(6.dp))
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
                    val eligible = if (state.excludePaused) {
                        filtered.filter { it.pauseCount == 0 }
                    } else {
                        filtered
                    }
                    val points = Stats.scatter(eligible, zone)
                    val rows = Stats.groupByWeeklyFrequency(eligible, zone)
                    ChartNote("每个点是一周。横轴是当周训练次数，纵轴是该周每次训练的平均时长。")
                    ScatterChart(points)
                    GroupTable(rows)
                    ChartNote("只展示关联，不代表次数越多或越少一定更好。目标与训练条件不同时应谨慎比较。")
                }
            }
        }
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
                if (session.achieved) {
                    TagChip("已达标", p.accent2, p.accentDark)
                } else {
                    TagChip("未达标", p.field, p.sub)
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    Formatters.minutes(session.actualSec),
                    color = p.ink,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1).sp
                )
                Text(
                    "分钟",
                    color = p.sub,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 3.dp, bottom = 4.dp)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "目标 ${Formatters.minutes(session.targetSec)} 分 · ${Formatters.percent(session.percentOfTarget)}",
                    color = p.sub,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
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
