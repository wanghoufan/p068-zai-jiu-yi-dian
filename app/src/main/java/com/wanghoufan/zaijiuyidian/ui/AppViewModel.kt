package com.wanghoufan.zaijiuyidian.ui

import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.wanghoufan.zaijiuyidian.BuildConfig
import com.wanghoufan.zaijiuyidian.data.backup.BackupCodec
import com.wanghoufan.zaijiuyidian.data.backup.BackupData
import com.wanghoufan.zaijiuyidian.data.backup.BackupException
import com.wanghoufan.zaijiuyidian.data.backup.BackupPreview
import com.wanghoufan.zaijiuyidian.data.backup.BackupRepository
import com.wanghoufan.zaijiuyidian.data.db.ActiveSessionEntity
import com.wanghoufan.zaijiuyidian.data.db.AppDatabase
import com.wanghoufan.zaijiuyidian.data.db.ProjectEntity
import com.wanghoufan.zaijiuyidian.data.db.SessionEntity
import com.wanghoufan.zaijiuyidian.data.prefs.SettingsRepository
import com.wanghoufan.zaijiuyidian.domain.AppSettings
import com.wanghoufan.zaijiuyidian.domain.ChartMode
import com.wanghoufan.zaijiuyidian.domain.DefaultSettings
import com.wanghoufan.zaijiuyidian.domain.Project
import com.wanghoufan.zaijiuyidian.domain.TimerMath
import com.wanghoufan.zaijiuyidian.domain.ThemeMode
import com.wanghoufan.zaijiuyidian.domain.TrainingSession
import com.wanghoufan.zaijiuyidian.domain.toModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

enum class Tab { TRAIN, HISTORY, SETTINGS }

sealed interface Sheet {
    data object ProjectPicker : Sheet
    data class ProjectEdit(val isNew: Boolean) : Sheet
    data object GoalEditor : Sheet
    data object PresetEditor : Sheet
    data object FontScalePicker : Sheet
    data class FinishConfirm(
        val actualSec: Int,
        val targetSec: Int,
        val pauseCount: Int,
        val pausedSec: Int,
        val endedAtWall: Long,
        val wasPaused: Boolean
    ) : Sheet
    data class EntryEditor(val sessionId: String?) : Sheet
    data class DeleteConfirm(val sessionId: String) : Sheet
    data object ImportPreview : Sheet
    data object ImportReplaceConfirm : Sheet
    data object SortInfo : Sheet
    data class ArchiveConfirm(val projectId: String) : Sheet
    data class DeleteProjectConfirm(val projectId: String) : Sheet
}

data class ToastMsg(val id: Long, val text: String, val actionLabel: String? = null)

data class EntryDraft(
    val id: String?,
    val projectId: String,
    val endedAtMs: Long,
    val targetSec: Int,
    val actualSec: Int,
    val note: String
)

private const val INTERVAL_MS = 180_000L

enum class VoiceKind { START, INTERVAL, TARGET }

data class VoiceCue(val id: Long, val kind: VoiceKind, val minute: Int = 0)

