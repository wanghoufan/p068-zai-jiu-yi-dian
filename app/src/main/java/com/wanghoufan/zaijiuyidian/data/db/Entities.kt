package com.wanghoufan.zaijiuyidian.data.db

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
    val updatedAt: Long
) {
    val presets: List<Int>
        get() = presetsSec.split(',').mapNotNull { it.trim().toIntOrNull() }.ifEmpty { listOf(15, 20, 25) }
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
    val deletedAt: Long? = null
) {
    companion object {
        const val SOURCE_TIMER = "timer"
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_IMPORT = "import"
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
