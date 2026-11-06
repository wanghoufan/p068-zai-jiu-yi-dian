package com.wanghoufan.zaijiuyidian.data.backup

import android.content.Context
import androidx.room.withTransaction
import com.wanghoufan.zaijiuyidian.data.db.AppDatabase
import com.wanghoufan.zaijiuyidian.data.db.SessionEntity
import com.wanghoufan.zaijiuyidian.domain.AppSettings
import com.wanghoufan.zaijiuyidian.domain.Formatters
import java.io.File

class BackupRepository(
    private val context: Context,
    private val db: AppDatabase
) {

    suspend fun exportJson(
        appVersion: String,
        settings: AppSettings,
        nowMs: Long = System.currentTimeMillis()
    ): String {
        val projects = db.projectDao().getAll()
        val sessions = db.sessionDao().getAll()
        return BackupCodec.encode(
            projects = projects,
            sessions = sessions,
            projectId = settings.selectedProjectId,
            settings = settings,
            appVersion = appVersion,
            nowMs = nowMs
        )
    }

    suspend fun exportCsv(): String {
        val projects = db.projectDao().getAll().associateBy { it.id }
        val sessions = db.sessionDao().getLive().sortedWith(compareByDescending<SessionEntity> { it.endedAt }.thenByDescending { it.id })
        val header = listOf(
            "完成时间", "项目", "目标秒数", "实际秒数", "达标", "暂停次数", "暂停秒数", "来源", "备注", "记录ID"
        )
        val rows = sessions.map { s ->
            listOf(
                Formatters.dateTime(s.endedAt),
                projects[s.projectId]?.name ?: "已删除项目",
                s.targetSec.toString(),
                s.actualSec.toString(),
                if (s.actualSec >= s.targetSec) "是" else "否",
                s.pauseCount.toString(),
                s.pausedSec.toString(),
                sourceLabel(s.source),
                s.note,
                s.id
            )
        }
        val builder = StringBuilder()
        builder.append('\uFEFF')
        builder.append(header.joinToString(",") { csv(it) }).append("\r\n")
        rows.forEach { row -> builder.append(row.joinToString(",") { csv(it) }).append("\r\n") }
        return builder.toString()
    }

    suspend fun preview(data: BackupData): BackupPreview {
        val existingProjects = db.projectDao().getAll().map { it.id }.toSet()
        val existingSessions = db.sessionDao().getAll().map { it.id }.toSet()
        return BackupCodec.preview(data, existingProjects, existingSessions)
    }

    suspend fun merge(data: BackupData): ImportCounts = db.withTransaction {
        val existingProjects = db.projectDao().getAll().map { it.id }.toSet()
        val existingSessions = db.sessionDao().getAll().map { it.id }.toSet()
        val (newProjects, newSessions) = BackupCodec.merge(data, existingProjects, existingSessions)
        db.projectDao().upsertAll(newProjects)
        db.sessionDao().upsertAll(newSessions)
        ImportCounts(
            projectsAdded = newProjects.size,
            sessionsAdded = newSessions.size,
            conflicts = (data.projects.size - newProjects.size) + (data.sessions.size - newSessions.size)
        )
    }

    suspend fun replace(data: BackupData, settings: AppSettings): ImportCounts {
        val snapshot = writeSnapshot(settings)
        db.withTransaction {
            db.sessionDao().deleteAll()
            db.projectDao().deleteAll()
            db.projectDao().upsertAll(data.projects.map { it.toEntity() })
            db.sessionDao().upsertAll(data.sessions.map { it.toEntity() })
        }
        return ImportCounts(
            projectsAdded = data.projects.size,
            sessionsAdded = data.sessions.size,
            conflicts = 0,
            snapshotPath = snapshot.absolutePath
        )
    }

    private suspend fun writeSnapshot(settings: AppSettings): File {
        val dir = File(context.filesDir, "safety-backups").apply { mkdirs() }
        val stamp = Formatters.dateTime(System.currentTimeMillis()).replace(":", "-").replace(" ", "_")
        val file = File(dir, "safety-$stamp.json")
        file.writeText(exportJson("snapshot", settings))
        return file
    }

    private fun sourceLabel(source: String): String = when (source) {
        SessionEntity.SOURCE_MANUAL -> "手动补录"
        SessionEntity.SOURCE_IMPORT -> "导入"
        else -> "计时"
    }

    private fun csv(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
}