data class UiState(
    val projects: List<Project> = emptyList(),
    val sessions: List<TrainingSession> = emptyList(),
    val deletedSessions: List<TrainingSession> = emptyList(),
    val settings: AppSettings = DefaultSettings,
    val active: ActiveSessionEntity? = null,
    val elapsedMs: Long = 0,
    val tab: Tab = Tab.TRAIN,
    val nowMs: Long = System.currentTimeMillis(),
    val sheet: Sheet? = null,
    val importData: BackupData? = null,
    val importPreview: BackupPreview? = null,
    val toast: ToastMsg? = null,
    val interruptedPrompt: ActiveSessionEntity? = null,
    val pendingDeleteId: String? = null,
    val searchQuery: String = "",
    val excludePaused: Boolean = false,
    val trendCount: Int = 10,
    val voiceCue: VoiceCue? = null,
    val busy: Boolean = false,
    val dataRevision: Long = 0
) {
    val selectedProject: Project?
        get() = projects.firstOrNull { it.id == settings.selectedProjectId }
            ?: projects.firstOrNull { !it.isArchived }

    val activeProject: Project?
        get() = active?.let { a -> projects.firstOrNull { it.id == a.projectId } }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val settingsRepo = SettingsRepository(app)
    private val backupRepo = BackupRepository(app, db)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var lastCheckpointWall = 0L
    private var ticker: Job? = null
    private var toastSeq = 0L
    private var voiceSeq = 0L
    private var lastIntervalMark = 0
    private var intervalSessionStartWall: Long? = null

    private fun emitVoice(kind: VoiceKind, minute: Int = 0) {
        voiceSeq += 1
        val cue = VoiceCue(voiceSeq, kind, minute)
        _state.update { it.copy(voiceCue = cue) }
    }

    private val bootCount: Int by lazy {
        try {
            Settings.Global.getInt(
                getApplication<Application>().contentResolver,
                Settings.Global.BOOT_COUNT,
                -1
            )
        } catch (e: Exception) {
            -1
        }
    }

    init {
        viewModelScope.launch {
            db.projectDao().observeAll().collect { entities ->
                val projects = entities.map { it.toModel() }
                _state.update { it.copy(projects = projects) }
                reconcileProjectSelection()
                ensureSeedProject(projects)
            }
        }
        viewModelScope.launch {
            db.sessionDao().observeAll().collect { entities ->
                val models = entities.map { it.toModel() }
                _state.update {
                    it.copy(
                        sessions = models.filter { session -> session.deletedAt == null },
                        deletedSessions = models.filter { session -> session.deletedAt != null }
                            .sortedByDescending { session -> session.deletedAt }
                    )
                }
            }
        }
        viewModelScope.launch {
            settingsRepo.settings.collect { settings ->
                _state.update { it.copy(settings = settings) }
                reconcileProjectSelection()
            }
        }
        viewModelScope.launch {
            db.activeSessionDao().observe().collect { active ->
                lastCheckpointWall = System.currentTimeMillis()
                if (active == null) {
                    intervalSessionStartWall = null
                    lastIntervalMark = 0
                } else if (active.startedAtWall != intervalSessionStartWall) {
                    intervalSessionStartWall = active.startedAtWall
                    lastIntervalMark = TimerMath.intervalMark(
                        TimerMath.elapsedMs(active, SystemClock.elapsedRealtime()),
                        INTERVAL_MS
                    )
                }
                _state.update { it.copy(active = active) }
                if (active != null && !active.interrupted) {
                    val nowW = System.currentTimeMillis()
                    val nowE = SystemClock.elapsedRealtime()
                    if (TimerMath.isRebooted(active, nowW, nowE, bootCount)) {
                        val interrupted = TimerMath.markInterrupted(active)
                        db.activeSessionDao().upsert(interrupted)
                        _state.update {
                            it.copy(
                                active = interrupted,
                                interruptedPrompt = interrupted
                            )
                        }
                    }
                }
            }
        }
        startTicker()
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                val active = _state.value.active
                val nowElapsed = SystemClock.elapsedRealtime()
                val nowWall = System.currentTimeMillis()
                if (active != null && !active.interrupted) {
                    val elapsed = TimerMath.elapsedMs(active, nowElapsed)
                    _state.update { it.copy(elapsedMs = elapsed, nowMs = nowWall) }
                    if (!TimerMath.isPaused(active)) {
                        val mark = TimerMath.intervalMark(elapsed, INTERVAL_MS)
                        if (mark > lastIntervalMark) {
                            lastIntervalMark = mark
                            if (mark >= 1) emitVoice(VoiceKind.INTERVAL, mark)
                        }
                    }
                    if (nowWall - lastCheckpointWall >= 5000) {
                        lastCheckpointWall = nowWall
                        val checkpoint = TimerMath.checkpoint(active, nowElapsed, nowWall)
                        withContext(Dispatchers.IO) { db.activeSessionDao().upsert(checkpoint) }
                    }
                    if (elapsed >= active.targetSec * 1000L && !active.notifiedAtTarget) {
                        val notified = active.copy(notifiedAtTarget = true)
                        withContext(Dispatchers.IO) { db.activeSessionDao().upsert(notified) }
                        emitVoice(VoiceKind.TARGET)
                    }
                } else if (active == null) {
                    _state.update { it.copy(elapsedMs = 0, nowMs = nowWall) }
                } else {
                    _state.update { it.copy(elapsedMs = active.accumulatedMs, nowMs = nowWall) }
                }
                delay(500)
            }
        }
    }

    private var seeding = false

    private fun ensureSeedProject(projects: List<Project>) {
        if (projects.isNotEmpty() || seeding) return
        seeding = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (db.projectDao().getAll().isNotEmpty()) return@launch
                val now = System.currentTimeMillis()
                val project = ProjectEntity(
                    id = UUID.randomUUID().toString(),
                    name = "练习 A",
                    defaultTargetSec = 25 * 60,
                    presetsSec = "900,1200,1500",
                    createdAt = now,
                    updatedAt = now
                )
                db.projectDao().upsert(project)
                settingsRepo.setSelectedProject(project.id)
            } finally {
                seeding = false
            }
        }
    }

    private fun reconcileProjectSelection() {
        val s = _state.value
        if (s.projects.isEmpty()) return
        val selected = s.settings.selectedProjectId
        val match = s.projects.firstOrNull { it.id == selected }
        if (match == null || match.isArchived) {
            val fallback = s.projects.firstOrNull { !it.isArchived } ?: s.projects.first()
            if (fallback.id != selected) {
                viewModelScope.launch(Dispatchers.IO) { settingsRepo.setSelectedProject(fallback.id) }
            }
        }
    }

    fun selectTab(tab: Tab) {
        _state.update { it.copy(tab = tab) }
    }

    fun showToast(text: String, actionLabel: String? = null) {
        toastSeq += 1
        _state.update { it.copy(toast = ToastMsg(toastSeq, text, actionLabel)) }
    }

    fun clearToast(id: Long) {
        _state.update { if (it.toast?.id == id) it.copy(toast = null) else it }
    }

    fun selectProject(id: String) {
        val project = _state.value.projects.firstOrNull { it.id == id } ?: return
        if (project.isArchived) {
            showToast("已归档的项目不能用于训练")
            return
        }
        if (_state.value.active != null) {
            showToast("请先结束当前训练")
            return
        }
        viewModelScope.launch(Dispatchers.IO) { settingsRepo.setSelectedProject(id) }
        _state.update { it.copy(settings = it.settings.copy(selectedProjectId = id), sheet = null) }
    }

    fun setTargetMinutes(minutes: Double) {
        val project = _state.value.selectedProject ?: return
        if (_state.value.active != null) {
            showToast("训练进行中，目标在下一次训练生效")
            return
        }
        if (minutes < 1 || minutes > 300) {
            showToast("请输入 1～300 分钟")
            return
        }
        val targetSec = Math.round(minutes * 60).toInt().coerceIn(60, 18_000)
        viewModelScope.launch(Dispatchers.IO) {
            db.projectDao().update(
                ProjectEntity(
                    id = project.id,
                    name = project.name,
                    defaultTargetSec = targetSec,
                    presetsSec = project.presetsSec.joinToString(","),
                    archivedAt = project.archivedAt,
                    createdAt = project.createdAt,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        _state.update { it.copy(sheet = null) }
        showToast("目标已保存为 ${com.wanghoufan.zaijiuyidian.domain.Formatters.minutes(targetSec)} 分钟")
    }

    fun setPresets(minutes: List<Double>) {
        val project = _state.value.selectedProject ?: return
        if (minutes.isEmpty() || minutes.size > 8 || minutes.any { it < 1 || it > 300 }) {
            showToast("请设置 1～8 个有效档位")
            return
        }
        val presets = minutes.map { Math.round(it * 60).toInt() }.distinct().sorted()
        viewModelScope.launch(Dispatchers.IO) {
            db.projectDao().update(
                ProjectEntity(
                    id = project.id,
                    name = project.name,
                    defaultTargetSec = project.defaultTargetSec,
                    presetsSec = presets.joinToString(","),
                    archivedAt = project.archivedAt,
                    createdAt = project.createdAt,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        _state.update { it.copy(sheet = null) }
        showToast("预设档位已更新")
    }

    fun createProject(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            showToast("请输入项目名称")
            return
        }
        if (trimmed.length > 24) {
            showToast("项目名称最多 24 个字")
            return
        }
        if (_state.value.projects.any { it.name.equals(trimmed, ignoreCase = true) }) {
            showToast("这个项目名称已经存在")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val entity = ProjectEntity(
                id = UUID.randomUUID().toString(),
                name = trimmed,
                defaultTargetSec = 15 * 60,
                presetsSec = "600,900,1200,1500",
                createdAt = now,
                updatedAt = now
            )
            db.projectDao().upsert(entity)
            settingsRepo.setSelectedProject(entity.id)
        }
        _state.update {
            it.copy(sheet = null, settings = it.settings.copy(lastProjectFilter = "current"))
        }
        showToast("项目已创建")
    }

    fun renameProject(name: String) {
        val project = _state.value.selectedProject ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            showToast("请输入项目名称")
            return
        }
        if (trimmed.length > 24) {
            showToast("项目名称最多 24 个字")
            return
        }
        if (_state.value.projects.any { it.id != project.id && it.name.equals(trimmed, ignoreCase = true) }) {
            showToast("这个项目名称已经存在")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            db.projectDao().update(
                ProjectEntity(
                    id = project.id,
                    name = trimmed,
                    defaultTargetSec = project.defaultTargetSec,
                    presetsSec = project.presetsSec.joinToString(","),
                    archivedAt = project.archivedAt,
                    createdAt = project.createdAt,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        _state.update { it.copy(sheet = null) }
        showToast("项目已保存")
    }

    fun archiveProject(projectId: String) {
        val project = _state.value.projects.firstOrNull { it.id == projectId } ?: return
        if (_state.value.active?.projectId == projectId) {
            showToast("训练进行中，不能归档当前项目")
            return
        }
        if (_state.value.projects.count { !it.isArchived } <= 1) {
            showToast("至少保留一个可用项目")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            db.projectDao().update(
                ProjectEntity(
                    id = project.id,
                    name = project.name,
                    defaultTargetSec = project.defaultTargetSec,
                    presetsSec = project.presetsSec.joinToString(","),
                    archivedAt = System.currentTimeMillis(),
                    createdAt = project.createdAt,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        _state.update { it.copy(sheet = null) }
        showToast("项目已归档，历史记录仍会保留")
    }

    fun restoreProject(projectId: String) {
        val project = _state.value.projects.firstOrNull { it.id == projectId } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            db.projectDao().update(
                ProjectEntity(
                    id = project.id,
                    name = project.name,
                    defaultTargetSec = project.defaultTargetSec,
                    presetsSec = project.presetsSec.joinToString(","),
                    archivedAt = null,
                    createdAt = project.createdAt,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        _state.update { it.copy(sheet = null) }
        showToast("项目已恢复")
    }

    fun requestDeleteProject(projectId: String) {
        val project = _state.value.projects.firstOrNull { it.id == projectId } ?: return
        if (_state.value.active?.projectId == projectId) {
            showToast("训练进行中，不能删除当前项目")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val count = db.projectDao().sessionCount(projectId)
            withContext(Dispatchers.Main) {
                if (count > 0) {
                    _state.update { it.copy(sheet = Sheet.ArchiveConfirm(projectId)) }
                } else {
                    _state.update { it.copy(sheet = Sheet.DeleteProjectConfirm(projectId)) }
                }
            }
        }
    }

    fun deleteProject(projectId: String) {
        val state = _state.value
        if (state.projects.count { !it.isArchived } <= 1) {
            showToast("至少保留一个可用项目")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            db.projectDao().deleteById(projectId)
            withContext(Dispatchers.Main) {
                _state.update { it.copy(sheet = null) }
                showToast("项目已删除")
            }
        }
    }

    fun startTraining() {
        val s = _state.value
        if (s.active != null) {
            showToast("已有正在进行的训练")
            return
        }
        val project = s.selectedProject ?: return
        if (project.isArchived) {
            showToast("请先恢复或新建一个可用项目")
            return
        }
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val session = ActiveSessionEntity(
            projectId = project.id,
            targetSec = project.defaultTargetSec,
            accumulatedMs = 0,
            segmentStartedElapsedMs = nowElapsed,
            pausedAtElapsedMs = null,
            pauseCount = 0,
            pausedMs = 0,
            bootWallBase = TimerMath.bootWallBase(nowWall, nowElapsed),
            bootCount = bootCount,
            startedAtWall = nowWall,
            notifiedAtTarget = false,
            lastKnownElapsedMs = 0,
            lastKnownAtWall = nowWall,
            interrupted = false
        )
        lastCheckpointWall = nowWall
        lastIntervalMark = 0
        intervalSessionStartWall = nowWall
        viewModelScope.launch(Dispatchers.IO) { db.activeSessionDao().upsert(session) }
        _state.update { it.copy(active = session, elapsedMs = 0, interruptedPrompt = null) }
        emitVoice(VoiceKind.START)
        showToast("已开始计时")
    }

    fun pauseTraining() {
        val active = _state.value.active ?: return
        if (active.interrupted) return
        if (TimerMath.isPaused(active)) return
        val nowElapsed = SystemClock.elapsedRealtime()
        val paused = TimerMath.pause(active, nowElapsed)
        viewModelScope.launch(Dispatchers.IO) { db.activeSessionDao().upsert(paused) }
        _state.update { it.copy(active = paused, elapsedMs = paused.accumulatedMs) }
    }

    fun resumeTraining() {
        val active = _state.value.active ?: return
        if (active.interrupted) return
        if (!TimerMath.isPaused(active)) return
        val nowElapsed = SystemClock.elapsedRealtime()
        val resumed = TimerMath.resume(active, nowElapsed)
        lastCheckpointWall = System.currentTimeMillis()
        viewModelScope.launch(Dispatchers.IO) { db.activeSessionDao().upsert(resumed) }
        _state.update { it.copy(active = resumed) }
    }

    fun togglePause() {
        val active = _state.value.active ?: return
        if (TimerMath.isPaused(active)) resumeTraining() else pauseTraining()
    }

    fun requestFinish() {
        val active = _state.value.active ?: return
        if (active.interrupted) {
            _state.update { it.copy(interruptedPrompt = active) }
            return
        }
        val nowElapsed = SystemClock.elapsedRealtime()
        val snapshot = TimerMath.finish(active, nowElapsed, System.currentTimeMillis())
        _state.update {
            it.copy(
                sheet = Sheet.FinishConfirm(
                    actualSec = snapshot.actualSec,
                    targetSec = active.targetSec,
                    pauseCount = snapshot.pauseCount,
                    pausedSec = snapshot.pausedSec,
                    endedAtWall = snapshot.endedAtWall,
                    wasPaused = TimerMath.isPaused(active)
                )
            )
        }
    }

    fun saveFinish(note: String) {
        val active = _state.value.active ?: return
        val sheet = _state.value.sheet as? Sheet.FinishConfirm ?: return
        if (sheet.actualSec <= 0) {
            showToast("计时时长不足 1 秒，不能保存")
            return
        }
        val project = _state.value.projects.firstOrNull { it.id == active.projectId }
            ?: run {
                showToast("项目不存在，无法保存")
                return
            }
        val now = System.currentTimeMillis()
        val session = SessionEntity(
            id = UUID.randomUUID().toString(),
            projectId = project.id,
            startedAt = active.startedAtWall,
            endedAt = sheet.endedAtWall,
            targetSec = active.targetSec,
            actualSec = sheet.actualSec,
            pauseCount = sheet.pauseCount,
            pausedSec = sheet.pausedSec,
            source = SessionEntity.SOURCE_TIMER,
            note = note.trim().take(300),
            createdAt = now,
            updatedAt = now,
            deletedAt = null
        )
        viewModelScope.launch(Dispatchers.IO) {
            db.withTransactionSave(session, clearActive = true)
            withContext(Dispatchers.Main) {
                _state.update {
                    it.copy(
                        sheet = null,
                        active = null,
                        elapsedMs = 0,
                        tab = Tab.HISTORY,
                        settings = it.settings.copy(lastProjectFilter = "current")
                    )
                }
                showToast("训练已计入历史")
            }
        }
    }

    fun discardFinish() {
        val active = _state.value.active ?: return
        viewModelScope.launch(Dispatchers.IO) {
            db.activeSessionDao().delete()
            withContext(Dispatchers.Main) {
                _state.update { it.copy(sheet = null, active = null, elapsedMs = 0) }
                showToast("本次未计入历史")
            }
        }
    }

    fun resolveInterrupted(save: Boolean) {
        val active = _state.value.interruptedPrompt ?: _state.value.active ?: return
        val actualSec = (active.lastKnownElapsedMs / 1000).toInt()
        if (!save || actualSec <= 0) {
            viewModelScope.launch(Dispatchers.IO) {
                db.activeSessionDao().delete()
                withContext(Dispatchers.Main) {
                    _state.update { it.copy(interruptedPrompt = null, active = null) }
                    showToast("已放弃这次中断的训练")
                }
            }
            return
        }
        val now = System.currentTimeMillis()
        val session = SessionEntity(
            id = UUID.randomUUID().toString(),
            projectId = active.projectId,
            startedAt = active.startedAtWall,
            endedAt = active.lastKnownAtWall.takeIf { it > 0 } ?: now,
            targetSec = active.targetSec,
            actualSec = actualSec,
            pauseCount = active.pauseCount,
            pausedSec = (active.pausedMs / 1000).toInt(),
            source = SessionEntity.SOURCE_TIMER,
            note = "设备重启后按最后可靠记录保存",
            createdAt = now,
            updatedAt = now
        )
        viewModelScope.launch(Dispatchers.IO) {
            db.withTransactionSave(session, clearActive = true)
            withContext(Dispatchers.Main) {
                _state.update {
                    it.copy(
                        interruptedPrompt = null,
                        active = null,
                        tab = Tab.HISTORY
                    )
                }
                showToast("已按最后可靠记录保存")
            }
        }
    }

    fun openSheet(sheet: Sheet) {
        _state.update { it.copy(sheet = sheet) }
    }

    fun dismissSheet() {
        _state.update { it.copy(sheet = null) }
    }

    fun saveEntry(draft: EntryDraft) {
        if (draft.projectId.isBlank() || _state.value.projects.none { it.id == draft.projectId }) {
            showToast("请选择所属项目")
            return
        }
        if (draft.actualSec < 1) {
            showToast("实际时长至少 1 秒")
            return
        }
        if (draft.targetSec < 1) {
            showToast("目标时长至少 1 秒")
            return
        }
        if (draft.endedAtMs <= 0 || draft.endedAtMs > System.currentTimeMillis() + 86_400_000L) {
            showToast("请选择正确的完成时间")
            return
        }
        val existing = draft.id?.let { id -> _state.value.sessions.firstOrNull { it.id == id } }
        val now = System.currentTimeMillis()
        val session = SessionEntity(
            id = draft.id ?: UUID.randomUUID().toString(),
            projectId = draft.projectId,
            startedAt = existing?.startedAt ?: (draft.endedAtMs - draft.actualSec * 1000L),
            endedAt = draft.endedAtMs,
            targetSec = draft.targetSec,
            actualSec = draft.actualSec,
            pauseCount = existing?.pauseCount ?: 0,
            pausedSec = existing?.pausedSec ?: 0,
            source = SessionEntity.SOURCE_MANUAL,
            note = draft.note.trim().take(300),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            deletedAt = null
        )
        viewModelScope.launch(Dispatchers.IO) {
            db.sessionDao().upsert(session)
            withContext(Dispatchers.Main) {
                _state.update { it.copy(sheet = null) }
                showToast(if (existing == null) "已补录记录" else "记录已更新")
            }
        }
    }

    fun requestDeleteSession(id: String) {
        _state.update { it.copy(sheet = Sheet.DeleteConfirm(id)) }
    }

    fun confirmDeleteSession() {
        val sheet = _state.value.sheet as? Sheet.DeleteConfirm ?: return
        val now = System.currentTimeMillis()
        viewModelScope.launch(Dispatchers.IO) {
            db.sessionDao().softDelete(sheet.sessionId, now)
            withContext(Dispatchers.Main) {
                _state.update {
                    it.copy(
                        sheet = null,
                        pendingDeleteId = sheet.sessionId
                    )
                }
                showToast("记录已删除", actionLabel = "撤销")
            }
        }
    }

    fun undoDelete() {
        val id = _state.value.pendingDeleteId ?: return
        restoreSession(id)
    }

    fun restoreSession(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.sessionDao().restore(id, System.currentTimeMillis())
            withContext(Dispatchers.Main) {
                _state.update { it.copy(pendingDeleteId = null, toast = null) }
                showToast("已恢复记录")
            }
        }
    }

    fun setSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun setExcludePaused(exclude: Boolean) {
        _state.update { it.copy(excludePaused = exclude) }
    }

    fun setTrendCount(count: Int) {
        _state.update { it.copy(trendCount = count) }
    }

    fun setProjectFilter(filter: String) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepo.setProjectFilter(filter) }
        _state.update { it.copy(settings = it.settings.copy(lastProjectFilter = filter)) }
    }

    fun setRangeDays(days: Int?) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepo.setRangeDays(days) }
        _state.update { it.copy(settings = it.settings.copy(lastRangeDays = days)) }
    }

    fun setChartMode(mode: ChartMode) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepo.setChart(mode) }
        _state.update { it.copy(settings = it.settings.copy(lastChart = mode)) }
    }

    fun toggleTheme() {
        val next = if (_state.value.settings.theme == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK
        viewModelScope.launch(Dispatchers.IO) { settingsRepo.setTheme(next) }
    }

    fun toggleSound() {
        val next = !_state.value.settings.soundEnabled
        viewModelScope.launch(Dispatchers.IO) { settingsRepo.setSound(next) }
    }

    fun toggleVibration() {
        val next = !_state.value.settings.vibrationEnabled
        viewModelScope.launch(Dispatchers.IO) { settingsRepo.setVibration(next) }
    }

    fun toggleVoice() {
        val next = !_state.value.settings.voiceEnabled
        viewModelScope.launch(Dispatchers.IO) { settingsRepo.setVoice(next) }
    }

    fun setFontScalePercent(percent: Int) {
        val clamped = com.wanghoufan.zaijiuyidian.domain.FontScaleOptions.clamp(percent)
        viewModelScope.launch(Dispatchers.IO) { settingsRepo.setFontScalePercent(clamped) }
        _state.update { it.copy(settings = it.settings.copy(fontScalePercent = clamped)) }
    }

    fun onBackground() {
        val active = _state.value.active ?: return
        if (active.interrupted) return
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()
        val checkpoint = TimerMath.checkpoint(active, nowElapsed, nowWall)
        viewModelScope.launch(Dispatchers.IO) { db.activeSessionDao().upsert(checkpoint) }
    }

    fun onForeground() {
        val active = _state.value.active ?: return
        if (active.interrupted) return
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        if (TimerMath.isRebooted(active, nowWall, nowElapsed, bootCount)) {
            val interrupted = TimerMath.markInterrupted(active)
            viewModelScope.launch(Dispatchers.IO) {
                db.activeSessionDao().upsert(interrupted)
                withContext(Dispatchers.Main) {
                    _state.update { it.copy(active = interrupted, interruptedPrompt = interrupted) }
                }
            }
        }
    }

    suspend fun buildBackupJson(): String = withContext(Dispatchers.IO) {
        backupRepo.exportJson(BuildConfig.VERSION_NAME, _state.value.settings)
    }

    suspend fun buildCsv(): String = withContext(Dispatchers.IO) { backupRepo.exportCsv() }

    fun handleImportText(raw: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_state.value.active != null) {
                withContext(Dispatchers.Main) { showToast("请先结束当前训练再导入") }
                return@launch
            }
            try {
                val parsed = BackupCodec.parse(raw)
                val preview = backupRepo.preview(parsed.data)
                withContext(Dispatchers.Main) {
                    _state.update {
                        it.copy(
                            importData = parsed.data,
                            importPreview = preview,
                            sheet = Sheet.ImportPreview
                        )
                    }
                }
            } catch (e: BackupException) {
                withContext(Dispatchers.Main) { showToast("无法导入：${e.message}") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { showToast("无法导入：文件读取失败") }
            }
        }
    }

    fun importMerge() {
        val data = _state.value.importData ?: return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val counts = backupRepo.merge(data)
                withContext(Dispatchers.Main) {
                    _state.update {
                        it.copy(
                            busy = false,
                            sheet = null,
                            importData = null,
                            importPreview = null,
                            tab = Tab.HISTORY
                        )
                    }
                    showToast("合并完成：新增 ${counts.sessionsAdded} 条记录，${counts.projectsAdded} 个项目；冲突 ${counts.conflicts} 条已保留现有版本")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _state.update { it.copy(busy = false) }
                    showToast("导入失败，现有数据未改变")
                }
            }
        }
    }

    fun importReplace() {
        val data = _state.value.importData ?: return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val counts = backupRepo.replace(data, _state.value.settings)
                val settings = data.settings
                settingsRepo.setTheme(if (settings.theme == "dark") ThemeMode.DARK else ThemeMode.LIGHT)
                settingsRepo.setSound(settings.soundEnabled)
                settingsRepo.setVibration(settings.vibrationEnabled)
                data.projectId?.let { settingsRepo.setSelectedProject(it) }
                withContext(Dispatchers.Main) {
                    _state.update {
                        it.copy(
                            busy = false,
                            sheet = null,
                            importData = null,
                            importPreview = null,
                            tab = Tab.HISTORY,
                            settings = it.settings.copy(
                                selectedProjectId = data.projectId ?: it.settings.selectedProjectId,
                                theme = if (settings.theme == "dark") ThemeMode.DARK else ThemeMode.LIGHT,
                                soundEnabled = settings.soundEnabled,
                                vibrationEnabled = settings.vibrationEnabled,
                                lastProjectFilter = "current"
                            )
                        )
                    }
                    showToast("备份恢复成功：${counts.sessionsAdded} 条记录、${counts.projectsAdded} 个项目")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _state.update { it.copy(busy = false) }
                    showToast("恢复失败，现有数据已回滚")
                }
            }
        }
    }

    fun clearImport() {
        _state.update {
            it.copy(importData = null, importPreview = null, sheet = null)
        }
    }

    fun entryDraftFor(sessionId: String?): EntryDraft {
        val session = sessionId?.let { id -> _state.value.sessions.firstOrNull { it.id == id } }
        val project = _state.value.selectedProject
        return if (session != null) {
            EntryDraft(
                id = session.id,
                projectId = session.projectId,
                endedAtMs = session.endedAt,
                targetSec = session.targetSec,
                actualSec = session.actualSec,
                note = session.note
            )
        } else {
            EntryDraft(
                id = null,
                projectId = project?.id.orEmpty(),
                endedAtMs = System.currentTimeMillis(),
                targetSec = project?.defaultTargetSec ?: 600,
                actualSec = 600,
                note = ""
            )
        }
    }

    fun localDateOf(epochMs: Long): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault())

    fun epochOf(dateTime: LocalDateTime): Long =
        dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

private suspend fun AppDatabase.withTransactionSave(session: SessionEntity, clearActive: Boolean) {
    withTransaction {
        sessionDao().upsert(session)
        if (clearActive) activeSessionDao().delete()
    }
}
