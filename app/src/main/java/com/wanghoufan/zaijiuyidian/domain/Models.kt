package com.wanghoufan.zaijiuyidian.domain

import com.wanghoufan.zaijiuyidian.data.db.ProjectEntity
import com.wanghoufan.zaijiuyidian.data.db.SessionEntity

data class Project(
    val id: String,
    val name: String,
    val defaultTargetSec: Int,
    val presetsSec: List<Int>,
    val archivedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long
) {
    val isArchived: Boolean get() = archivedAt != null
    val defaultTargetMinutes: Double get() = defaultTargetSec / 60.0
}

fun ProjectEntity.toModel() = Project(
    id = id,
    name = name,
    defaultTargetSec = defaultTargetSec,
    presetsSec = presets,
    archivedAt = archivedAt,
    createdAt = createdAt,
    updatedAt = updatedAt
)

data class TrainingSession(
    val id: String,
    val projectId: String,
    val startedAt: Long?,
    val endedAt: Long,
    val targetSec: Int,
    val actualSec: Int,
    val pauseCount: Int,
    val pausedSec: Int,
    val source: String,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
) {
    val achieved: Boolean get() = actualSec >= targetSec
    val isPaused: Boolean get() = pauseCount > 0
    val isManual: Boolean get() = source == SessionEntity.SOURCE_MANUAL
    val percentOfTarget: Int get() = if (targetSec <= 0) 0 else Math.round(actualSec * 100f / targetSec)
}

fun SessionEntity.toModel() = TrainingSession(
    id = id,
    projectId = projectId,
    startedAt = startedAt,
    endedAt = endedAt,
    targetSec = targetSec,
    actualSec = actualSec,
    pauseCount = pauseCount,
    pausedSec = pausedSec,
    source = source,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)

enum class ThemeMode { LIGHT, DARK }

enum class ChartMode { TREND, WEEKLY, SCATTER }

data class AppSettings(
    val selectedProjectId: String?,
    val theme: ThemeMode,
    val soundEnabled: Boolean,
    val vibrationEnabled: Boolean,
    val voiceEnabled: Boolean = true,
    val lastRangeDays: Int?, // null = all time
    val lastChart: ChartMode,
    val lastProjectFilter: String?, // "current" / "all" / project id
    val fontScalePercent: Int = 115
)

val DefaultSettings = AppSettings(
    selectedProjectId = null,
    theme = ThemeMode.LIGHT,
    soundEnabled = true,
    vibrationEnabled = true,
    voiceEnabled = true,
    lastRangeDays = null,
    lastChart = ChartMode.TREND,
    lastProjectFilter = "current",
    fontScalePercent = 115
)

object FontScaleOptions {
    val levels = listOf(
        100 to "小",
        115 to "标准",
        130 to "大",
        145 to "特大"
    )

    fun label(percent: Int): String =
        levels.firstOrNull { it.first == percent }?.second ?: "标准"

    fun clamp(percent: Int): Int = percent.coerceIn(100, 145)
}
