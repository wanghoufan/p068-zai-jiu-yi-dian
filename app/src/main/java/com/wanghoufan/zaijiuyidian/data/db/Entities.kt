package com.wanghoufan.zaijiuyidian.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val defaultTargetSec: Int,
    val presetsSec: String,
    val archivedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "TIME") val mode: String = MODE_TIME,
    @ColumnInfo(defaultValue = "3") val plannedSets: Int = 3,
    @ColumnInfo(defaultValue = "10") val targetRepsPerSet: Int = 10
) {
    val presets: List<Int>
        get() = presetsSec.split(',').mapNotNull { it.trim().toIntOrNull() }.ifEmpty { listOf(15, 20, 25) }

    companion object {
        const val MODE_TIME = "TIME"
        const val MODE_REPS = "REPS"
    }
}

@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("projectId"), Index("endedAt"), Index("deletedAt")]
)
data class SessionEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val startedAt: Long?,
    val endedAt: Long,
    val targetSec: Int,
    val actualSec: Int,
    val pauseCount: Int = 0,
    val pausedSec: Int = 0,
    val source: String = SOURCE_TIMER,
    val note: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    @ColumnInfo(defaultValue = "TIME") val mode: String = MODE_TIME,
    @ColumnInfo(defaultValue = "3") val plannedSets: Int = 3,
    @ColumnInfo(defaultValue = "10") val targetRepsPerSet: Int = 10
) {
    companion object {
        const val SOURCE_TIMER = "timer"
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_IMPORT = "import"
        const val SOURCE_REPS = "reps"
        const val MODE_TIME = "TIME"
        const val MODE_REPS = "REPS"
    }
}

@Entity(
    tableName = "rep_sets",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("sessionId"),
        Index(value = ["sessionId", "ordinal"], unique = true)
    ]
)
data class RepSetEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val ordinal: Int,
    val actualReps: Int
)

@Entity(tableName = "rep_draft")
data class RepDraftEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val projectId: String,
    val plannedSets: Int,
    val targetRepsPerSet: Int,
    val setsCsv: String,
    val nextReps: Int,
    val touchedAt: Long
) {
    val sets: List<Int>
        get() = setsCsv.split(',').mapNotNull { it.trim().toIntOrNull() }

    companion object {
        const val SINGLETON_ID = 1
    }
}

@Entity(tableName = "active_session")
data class ActiveSessionEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val projectId: String,
    val targetSec: Int,
    val accumulatedMs: Long,
    val segmentStartedElapsedMs: Long?,
    val pausedAtElapsedMs: Long?,
    val pauseCount: Int,
    val pausedMs: Long,
    val bootWallBase: Long,
    val bootCount: Int,
    val startedAtWall: Long,
    val notifiedAtTarget: Boolean,
    val lastKnownElapsedMs: Long,
    val lastKnownAtWall: Long,
    val interrupted: Boolean = false
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
