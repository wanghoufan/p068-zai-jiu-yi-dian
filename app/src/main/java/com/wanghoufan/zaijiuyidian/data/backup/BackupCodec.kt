package com.wanghoufan.zaijiuyidian.data.backup

import com.wanghoufan.zaijiuyidian.data.db.ProjectEntity
import com.wanghoufan.zaijiuyidian.data.db.SessionEntity
import com.wanghoufan.zaijiuyidian.domain.AppSettings
import com.wanghoufan.zaijiuyidian.domain.Formatters
import com.wanghoufan.zaijiuyidian.domain.ThemeMode
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

class BackupException(message: String) : IllegalArgumentException(message)

data class BackupProject(
    val id: String,
    val name: String,
    val defaultTargetSec: Int,
    val presetsSec: List<Int>,
    val archivedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val mode: String = ProjectEntity.MODE_TIME,
    val plannedSets: Int = 3,
    val targetRepsPerSet: Int = 10
) {
    fun toEntity() = ProjectEntity(
        id = id,
        name = name,
        defaultTargetSec = defaultTargetSec,
        presetsSec = presetsSec.joinToString(","),
        archivedAt = archivedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        mode = mode,
        plannedSets = plannedSets,
        targetRepsPerSet = targetRepsPerSet
    )
}

data class BackupSession(
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
    val deletedAt: Long?,
    val mode: String = SessionEntity.MODE_TIME,
    val plannedSets: Int = 3,
    val targetRepsPerSet: Int = 10,
    val sets: List<Int> = emptyList()
) {
    fun toEntity() = SessionEntity(
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
        mode = mode,
        plannedSets = plannedSets,
        targetRepsPerSet = targetRepsPerSet
    )
}

data class BackupDraft(
    val projectId: String,
    val plannedSets: Int,
    val targetRepsPerSet: Int,
    val sets: List<Int>,
    val nextReps: Int,
    val touchedAt: Long
)

data class BackupSettings(
    val theme: String,
    val soundEnabled: Boolean,
    val vibrationEnabled: Boolean,
    val voiceEnabled: Boolean = true,
    val fontScalePercent: Int = 115,
    val lastRangeDays: Int? = null,
    val lastChart: String = "TREND",
    val lastProjectFilter: String? = "current",
    val lastTimeProjectId: String? = null,
    val lastRepsProjectId: String? = null
)

data class BackupData(
    val app: String,
    val version: Int,
    val exportedAt: String,
    val appVersion: String,
    val projectId: String?,
    val projects: List<BackupProject>,
    val sessions: List<BackupSession>,
    val settings: BackupSettings,
    val checksum: String,
    val draft: BackupDraft? = null
)

data class ParsedBackup(val data: BackupData)

data class BackupPreview(
    val projectCount: Int,
    val sessionCount: Int,
    val newProjects: Int,
    val newSessions: Int,
    val conflictProjects: Int,
    val conflictSessions: Int,
    val deletedSessions: Int,
    val repsSessions: Int = 0,
    val hasDraft: Boolean = false
)

data class ImportCounts(
    val projectsAdded: Int,
    val sessionsAdded: Int,
    val conflicts: Int,
    val snapshotPath: String? = null
)

object BackupCodec {

    const val APP_ID = "duration-progress"
    const val LEGACY_SCHEMA_VERSION = 1
    const val V2_SCHEMA_VERSION = 2
    const val SCHEMA_VERSION = 3
    const val MAX_FILE_BYTES = 15 * 1024 * 1024
    const val MAX_PROJECTS = 100
    const val MAX_SESSIONS = 100_000

