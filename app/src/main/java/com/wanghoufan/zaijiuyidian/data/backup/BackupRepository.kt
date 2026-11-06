package com.wanghoufan.zaijiuyidian.data.backup

import android.content.Context
import androidx.room.withTransaction
import com.wanghoufan.zaijiuyidian.data.db.AppDatabase
import com.wanghoufan.zaijiuyidian.data.db.ProjectEntity
import com.wanghoufan.zaijiuyidian.data.db.RepDraftEntity
import com.wanghoufan.zaijiuyidian.data.db.RepSetEntity
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
        val bundle = db.withTransaction {
            ExportBundle(
                projects = db.projectDao().getAll(),
                sessions = db.sessionDao().getAll(),
                repSets = db.repSetDao().getAll().groupBy { it.sessionId }
                    .mapValues { (_, rows) -> rows.sortedBy { it.ordinal }.map { it.actualReps } },
                draft = db.repDraftDao().get()
            )
        }
        return BackupCodec.encode(
            projects = bundle.projects,
            sessions = bundle.sessions,
            projectId = settings.selectedProjectId,
            settings = settings,
            appVersion = appVersion,
            nowMs = nowMs,
            repSets = bundle.repSets,
            draft = bundle.draft?.let {
                BackupDraft(
                    projectId = it.projectId,
                    plannedSets = it.plannedSets,
                    targetRepsPerSet = it.targetRepsPerSet,
                    sets = it.sets,
                    nextReps = it.nextReps,
                    touchedAt = it.touchedAt
                )
            }
        )
    }

    suspend fun exportCsv(): String {
        val projects = db.projectDao().getAll().associateBy { it.id }
        val sessions = db.sessionDao().getLive().sortedWith(compareByDescending<SessionEntity> { it.endedAt }.thenByDescending { it.id })
        val sets = db.repSetDao().getAll().groupBy { it.sessionId }
            .mapValues { (_, rows) -> rows.sortedBy { it.ordinal }.map { it.actualReps } }
        val header = listOf(
            "完成时间", "项目", "模式", "目标秒数", "实际秒数", "计划组数", "每组目标",
            "实际组数", "各组次数", "总次数", "达标", "暂停次数", "暂停秒数", "来源", "备注", "记录ID"
        )
        val rows = sessions.map { s ->
            val isReps = s.mode == SessionEntity.MODE_REPS
            val actualSets = sets[s.id].orEmpty()
            listOf(
                Formatters.dateTime(s.endedAt),
                projects[s.projectId]?.name ?: "已删除项目",
                if (isReps) "计次" else "计时",
                if (isReps) "" else s.targetSec.toString(),
                if (isReps) "" else s.actualSec.toString(),
                s.plannedSets.toString(),
                s.targetRepsPerSet.toString(),
                if (isReps) actualSets.size.toString() else "",
                if (isReps) actualSets.joinToString("+") else "",
                if (isReps) actualSets.sum().toString() else "",
                if (isReps) {
                    if (actualSets.sum() >= s.plannedSets * s.targetRepsPerSet) "是" else "否"
                } else {
                    if (s.actualSec >= s.targetSec) "是" else "否"
                },
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
        val newIds = newSessions.map { it.id }.toSet()
        val importedSets = data.sessions.filter { it.id in newIds }.flatMap { session ->
            session.sets.mapIndexed { index, reps ->
                RepSetEntity(
                    id = "${session.id}:${index + 1}",
                    sessionId = session.id,
                    ordinal = index + 1,
                    actualReps = reps
                )
            }
        }
        if (importedSets.isNotEmpty()) db.repSetDao().upsertAll(importedSets)
        if (data.draft != null && db.repDraftDao().get() == null) {
            val d = data.draft
            db.repDraftDao().upsert(
                RepDraftEntity(
                    projectId = d.projectId,
                    plannedSets = d.plannedSets,
                    targetRepsPerSet = d.targetRepsPerSet,
                    setsCsv = d.sets.joinToString(","),
                    nextReps = d.nextReps,
                    touchedAt = d.touchedAt
                )
            )
        }
        ImportCounts(
            projectsAdded = newProjects.size,
            sessionsAdded = newSessions.size,
            conflicts = (data.projects.size - newProjects.size) + (data.sessions.size - newSessions.size)
        )
    }

    suspend fun replace(data: BackupData, settings: AppSettings): ImportCounts {
        val snapshot = writeSnapshot(settings)
        db.withTransaction {
            db.repSetDao().deleteAll()
            db.repDraftDao().delete()
            db.sessionDao().deleteAll()
            db.projectDao().deleteAll()
            db.projectDao().upsertAll(data.projects.map { it.toEntity() })
            db.sessionDao().upsertAll(data.sessions.map { it.toEntity() })
            val allSets = data.sessions.flatMap { session ->
                session.sets.mapIndexed { index, reps ->
                    RepSetEntity(
                        id = "${session.id}:${index + 1}",
                        sessionId = session.id,
                        ordinal = index + 1,
                        actualReps = reps
                    )
                }
            }
            if (allSets.isNotEmpty()) db.repSetDao().upsertAll(allSets)
            data.draft?.let { d ->
                db.repDraftDao().upsert(
                    RepDraftEntity(
                        projectId = d.projectId,
                        plannedSets = d.plannedSets,
                        targetRepsPerSet = d.targetRepsPerSet,
                        setsCsv = d.sets.joinToString(","),
                        nextReps = d.nextReps,
                        touchedAt = d.touchedAt
                    )
                )
            }
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
        SessionEntity.SOURCE_REPS -> "计次"
        else -> "计时"
    }

    private data class ExportBundle(
        val projects: List<ProjectEntity>,
        val sessions: List<SessionEntity>,
        val repSets: Map<String, List<Int>>,
        val draft: RepDraftEntity?
    )

    private fun csv(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
}
