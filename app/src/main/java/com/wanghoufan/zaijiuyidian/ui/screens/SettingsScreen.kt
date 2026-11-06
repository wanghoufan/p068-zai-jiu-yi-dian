package com.wanghoufan.zaijiuyidian.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wanghoufan.zaijiuyidian.BuildConfig
import com.wanghoufan.zaijiuyidian.domain.FontScaleOptions
import com.wanghoufan.zaijiuyidian.domain.Formatters
import com.wanghoufan.zaijiuyidian.domain.ThemeMode
import com.wanghoufan.zaijiuyidian.ui.AppViewModel
import com.wanghoufan.zaijiuyidian.ui.Sheet
import com.wanghoufan.zaijiuyidian.ui.UiState
import com.wanghoufan.zaijiuyidian.ui.components.AppCard
import com.wanghoufan.zaijiuyidian.ui.components.AppSwitch
import com.wanghoufan.zaijiuyidian.ui.components.DividerLine
import com.wanghoufan.zaijiuyidian.ui.components.TextAction
import com.wanghoufan.zaijiuyidian.ui.components.TopTitle
import com.wanghoufan.zaijiuyidian.ui.theme.AppTheme

@Composable
fun SettingsScreen(
    state: UiState,
    vm: AppViewModel,
    onExportJson: () -> Unit,
    onExportCsv: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = AppTheme.palette
    val project = state.selectedProject
    val presetDetail = if (project == null) {
        "暂无项目，请先新建"
    } else {
        val presets = if (project.presetsSec.isEmpty()) {
            "尚未设置档位"
        } else {
            project.presetsSec.joinToString(" / ") { Formatters.minutes(it) }
        }
        "${project.name} · $presets 分钟"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 19.dp, end = 19.dp, top = 10.dp, bottom = 32.dp)
    ) {
        TopTitle("设置", eyebrow = "偏好与数据管理")

        SettingsGroupTitle("训练偏好")
        AppCard(Modifier.fillMaxWidth()) {
            SettingsRow(
                name = "训练项目",
                detail = "新建、改名或切换练习",
                trailing = {
                    TextAction("管理　›") { vm.openSheet(Sheet.ProjectPicker) }
                }
            )
            DividerLine()
            SettingsRow(
                name = "预设目标",
                detail = presetDetail,
                trailing = {
                    TextAction("编辑　›") { vm.openSheet(Sheet.PresetEditor) }
                }
            )
            DividerLine()
            SettingsRow(
                name = "提示音",
                detail = "应用在前台时，达到目标会提示",
                trailing = {
                    AppSwitch(state.settings.soundEnabled) { vm.toggleSound() }
                }
            )
            DividerLine()
            SettingsRow(
                name = "震动提示",
                detail = "需要设备支持；应用在前台时生效",
                trailing = {
                    AppSwitch(state.settings.vibrationEnabled) { vm.toggleVibration() }
                }
            )
            DividerLine()
            SettingsRow(
                name = "语音提醒",
                detail = "应用在前台时：开始、每 3 分钟、达到目标播报",
                trailing = {
                    AppSwitch(state.settings.voiceEnabled) { vm.toggleVoice() }
                }
            )
        }

        SettingsGroupTitle("外观")
        AppCard(Modifier.fillMaxWidth()) {
            SettingsRow(
                name = "深色模式",
                detail = "首次打开默认浅色，切换后记住选择",
                trailing = {
                    AppSwitch(state.settings.theme == ThemeMode.DARK) { vm.toggleTheme() }
                }
            )
            DividerLine()
            SettingsRow(
                name = "字体大小",
                detail = "只调整应用内文字，不改变手机系统设置",
                trailing = {
                    TextAction("${FontScaleOptions.label(state.settings.fontScalePercent)}　›") {
                        vm.openSheet(Sheet.FontScalePicker)
                    }
                }
            )
        }

        SettingsGroupTitle("数据管理")
        AppCard(Modifier.fillMaxWidth()) {
            SettingsRow(
                name = "导出完整备份",
                detail = "JSON 文件 · 包含项目、历史、目标和设置",
                trailing = {
                    TextAction("导出　›") { onExportJson() }
                }
            )
            DividerLine()
            SettingsRow(
                name = "导出历史表格",
                detail = "CSV 文件 · 可以用 Excel 打开",
                trailing = {
                    TextAction("导出　›") { onExportCsv() }
                }
            )
            DividerLine()
            SettingsRow(
                name = "导入与恢复",
                detail = "检查备份后，选择合并或覆盖",
                trailing = {
                    TextAction("导入　›") { onImport() }
                }
            )
        }

        SettingsGroupTitle("关于")
        AppCard(Modifier.fillMaxWidth()) {
            SettingsRow(
                name = "版本",
                trailing = {
                    Text(
                        text = BuildConfig.VERSION_NAME,
                        color = p.sub,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            )
            DividerLine()
            SettingsRow(
                name = "离线可用",
                detail = "计时、历史、统计与备份均不需要联网或账号"
            )
            DividerLine()
            SettingsRow(
                name = "锁屏提醒",
                detail = "应用在后台或锁屏时不会响铃；返回后显示真实经过时长"
            )
        }

        Text(
            text = "记录保存在本机。卸载前请先导出完整备份，否则应用数据可能无法恢复。",
            color = p.sub,
            fontSize = 11.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(top = 14.dp, start = 4.dp, end = 4.dp)
        )
    }
}

@Composable
private fun SettingsGroupTitle(title: String) {
    val p = AppTheme.palette
    Text(
        text = title,
        color = p.sub,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 9.dp)
    )
}

@Composable
private fun SettingsRow(
    name: String,
    detail: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val p = AppTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = p.ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (detail != null) {
                Text(
                    text = detail,
                    color = p.sub,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 5.dp)
                )
            }
        }
        if (trailing != null) {
            Row(
                modifier = Modifier.padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                trailing()
            }
        }
    }
}