    fun encode(
        projects: List<ProjectEntity>,
        sessions: List<SessionEntity>,
        projectId: String?,
        settings: AppSettings,
        appVersion: String,
        nowMs: Long,
        repSets: Map<String, List<Int>> = emptyMap(),
        draft: BackupDraft? = null
    ): String {
        val dto = BackupData(
            app = APP_ID,
            version = SCHEMA_VERSION,
            exportedAt = Formatters.iso(nowMs),
            appVersion = appVersion,
            projectId = projectId,
            projects = projects.sortedBy { it.id }.map {
                BackupProject(
                    id = it.id,
                    name = it.name,
                    defaultTargetSec = it.defaultTargetSec,
                    presetsSec = it.presets,
                    archivedAt = it.archivedAt,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt,
                    mode = it.mode,
                    plannedSets = it.plannedSets,
                    targetRepsPerSet = it.targetRepsPerSet
                )
            },
            sessions = sessions.sortedBy { it.id }.map {
                BackupSession(
                    id = it.id,
                    projectId = it.projectId,
                    startedAt = it.startedAt,
                    endedAt = it.endedAt,
                    targetSec = it.targetSec,
                    actualSec = it.actualSec,
                    pauseCount = it.pauseCount,
                    pausedSec = it.pausedSec,
                    source = it.source,
                    note = it.note,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt,
                    deletedAt = it.deletedAt,
                    mode = it.mode,
                    plannedSets = it.plannedSets,
                    targetRepsPerSet = it.targetRepsPerSet,
                    sets = repSets[it.id].orEmpty()
                )
            },
            settings = BackupSettings(
                theme = if (settings.theme == ThemeMode.DARK) "dark" else "light",
                soundEnabled = settings.soundEnabled,
                vibrationEnabled = settings.vibrationEnabled,
                voiceEnabled = settings.voiceEnabled,
                fontScalePercent = settings.fontScalePercent,
                lastRangeDays = settings.lastRangeDays,
                lastChart = settings.lastChart.name,
                lastProjectFilter = settings.lastProjectFilter,
                lastTimeProjectId = settings.lastTimeProjectId,
                lastRepsProjectId = settings.lastRepsProjectId
            ),
            checksum = "",
            draft = draft
        )
        return encodeData(dto)
    }

