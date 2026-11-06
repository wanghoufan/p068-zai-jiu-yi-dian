package com.wanghoufan.zaijiuyidian.domain

import com.wanghoufan.zaijiuyidian.data.db.ProjectEntity
import com.wanghoufan.zaijiuyidian.data.db.SessionEntity

enum class TrainingMode {
    TIME,
    REPS;

    companion object {
        fun from(value: String?): TrainingMode =
            if (value == ProjectEntity.MODE_REPS) REPS else TIME
    }
}

data class Project(
    val id: String,
    val name: String,
    val defaultTargetSec: Int,
    val presetsSec: List<Int>,
    val archivedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val mode: TrainingMode = TrainingMode.TIME,
    val plannedSets: Int = 3,
    val targetRepsPerSet: Int = 10
) {
    val isArchived: Boolean get() = archivedAt != null
    val defaultTargetMinutes: Double get() = defaultTargetSec / 60.0
    val isReps: Boolean get() = mode == TrainingMode.REPS
    val plannedTotalReps: Int get() = plannedSets * targetRepsPerSet
    val modeLabel: String get() = if (isReps) "计次" else "计时"
}

fun ProjectEntity.toModel() = Project(
    id = id,
    name = name,
    defaultTargetSec = defaultTargetSec,
    presetsSec = presets,
    archivedAt = archivedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    mode = TrainingMode.from(mode),
    plannedSets = plannedSets,
    targetRepsPerSet = targetRepsPerSet
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
    val deletedAt: Long? = null,
    val mode: TrainingMode = TrainingMode.TIME,
    val plannedSets: Int = 3,
    val targetRepsPerSet: Int = 10,
    val sets: List<Int> = emptyList()
) {
    val isReps: Boolean get() = mode == TrainingMode.REPS
    val repTotal: Int get() = sets.sum()
    val repBestSet: Int get() = sets.maxOrNull() ?: 0
    val repSetCount: Int get() = sets.size
    val plannedTotalReps: Int get() = plannedSets * targetRepsPerSet
    val achieved: Boolean
        get() = if (isReps) repTotal >= plannedTotalReps else actualSec >= targetSec
    val isPaused: Boolean get() = pauseCount > 0
    val isManual: Boolean get() = source == SessionEntity.SOURCE_MANUAL
    val percentOfTarget: Int
        get() = when {
            isReps && plannedTotalReps > 0 -> Math.round(repTotal * 100f / plannedTotalReps)
            !isReps && targetSec > 0 -> Math.round(actualSec * 100f / targetSec)
            else -> 0
        }
    val setDetail: String get() = sets.mapIndexed { index, reps -> "第${index + 1}组 $reps" }
        .joinToString(" · ")
}

fun SessionEntity.toModel(sets: List<Int> = emptyList()) = TrainingSession(
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
    deletedAt = deletedAt,
    mode = TrainingMode.from(mode),
    plannedSets = plannedSets,
    targetRepsPerSet = targetRepsPerSet,
    sets = sets
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
    val fontScalePercent: Int = 115,
    val autoBackupEnabled: Boolean = false,
    val autoBackupTreeUri: String? = null,
    val autoBackupLastSuccessAt: Long? = null,
    val autoBackupLastError: String? = null,
    val lastTimeProjectId: String? = null,
    val lastRepsProjectId: String? = null
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