    fun encodeData(dto: BackupData): String {
        val checksum = checksum(dto)
        val json = JSONObject()
        json.put("app", dto.app)
        json.put("version", dto.version)
        json.put("exportedAt", dto.exportedAt)
        json.put("appVersion", dto.appVersion)
        json.put("projectId", dto.projectId ?: JSONObject.NULL)
        json.put("projects", JSONArray().apply {
            dto.projects.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("defaultTargetSec", p.defaultTargetSec)
                    put("presetsSec", JSONArray(p.presetsSec))
                    put("archivedAt", p.archivedAt ?: JSONObject.NULL)
                    put("createdAt", p.createdAt)
                    put("updatedAt", p.updatedAt)
                    if (dto.version >= SCHEMA_VERSION) {
                        put("mode", p.mode)
                        put("plannedSets", p.plannedSets)
                        put("targetRepsPerSet", p.targetRepsPerSet)
                    }
                })
            }
        })
        json.put("sessions", JSONArray().apply {
            dto.sessions.forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id)
                    put("projectId", s.projectId)
                    put("startedAt", s.startedAt ?: JSONObject.NULL)
                    put("endedAt", s.endedAt)
                    put("endedAtIso", Formatters.iso(s.endedAt))
                    put("targetSec", s.targetSec)
                    put("actualSec", s.actualSec)
                    put("pauseCount", s.pauseCount)
                    put("pausedSec", s.pausedSec)
                    put("source", s.source)
                    put("note", s.note)
                    put("createdAt", s.createdAt)
                    put("updatedAt", s.updatedAt)
                    put("deletedAt", s.deletedAt ?: JSONObject.NULL)
                    if (dto.version >= SCHEMA_VERSION) {
                        put("mode", s.mode)
                        put("plannedSets", s.plannedSets)
                        put("targetRepsPerSet", s.targetRepsPerSet)
                        put("sets", JSONArray(s.sets))
                    }
                })
            }
        })
        json.put("settings", JSONObject().apply {
            put("theme", dto.settings.theme)
            put("soundEnabled", dto.settings.soundEnabled)
            put("vibrationEnabled", dto.settings.vibrationEnabled)
            put("voiceEnabled", dto.settings.voiceEnabled)
            put("fontScalePercent", dto.settings.fontScalePercent)
            put("lastRangeDays", dto.settings.lastRangeDays ?: JSONObject.NULL)
            put("lastChart", dto.settings.lastChart)
            put("lastProjectFilter", dto.settings.lastProjectFilter ?: JSONObject.NULL)
            if (dto.version >= SCHEMA_VERSION) {
                put("lastTimeProjectId", dto.settings.lastTimeProjectId ?: JSONObject.NULL)
                put("lastRepsProjectId", dto.settings.lastRepsProjectId ?: JSONObject.NULL)
            }
        })
        if (dto.version >= SCHEMA_VERSION) {
            json.put(
                "draft",
                dto.draft?.let { d ->
                    JSONObject().apply {
                        put("projectId", d.projectId)
                        put("plannedSets", d.plannedSets)
                        put("targetRepsPerSet", d.targetRepsPerSet)
                        put("sets", JSONArray(d.sets))
                        put("nextReps", d.nextReps)
                        put("touchedAt", d.touchedAt)
                    }
                } ?: JSONObject.NULL
            )
        }
        json.put("checksum", checksum)
        return json.toString(2)
    }

    fun parse(raw: String): ParsedBackup {
        if (raw.length > MAX_FILE_BYTES) throw BackupException("备份文件超过 15MB")
        val root = try {
            JSONObject(raw)
        } catch (e: Exception) {
            throw BackupException("不是有效的 JSON 备份文件")
        }
        val app = root.optString("app", "")
        if (app != APP_ID) throw BackupException("不是「再久一点」的备份文件")
        val version = root.optInt("version", -1)
        if (version !in LEGACY_SCHEMA_VERSION..SCHEMA_VERSION) {
            throw BackupException("备份版本不受支持（$version）")
        }

        val projectsArray = root.optJSONArray("projects") ?: throw BackupException("备份缺少项目数据")
        val sessionsArray = root.optJSONArray("sessions") ?: throw BackupException("备份缺少训练记录")
        if (projectsArray.length() < 1) throw BackupException("备份中没有项目")
        if (projectsArray.length() > MAX_PROJECTS) throw BackupException("项目数量超出上限")
        if (sessionsArray.length() > MAX_SESSIONS) throw BackupException("训练记录数量超出上限")

        val projects = mutableListOf<BackupProject>()
        val projectIds = HashSet<String>()
        val projectModes = HashMap<String, String>()
        for (i in 0 until projectsArray.length()) {
            val o = projectsArray.optJSONObject(i) ?: throw BackupException("项目数据格式有误")
            val id = o.optString("id", "")
            val name = o.optString("name", "")
            if (id.isBlank()) throw BackupException("项目缺少 ID")
            if (!projectIds.add(id)) throw BackupException("项目 ID 重复：$id")
            if (name.isBlank() || name.length > 100) throw BackupException("项目名称无效")
            val target = o.optInt("defaultTargetSec", -1)
            if (target !in 60..18_000) throw BackupException("项目目标时长超出范围")
            val presetsArray = o.optJSONArray("presetsSec") ?: throw BackupException("项目预设档位缺失")
            if (presetsArray.length() !in 1..20) throw BackupException("项目预设档位数量无效")
            val presets = (0 until presetsArray.length()).map { idx ->
                val v = presetsArray.optInt(idx, -1)
                if (v !in 60..18_000) throw BackupException("项目预设档位超出范围")
                v
            }
            val mode = if (version >= SCHEMA_VERSION) {
                o.optString("mode", ProjectEntity.MODE_TIME).also {
                    if (it != ProjectEntity.MODE_TIME && it != ProjectEntity.MODE_REPS) {
                        throw BackupException("项目模式无效")
                    }
                }
            } else {
                ProjectEntity.MODE_TIME
            }
            val plannedSets = if (version >= SCHEMA_VERSION) o.optInt("plannedSets", 3) else 3
            val targetRepsPerSet = if (version >= SCHEMA_VERSION) o.optInt("targetRepsPerSet", 10) else 10
            if (plannedSets !in 1..40) throw BackupException("项目计划组数超出范围")
            if (targetRepsPerSet !in 1..999) throw BackupException("项目每组目标次数超出范围")
            projectModes[id] = mode
            projects.add(
                BackupProject(
                    id = id,
                    name = name,
                    defaultTargetSec = target,
                    presetsSec = presets.distinct().sorted(),
                    archivedAt = optLongOrNull(o, "archivedAt"),
                    createdAt = o.optLong("createdAt", 0L),
                    updatedAt = o.optLong("updatedAt", 0L),
                    mode = mode,
                    plannedSets = plannedSets,
                    targetRepsPerSet = targetRepsPerSet
                )
            )
        }

        val sessions = mutableListOf<BackupSession>()
        val sessionIds = HashSet<String>()
        for (i in 0 until sessionsArray.length()) {
            val o = sessionsArray.optJSONObject(i) ?: throw BackupException("训练记录格式有误")
            val id = o.optString("id", "")
            if (id.isBlank()) throw BackupException("训练记录缺少 ID")
            if (!sessionIds.add(id)) throw BackupException("训练记录 ID 重复：$id")
            val projectId = o.optString("projectId", "")
            if (projectId !in projectIds) throw BackupException("训练记录引用了不存在的项目")
            val endedAt = o.optLong("endedAt", 0L)
            if (endedAt <= 0L) throw BackupException("训练记录缺少完成时间")
            val mode = if (version >= SCHEMA_VERSION) {
                o.optString("mode", SessionEntity.MODE_TIME).also {
                    if (it != SessionEntity.MODE_TIME && it != SessionEntity.MODE_REPS) {
                        throw BackupException("训练记录模式无效")
                    }
                    if (projectModes[projectId] != it) {
                        throw BackupException("训练记录模式与项目不一致")
                    }
                }
            } else {
                SessionEntity.MODE_TIME
            }
            val plannedSets = if (version >= SCHEMA_VERSION) o.optInt("plannedSets", 3) else 3
            val targetRepsPerSet = if (version >= SCHEMA_VERSION) o.optInt("targetRepsPerSet", 10) else 10
            if (plannedSets !in 1..40) throw BackupException("训练计划组数超出范围")
            if (targetRepsPerSet !in 1..999) throw BackupException("训练每组目标次数超出范围")
            val target = o.optInt("targetSec", -1)
            val actual = o.optInt("actualSec", -1)
            if (mode == SessionEntity.MODE_REPS) {
                if (target !in 0..18_000) throw BackupException("计次训练目标超出范围")
                if (actual !in 0..36_000) throw BackupException("计次训练实际值超出范围")
            } else {
                if (target !in 1..18_000) throw BackupException("训练目标时长超出范围")
                if (actual !in 1..36_000) throw BackupException("训练实际时长超出范围")
            }
            val sets = if (mode == SessionEntity.MODE_REPS) {
                val setsArray = o.optJSONArray("sets")
                    ?: throw BackupException("计次训练缺少每组明细")
                if (setsArray.length() !in 1..40) throw BackupException("计次训练组数超出范围")
                (0 until setsArray.length()).map { idx ->
                    val reps = setsArray.optInt(idx, -1)
                    if (reps !in 1..999) throw BackupException("每组实际次数超出范围")
                    reps
                }
            } else {
                emptyList()
            }
            val note = o.optString("note", "")
            if (note.length > 1000) throw BackupException("训练备注过长")
            val pauseCount = o.optInt("pauseCount", 0)
            if (pauseCount !in 0..10_000) throw BackupException("暂停次数无效")
            val pausedSec = o.optInt("pausedSec", 0)
            if (pausedSec !in 0..31_536_000) throw BackupException("暂停时长无效")
            val source = o.optString("source", SessionEntity.SOURCE_TIMER)
            if (source !in setOf(
                    SessionEntity.SOURCE_TIMER,
                    SessionEntity.SOURCE_MANUAL,
                    SessionEntity.SOURCE_IMPORT,
                    SessionEntity.SOURCE_REPS
                )
            ) {
                throw BackupException("训练记录来源无效")
            }
            sessions.add(
                BackupSession(
                    id = id,
                    projectId = projectId,
                    startedAt = optLongOrNull(o, "startedAt"),
                    endedAt = endedAt,
                    targetSec = target,
                    actualSec = actual,
                    pauseCount = pauseCount,
                    pausedSec = pausedSec,
                    source = source,
                    note = note,
                    createdAt = o.optLong("createdAt", endedAt),
                    updatedAt = o.optLong("updatedAt", endedAt),
                    deletedAt = optLongOrNull(o, "deletedAt"),
                    mode = mode,
                    plannedSets = plannedSets,
                    targetRepsPerSet = targetRepsPerSet,
                    sets = sets
                )
            )
        }

        val draft = if (version >= SCHEMA_VERSION) {
            val d = root.optJSONObject("draft")
            if (d == null) {
                null
            } else {
                val draftProjectId = d.optString("projectId", "")
                if (draftProjectId !in projectIds) throw BackupException("草稿引用了不存在的项目")
                if (projectModes[draftProjectId] != ProjectEntity.MODE_REPS) {
                    throw BackupException("草稿必须属于计次项目")
                }
                val setsArray = d.optJSONArray("sets") ?: throw BackupException("草稿缺少组明细")
                if (setsArray.length() !in 0..40) throw BackupException("草稿组数超出范围")
                val draftSets = (0 until setsArray.length()).map { idx ->
                    val reps = setsArray.optInt(idx, -1)
                    if (reps !in 1..999) throw BackupException("草稿每组次数超出范围")
                    reps
                }
                val planned = d.optInt("plannedSets", -1)
                val targetReps = d.optInt("targetRepsPerSet", -1)
                if (planned !in 1..40) throw BackupException("草稿计划组数超出范围")
                if (targetReps !in 1..999) throw BackupException("草稿目标次数超出范围")
                val nextReps = d.optInt("nextReps", -1)
                if (nextReps !in 1..999) throw BackupException("草稿下一组次数超出范围")
                val touchedAt = d.optLong("touchedAt", 0L)
                if (touchedAt <= 0L) throw BackupException("草稿时间无效")
                BackupDraft(
                    projectId = draftProjectId,
                    plannedSets = planned,
                    targetRepsPerSet = targetReps,
                    sets = draftSets,
                    nextReps = nextReps,
                    touchedAt = touchedAt
                )
            }
        } else {
            null
        }
        val settingsObj = root.optJSONObject("settings")
        val data = BackupData(
            app = app,
            version = version,
            exportedAt = root.optString("exportedAt", ""),
            appVersion = root.optString("appVersion", ""),
            projectId = root.optString("projectId", "").takeIf { it.isNotBlank() && it in projectIds },
            projects = projects,
            sessions = sessions,
            settings = BackupSettings(
                theme = if (settingsObj?.optString("theme") == "dark") "dark" else "light",
                soundEnabled = settingsObj?.optBoolean("soundEnabled", true) ?: true,
                vibrationEnabled = settingsObj?.optBoolean("vibrationEnabled", true) ?: true,
                voiceEnabled = settingsObj?.optBoolean("voiceEnabled", true) ?: true,
                fontScalePercent = (settingsObj?.optInt("fontScalePercent", 115) ?: 115)
                    .coerceIn(100, 145),
                lastRangeDays = settingsObj?.let { s ->
                    if (s.has("lastRangeDays") && !s.isNull("lastRangeDays")) {
                        s.optInt("lastRangeDays", 0).takeIf { it == 7 || it == 30 || it == 90 }
                    } else {
                        null
                    }
                },
                lastChart = settingsObj?.optString("lastChart", "TREND")
                    ?.takeIf { it in setOf("TREND", "WEEKLY", "SCATTER") } ?: "TREND",
                lastProjectFilter = settingsObj?.optString("lastProjectFilter", "current")
                    ?.takeIf { it.isNotBlank() } ?: "current",
                lastTimeProjectId = if (version >= SCHEMA_VERSION) {
                    settingsObj?.optString("lastTimeProjectId", "")
                        ?.takeIf { it.isNotBlank() && it in projectIds }
                } else {
                    null
                },
                lastRepsProjectId = if (version >= SCHEMA_VERSION) {
                    settingsObj?.optString("lastRepsProjectId", "")
                        ?.takeIf { it.isNotBlank() && it in projectIds }
                } else {
                    null
                }
            ),
            checksum = root.optString("checksum", ""),
            draft = draft
        )
        val expected = checksum(data)
        if (data.checksum.isBlank()) throw BackupException("备份缺少校验摘要")
        if (!data.checksum.equals(expected, ignoreCase = true)) {
            throw BackupException("备份校验摘要不匹配，文件可能损坏或被修改")
        }
        return ParsedBackup(data)
    }

    fun preview(
        data: BackupData,
        existingProjectIds: Set<String>,
        existingSessionIds: Set<String>
    ): BackupPreview {
        val conflictProjects = data.projects.count { it.id in existingProjectIds }
        val conflictSessions = data.sessions.count { it.id in existingSessionIds }
        return BackupPreview(
            projectCount = data.projects.size,
            sessionCount = data.sessions.size,
            newProjects = data.projects.size - conflictProjects,
            newSessions = data.sessions.size - conflictSessions,
            conflictProjects = conflictProjects,
            conflictSessions = conflictSessions,
            deletedSessions = data.sessions.count { it.deletedAt != null },
            repsSessions = data.sessions.count { it.mode == SessionEntity.MODE_REPS },
            hasDraft = data.draft != null
        )
    }

    fun merge(
        data: BackupData,
        existingProjectIds: Set<String>,
        existingSessionIds: Set<String>
    ): Pair<List<ProjectEntity>, List<SessionEntity>> {
        val projects = data.projects.filter { it.id !in existingProjectIds }.map { it.toEntity() }
        val sessions = data.sessions.filter { it.id !in existingSessionIds }.map { it.toEntity() }
        return projects to sessions
    }

    /** V1 与 V2 使用各自的原生算法，禁止先补字段再校验旧文件。 */
    fun checksum(data: BackupData): String = when (data.version) {
        LEGACY_SCHEMA_VERSION -> checksumV1(data)
        V2_SCHEMA_VERSION -> checksumV2(data)
        else -> checksumV3(data)
    }

    private fun baseChecksum(data: BackupData): StringBuilder {
        val sb = StringBuilder()
        sb.append(data.app).append('|').append(data.version).append('|')
            .append(data.exportedAt).append('|').append(data.appVersion).append('|')
            .append(data.projectId ?: "").append('\n')
        data.projects.sortedBy { it.id }.forEach { p ->
            sb.append("P\u001f").append(p.id).append('\u001f').append(p.name).append('\u001f')
                .append(p.defaultTargetSec).append('\u001f').append(p.presetsSec.joinToString(","))
                .append('\u001f').append(p.archivedAt ?: "").append('\u001f')
                .append(p.createdAt).append('\u001f').append(p.updatedAt).append('\n')
        }
        data.sessions.sortedBy { it.id }.forEach { s ->
            sb.append("S\u001f").append(s.id).append('\u001f').append(s.projectId).append('\u001f')
                .append(s.startedAt ?: "").append('\u001f').append(s.endedAt).append('\u001f')
                .append(s.targetSec).append('\u001f').append(s.actualSec).append('\u001f')
                .append(s.pauseCount).append('\u001f').append(s.pausedSec).append('\u001f')
                .append(s.source).append('\u001f').append(s.note).append('\u001f')
                .append(s.createdAt).append('\u001f').append(s.updatedAt).append('\u001f')
                .append(s.deletedAt ?: "").append('\n')
        }
        return sb
    }

    private fun checksumV1(data: BackupData): String {
        val sb = baseChecksum(data)
        sb.append("T\u001f").append(data.settings.theme).append('\u001f')
            .append(data.settings.soundEnabled).append('\u001f')
            .append(data.settings.vibrationEnabled)
        return sha256(sb)
    }

    private fun checksumV2(data: BackupData): String {
        val sb = baseChecksum(data)
        sb.append("T\u001f").append(data.settings.theme).append('\u001f')
            .append(data.settings.soundEnabled).append('\u001f')
            .append(data.settings.vibrationEnabled).append('\u001f')
            .append(data.settings.voiceEnabled).append('\u001f')
            .append(data.settings.fontScalePercent).append('\u001f')
            .append(data.settings.lastRangeDays ?: "").append('\u001f')
            .append(data.settings.lastChart).append('\u001f')
            .append(data.settings.lastProjectFilter ?: "")
        return sha256(sb)
    }

    private fun checksumV3(data: BackupData): String {
        val sb = baseChecksum(data)
        data.projects.sortedBy { it.id }.forEach { p ->
            sb.append("PX\u001f").append(p.id).append('\u001f').append(p.mode).append('\u001f')
                .append(p.plannedSets).append('\u001f').append(p.targetRepsPerSet).append('\n')
        }
        data.sessions.sortedBy { it.id }.forEach { s ->
            sb.append("SX\u001f").append(s.id).append('\u001f').append(s.mode).append('\u001f')
                .append(s.plannedSets).append('\u001f').append(s.targetRepsPerSet).append('\u001f')
                .append(s.sets.joinToString(",")).append('\n')
        }
        sb.append("T\u001f").append(data.settings.theme).append('\u001f')
            .append(data.settings.soundEnabled).append('\u001f')
            .append(data.settings.vibrationEnabled).append('\u001f')
            .append(data.settings.voiceEnabled).append('\u001f')
            .append(data.settings.fontScalePercent).append('\u001f')
            .append(data.settings.lastRangeDays ?: "").append('\u001f')
            .append(data.settings.lastChart).append('\u001f')
            .append(data.settings.lastProjectFilter ?: "").append('\u001f')
            .append(data.settings.lastTimeProjectId ?: "").append('\u001f')
            .append(data.settings.lastRepsProjectId ?: "").append('\n')
        data.draft?.let { d ->
            sb.append("D\u001f").append(d.projectId).append('\u001f')
                .append(d.plannedSets).append('\u001f').append(d.targetRepsPerSet).append('\u001f')
                .append(d.sets.joinToString(",")).append('\u001f')
                .append(d.nextReps).append('\u001f').append(d.touchedAt)
        }
        return sha256(sb)
    }

    private fun sha256(sb: StringBuilder): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(sb.toString().toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun optLongOrNull(o: JSONObject, key: String): Long? {
        if (!o.has(key) || o.isNull(key)) return null
        val v = o.optLong(key, Long.MIN_VALUE)
        return if (v == Long.MIN_VALUE) null else v
    }
}
