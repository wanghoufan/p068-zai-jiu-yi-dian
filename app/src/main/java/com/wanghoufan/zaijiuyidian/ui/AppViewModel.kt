package com.wanghoufan.zaijiuyidian.ui

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.InvalidationTracker
import androidx.room.withTransaction
import com.wanghoufan.zaijiuyidian.BuildConfig
import com.wanghoufan.zaijiuyidian.data.backup.BackupCodec
import com.wanghoufan.zaijiuyidian.data.backup.BackupData
import com.wanghoufan.zaijiuyidian.data.backup.BackupException
import com.wanghoufan.zaijiuyidian.data.backup.BackupPreview
import com.wanghoufan.zaijiuyidian.data.backup.AutoBackupManager
import com.wanghoufan.zaijiuyidian.data.backup.BackupEntry
import com.wanghoufan.zaijiuyidian.data.backup.BackupRepository
import com.wanghoufan.zaijiuyidian.audio.TrainingAlerts
import com.wanghoufan.zaijiuyidian.audio.TrainingServiceController
import com.wanghoufan.zaijiuyidian.audio.TrainingServiceState
import com.wanghoufan.zaijiuyidian.data.db.ActiveSessionEntity
import com.wanghoufan.zaijiuyidian.data.db.AppDatabase
import com.wanghoufan.zaijiuyidian.data.db.ProjectEntity
import com.wanghoufan.zaijiuyidian.data.db.RepDraftEntity
import com.wanghoufan.zaijiuyidian.data.db.RepSetEntity
import com.wanghoufan.zaijiuyidian.data.db.SessionEntity
import com.wanghoufan.zaijiuyidian.data.db.SyncOutboxEntity
import com.wanghoufan.zaijiuyidian.data.prefs.SettingsRepository
import com.wanghoufan.zaijiuyidian.data.sync.SyncManager
import com.wanghoufan.zaijiuyidian.data.sync.SyncConflictItem
import com.wanghoufan.zaijiuyidian.data.sync.SyncPhase
import com.wanghoufan.zaijiuyidian.data.sync.SyncStatus
import com.wanghoufan.zaijiuyidian.domain.AppSettings
import com.wanghoufan.zaijiuyidian.domain.ChartMode
import com.wanghoufan.zaijiuyidian.domain.DefaultSettings
import com.wanghoufan.zaijiuyidian.domain.Project
import com.wanghoufan.zaijiuyidian.domain.TimerMath
import com.wanghoufan.zaijiuyidian.domain.ThemeMode
import com.wanghoufan.zaijiuyidian.domain.TrainingMode
import com.wanghoufan.zaijiuyidian.domain.TrainingSession
import com.wanghoufan.zaijiuyidian.domain.toModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

enum class Tab {
    TIME,
    REPS,
    HISTORY,
    SETTINGS;

    val trainMode: TrainingMode?
        get() = when (this) {
            TIME -> TrainingMode.TIME
            REPS -> TrainingMode.REPS
            else -> null
        }
}

sealed interface Sheet {
    data object ProjectPicker : Sheet
    data class ProjectEdit(val isNew: Boolean, val mode: TrainingMode? = null) : Sheet
    data object GoalEditor : Sheet
    data object PresetEditor : Sheet
    data object RepsDefaultsEditor : Sheet
    data object RepsPlanEditor : Sheet
    data class RepSetEditor(val index: Int) : Sheet
    data object FinishReps : Sheet
    data object DiscardRepDraft : Sheet
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
    data object BackupManager : Sheet
    data class BackupRestorePreview(val entry: BackupEntry) : Sheet
    data object SortInfo : Sheet
    data class ArchiveConfirm(val projectId: String) : Sheet
    data object SyncInfo : Sheet
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

data class RepEntryDraft(
    val id: String?,
    val projectId: String,
    val endedAtMs: Long,
    val plannedSets: Int,
    val targetRepsPerSet: Int,
    val sets: List<Int>,
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
    val tab: Tab = Tab.TIME,
    val nowMs: Long = System.currentTimeMillis(),
    val sheet: Sheet? = null,
    val importData: BackupData? = null,
    val importPreview: BackupPreview? = null,
    val backupEntries: List<BackupEntry> = emptyList(),
    val backupEntriesLoading: Boolean = false,
    val toast: ToastMsg? = null,
    val interruptedPrompt: ActiveSessionEntity? = null,
    val pendingDeleteId: String? = null,
    val searchQuery: String = "",
    val excludePaused: Boolean = false,
    val trendCount: Int = 10,
    val voiceCue: VoiceCue? = null,
    val serviceActive: Boolean = false,
    val busy: Boolean = false,
    val dataRevision: Long = 0,
    val repDraft: RepDraftEntity? = null,
    val sync: SyncStatus = SyncStatus()
) {
    val selectedProject: Project?
        get() {
            val mode = tab.trainMode
            val live = projects.filter { !it.isArchived }
            if (mode != null) {
                return live.firstOrNull { it.id == settings.selectedProjectId && it.mode == mode }
                    ?: live.firstOrNull { it.mode == mode }
            }
            return live.firstOrNull { it.id == settings.selectedProjectId } ?: live.firstOrNull()
        }

    val selectedTimeProject: Project?
        get() = projects.firstOrNull { it.id == settings.selectedProjectId && it.mode == TrainingMode.TIME }
            ?: projects.firstOrNull { !it.isArchived && it.mode == TrainingMode.TIME }

    val selectedRepsProject: Project?
        get() = projects.firstOrNull { it.id == settings.lastRepsProjectId && !it.isArchived && it.mode == TrainingMode.REPS }
            ?: projects.firstOrNull { !it.isArchived && it.mode == TrainingMode.REPS }

    val activeProject: Project?
        get() = active?.let { a -> projects.firstOrNull { it.id == a.projectId } }

    val draftProject: Project?
        get() = repDraft?.let { d -> projects.firstOrNull { it.id == d.projectId } }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val settingsRepo = SettingsRepository(app)
    private val backupRepo = BackupRepository(app, db)
    private val autoBackup = AutoBackupManager(app, db, backupRepo, settingsRepo)
    val syncManager = SyncManager(app, db)
    private val appContext: Context = app.applicationContext

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var lastCheckpointWall = 0L
    private var ticker: Job? = null
    private var toastSeq = 0L
    private var voiceSeq = 0L
    private var lastIntervalMark = 0
    private var intervalSessionStartWall: Long? = null
    @Volatile private var backupArmed = false
    private val dbObserver = object : InvalidationTracker.Observer(
        "projects", "sessions", "rep_sets", "rep_draft"
    ) {
        override fun onInvalidated(tables: Set<String>) {
            if (backupArmed) autoBackup.requestBackup()
        }
    }

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
            combine(
                db.sessionDao().observeAll(),
                db.repSetDao().observeAll()
            ) { entities, setRows -> entities to setRows }
                .collect { (entities, setRows) ->
                    val setsBySession = setRows.groupBy { it.sessionId }
                        .mapValues { (_, rows) -> rows.sortedBy { it.ordinal }.map { it.actualReps } }
                    val models = entities.map { it.toModel(setsBySession[it.id].orEmpty()) }
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
            db.repDraftDao().observe().collect { draft ->
                _state.update { it.copy(repDraft = draft) }
            }
        }
        viewModelScope.launch {
            settingsRepo.settings.collect { settings ->
                val previous = _state.value.settings
                _state.update { it.copy(settings = settings) }
                if (
                    settings.autoBackupLastSuccessAt != previous.autoBackupLastSuccessAt &&
                    _state.value.sheet is Sheet.BackupManager
                ) {
                    loadBackupEntries()
                }
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
                    if (!TrainingServiceState.active.value) {
                        TrainingServiceController.start(appContext)
                    }
                } else if (active == null) {
                    TrainingServiceController.stop(appContext)
                }
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
        db.invalidationTracker.addObserver(dbObserver)
        viewModelScope.launch {
            TrainingServiceState.active.collect { active ->
                _state.update { it.copy(serviceActive = active) }
            }
        }
        viewModelScope.launch {
            delay(1500)
            backupArmed = true
        }
        viewModelScope.launch {
            syncManager.status.collect { status ->
                _state.update { it.copy(sync = status) }
            }
        }
        viewModelScope.launch {
            db.syncOutboxDao().observeCount().collect { count ->
                _state.update { it.copy(sync = it.sync.copy(pending = count)) }
            }
        }
        startTicker()
    }

    override fun onCleared() {
        db.invalidationTracker.removeObserver(dbObserver)
        super.onCleared()
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
                        if (TrainingServiceState.active.value) {
                            // 前台服务负责写标记并播报，避免 VM 抢先标记导致服务静默。
                        } else {
                            val notified = active.copy(notifiedAtTarget = true)
                            withContext(Dispatchers.IO) { db.activeSessionDao().upsert(notified) }
                            emitVoice(VoiceKind.TARGET)
                        }
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
                val time = ProjectEntity(
                    id = UUID.randomUUID().toString(),
                    name = "平板支撑",
                    defaultTargetSec = 15 * 60,
                    presetsSec = "600,900,1200,1500",
                    createdAt = now,
                    updatedAt = now,
                    mode = ProjectEntity.MODE_TIME
                )
                val reps = ProjectEntity(
                    id = UUID.randomUUID().toString(),
                    name = "引体向上",
                    defaultTargetSec = 15 * 60,
                    presetsSec = "600,900,1200,1500",
                    createdAt = now + 1,
                    updatedAt = now + 1,
                    mode = ProjectEntity.MODE_REPS,
                    plannedSets = 3,
                    targetRepsPerSet = 10
                )
                db.projectDao().upsert(time)
                db.projectDao().upsert(reps)
                settingsRepo.setSelectedProject(time.id)
                settingsRepo.setLastTimeProject(time.id)
                settingsRepo.setLastRepsProject(reps.id)
            } finally {
                seeding = false
            }
        }
    }

    private fun reconcileProjectSelection() {
        val s = _state.value
        if (s.projects.isEmpty()) return
        val selected = s.settings.selectedProjectId
        val mode = s.tab.trainMode
        val candidate = if (mode != null) {
            val preferred = when (mode) {
                TrainingMode.TIME -> s.settings.lastTimeProjectId
                else -> s.settings.lastRepsProjectId
            }
            s.projects.firstOrNull {
                !it.isArchived && it.mode == mode && (it.id == preferred || it.id == selected)
            } ?: s.projects.firstOrNull { !it.isArchived && it.mode == mode }
        } else {
            s.projects.firstOrNull { !it.isArchived && it.id == selected }
                ?: s.projects.firstOrNull { !it.isArchived }
        }
        if (candidate != null && candidate.id != selected) {
            viewModelScope.launch(Dispatchers.IO) {
                settingsRepo.setSelectedProject(candidate.id)
                if (candidate.mode == TrainingMode.TIME) {
                    settingsRepo.setLastTimeProject(candidate.id)
                } else {
                    settingsRepo.setLastRepsProject(candidate.id)
                }
            }
        } else if (mode == TrainingMode.TIME && s.settings.lastTimeProjectId == null && candidate != null) {
            viewModelScope.launch(Dispatchers.IO) { settingsRepo.setLastTimeProject(candidate.id) }
        } else if (mode == TrainingMode.REPS && s.settings.lastRepsProjectId == null && candidate != null) {
            viewModelScope.launch(Dispatchers.IO) { settingsRepo.setLastRepsProject(candidate.id) }
        }
    }

    fun selectTab(tab: Tab) {
        val s = _state.value
        if (s.tab == tab) return
        val mode = tab.trainMode
        var settings = s.settings
        if (mode != null) {
            val preferred = when (mode) {
                TrainingMode.TIME -> s.settings.lastTimeProjectId
                else -> s.settings.lastRepsProjectId
            }
            val project = s.projects.firstOrNull {
                !it.isArchived && it.mode == mode && it.id == preferred
            } ?: s.projects.firstOrNull { !it.isArchived && it.mode == mode }
            if (project != null && project.id != settings.selectedProjectId) {
                settings = settings.copy(selectedProjectId = project.id)
                viewModelScope.launch(Dispatchers.IO) {
                    settingsRepo.setSelectedProject(project.id)
                    if (mode == TrainingMode.TIME) {
                        settingsRepo.setLastTimeProject(project.id)
                    } else {
                        settingsRepo.setLastRepsProject(project.id)
                    }
                }
            }
        }
        _state.update { it.copy(tab = tab, settings = settings) }
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
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.setSelectedProject(id)
            if (project.mode == TrainingMode.TIME) {
                settingsRepo.setLastTimeProject(id)
            } else {
                settingsRepo.setLastRepsProject(id)
            }
            autoBackup.requestBackup()
        }
        _state.update {
            it.copy(
                settings = it.settings.copy(selectedProjectId = id, lastProjectFilter = "current"),
                tab = if (project.mode == TrainingMode.TIME) Tab.TIME else Tab.REPS,
                sheet = null
            )
        }
    }

    fun setTargetMinutes(minutes: Double) {
        val project = _state.value.selectedProject ?: return
        if (project.isReps) {
            showToast("计次项目请修改默认组数与每组次数")
            return
        }
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
            saveProject(
                ProjectEntity(
                    id = project.id,
                    name = project.name,
                    defaultTargetSec = targetSec,
                    presetsSec = project.presetsSec.joinToString(","),
                    archivedAt = project.archivedAt,
                    createdAt = project.createdAt,
                    updatedAt = System.currentTimeMillis(),
                    mode = project.mode.name,
                    plannedSets = project.plannedSets,
                    targetRepsPerSet = project.targetRepsPerSet
                )
            )
        }
        _state.update { it.copy(sheet = null) }
        showToast("目标已保存为 ${com.wanghoufan.zaijiuyidian.domain.Formatters.minutes(targetSec)} 分钟")
    }

    fun setPresets(minutes: List<Double>) {
        val project = _state.value.selectedProject ?: return
        if (project.isReps) {
            showToast("计次项目没有时长预设")
            return
        }
        if (minutes.isEmpty() || minutes.size > 8 || minutes.any { it < 1 || it > 300 }) {
            showToast("请设置 1～8 个有效档位")
            return
        }
        val presets = minutes.map { Math.round(it * 60).toInt() }.distinct().sorted()
        viewModelScope.launch(Dispatchers.IO) {
            saveProject(
                ProjectEntity(
                    id = project.id,
                    name = project.name,
                    defaultTargetSec = project.defaultTargetSec,
                    presetsSec = presets.joinToString(","),
                    archivedAt = project.archivedAt,
                    createdAt = project.createdAt,
                    updatedAt = System.currentTimeMillis(),
                    mode = project.mode.name,
                    plannedSets = project.plannedSets,
                    targetRepsPerSet = project.targetRepsPerSet
                )
            )
        }
        _state.update { it.copy(sheet = null) }
        showToast("预设档位已更新")
    }

    fun createProject(
        name: String,
        mode: TrainingMode = TrainingMode.TIME,
        targetMinutes: Double = 15.0,
        plannedSets: Int = 3,
        targetRepsPerSet: Int = 10
    ) {
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
        if (mode == TrainingMode.TIME && (targetMinutes < 1 || targetMinutes > 300)) {
            showToast("请输入 1～300 分钟")
            return
        }
        if (mode == TrainingMode.REPS && (plannedSets !in 1..40 || targetRepsPerSet !in 1..999)) {
            showToast("组数须为 1～40，每组目标须为 1～999")
            return
        }
        val targetSec = if (mode == TrainingMode.TIME) {
            Math.round(targetMinutes * 60).toInt().coerceIn(60, 18_000)
        } else {
            15 * 60
        }
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val entity = ProjectEntity(
                id = UUID.randomUUID().toString(),
                name = trimmed,
                defaultTargetSec = targetSec,
                presetsSec = "600,900,1200,1500",
                createdAt = now,
                updatedAt = now,
                mode = if (mode == TrainingMode.TIME) ProjectEntity.MODE_TIME else ProjectEntity.MODE_REPS,
                plannedSets = plannedSets,
                targetRepsPerSet = targetRepsPerSet
            )
            db.projectDao().upsert(entity)
            syncManager.enqueueProject(entity.id, SyncOutboxEntity.OP_UPSERT)
            settingsRepo.setSelectedProject(entity.id)
            if (mode == TrainingMode.TIME) {
                settingsRepo.setLastTimeProject(entity.id)
            } else {
                settingsRepo.setLastRepsProject(entity.id)
            }
        }
        _state.update {
            it.copy(
                sheet = null,
                tab = if (mode == TrainingMode.TIME) Tab.TIME else Tab.REPS,
                settings = it.settings.copy(lastProjectFilter = "current")
            )
        }
        showToast("项目已创建")
    }

    fun setRepsDefaults(plannedSets: Int, targetRepsPerSet: Int) {
        val project = _state.value.selectedProject ?: return
        if (!project.isReps) {
            showToast("计时项目请修改目标时长")
            return
        }
        if (plannedSets !in 1..40 || targetRepsPerSet !in 1..999) {
            showToast("组数须为 1～40，每组目标须为 1～999")
            return
        }
        if (_state.value.active != null) {
            showToast("训练进行中，默认计划将在下一次训练生效")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            saveProject(
                ProjectEntity(
                    id = project.id,
                    name = project.name,
                    defaultTargetSec = project.defaultTargetSec,
                    presetsSec = project.presetsSec.joinToString(","),
                    archivedAt = project.archivedAt,
                    createdAt = project.createdAt,
                    updatedAt = System.currentTimeMillis(),
                    mode = project.mode.name,
                    plannedSets = plannedSets,
                    targetRepsPerSet = targetRepsPerSet
                )
            )
        }
        _state.update { it.copy(sheet = null) }
        showToast("计次默认计划已更新")
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
            saveProject(
                ProjectEntity(
                    id = project.id,
                    name = trimmed,
                    defaultTargetSec = project.defaultTargetSec,
                    presetsSec = project.presetsSec.joinToString(","),
                    archivedAt = project.archivedAt,
                    createdAt = project.createdAt,
                    updatedAt = System.currentTimeMillis(),
                    mode = project.mode.name,
                    plannedSets = project.plannedSets,
                    targetRepsPerSet = project.targetRepsPerSet
                )
            )
        }
        _state.update { it.copy(sheet = null) }
        showToast("项目已保存")
    }

    fun updateProjectSettings(
        name: String,
        targetMinutes: Double,
        plannedSets: Int,
        targetRepsPerSet: Int
    ) {
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
        if (_state.value.projects.any {
                it.id != project.id && it.name.equals(trimmed, ignoreCase = true)
            }
        ) {
            showToast("这个项目名称已经存在")
            return
        }
        if (project.isReps && (plannedSets !in 1..40 || targetRepsPerSet !in 1..999)) {
            showToast("组数须为 1～40，每组目标须为 1～999")
            return
        }
        if (!project.isReps && (targetMinutes < 1 || targetMinutes > 300)) {
            showToast("请输入 1～300 分钟")
            return
        }
        val targetSec = if (project.isReps) {
            project.defaultTargetSec
        } else {
            Math.round(targetMinutes * 60).toInt().coerceIn(60, 18_000)
        }
        viewModelScope.launch(Dispatchers.IO) {
            saveProject(
                ProjectEntity(
                    id = project.id,
                    name = trimmed,
                    defaultTargetSec = targetSec,
                    presetsSec = project.presetsSec.joinToString(","),
                    archivedAt = project.archivedAt,
                    createdAt = project.createdAt,
                    updatedAt = System.currentTimeMillis(),
                    mode = project.mode.name,
                    plannedSets = plannedSets,
                    targetRepsPerSet = targetRepsPerSet
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
        if (_state.value.repDraft?.projectId == projectId) {
            showToast("这个项目还有未完成草稿，请先保存或放弃")
            return
        }
        if (_state.value.projects.count { !it.isArchived && it.mode == project.mode } <= 1) {
            showToast("至少保留一个可用的${project.modeLabel}项目")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            saveProject(
                ProjectEntity(
                    id = project.id,
                    name = project.name,
                    defaultTargetSec = project.defaultTargetSec,
                    presetsSec = project.presetsSec.joinToString(","),
                    archivedAt = System.currentTimeMillis(),
                    createdAt = project.createdAt,
                    updatedAt = System.currentTimeMillis(),
                    mode = project.mode.name,
                    plannedSets = project.plannedSets,
                    targetRepsPerSet = project.targetRepsPerSet
                )
            )
        }
        _state.update { it.copy(sheet = null) }
        showToast("项目已归档，历史记录仍会保留")
    }

    fun restoreProject(projectId: String) {
        val project = _state.value.projects.firstOrNull { it.id == projectId } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            saveProject(
                ProjectEntity(
                    id = project.id,
                    name = project.name,
                    defaultTargetSec = project.defaultTargetSec,
                    presetsSec = project.presetsSec.joinToString(","),
                    archivedAt = null,
                    createdAt = project.createdAt,
                    updatedAt = System.currentTimeMillis(),
                    mode = project.mode.name,
                    plannedSets = project.plannedSets,
                    targetRepsPerSet = project.targetRepsPerSet
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
        if (_state.value.repDraft?.projectId == projectId) {
            showToast("这个项目还有未完成草稿，请先保存或放弃")
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
        val project = state.projects.firstOrNull { it.id == projectId } ?: return
        if (state.projects.count { !it.isArchived && it.mode == project.mode } <= 1) {
            showToast("至少保留一个可用的${project.modeLabel}项目")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            db.projectDao().softDelete(projectId, now)
            syncManager.enqueueProject(projectId, SyncOutboxEntity.OP_DELETE)
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
        if (project.isReps) {
            showToast("计次项目请用「完成一组」记录")
            return
        }
        if (s.repDraft != null) {
            showToast("还有未完成的计次草稿，请先保存或放弃")
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
        scheduleTargetAlarm(session)
        val serviceStarted = TrainingServiceController.start(appContext)
        emitVoice(VoiceKind.START)
        val notificationsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        when {
            !notificationsAllowed -> showToast("已开始计时；通知权限未开，锁屏提醒可能不可用")
            serviceStarted -> showToast("已开始计时，锁屏也会继续提醒")
            else -> showToast("已开始计时；系统未允许后台提醒，本次仅前台播报")
        }
    }

    fun startRepsTraining() {
        val s = _state.value
        if (s.active != null) {
            showToast("请先结束正在进行的计时训练")
            return
        }
        val project = s.selectedProject ?: return
        if (!project.isReps) {
            showToast("请选择一个计次项目")
            return
        }
        val existing = s.repDraft
        if (existing != null) {
            if (existing.projectId == project.id) {
                showToast("草稿已恢复，继续记录下一组")
            } else {
                showToast("另一个项目还有未完成草稿，请先继续或放弃")
            }
            return
        }
        val now = System.currentTimeMillis()
        viewModelScope.launch(Dispatchers.IO) {
            db.repDraftDao().upsert(
                RepDraftEntity(
                    projectId = project.id,
                    plannedSets = project.plannedSets,
                    targetRepsPerSet = project.targetRepsPerSet,
                    setsCsv = "",
                    nextReps = project.targetRepsPerSet,
                    touchedAt = now
                )
            )
        }
        showToast("已开始，做完一组就记录")
    }

    fun resumeRepDraft() {
        val draft = _state.value.repDraft ?: return
        val project = _state.value.projects.firstOrNull { it.id == draft.projectId } ?: run {
            showToast("草稿所属项目已不存在，请放弃草稿")
            return
        }
        selectProject(project.id)
    }

    fun updateDraftPlan(plannedSets: Int, targetRepsPerSet: Int) {
        if (plannedSets !in 1..40 || targetRepsPerSet !in 1..999) {
            showToast("组数须为 1～40，每组目标须为 1～999")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val draft = db.repDraftDao().get() ?: return@launch
            db.repDraftDao().upsert(
                draft.copy(
                    plannedSets = plannedSets,
                    targetRepsPerSet = targetRepsPerSet,
                    nextReps = targetRepsPerSet,
                    touchedAt = System.currentTimeMillis()
                )
            )
        }
        _state.update { it.copy(sheet = null) }
        showToast("本次计划已更新，已记录的组不变")
    }

    fun addRepSet(reps: Int) {
        if (reps !in 1..999) {
            showToast("请输入 1～999 的实际次数")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val draft = db.repDraftDao().get() ?: return@launch
            val values = draft.sets + reps
            if (values.size > 40) {
                withContext(Dispatchers.Main) { showToast("最多记录 40 组") }
                return@launch
            }
            db.repDraftDao().upsert(
                draft.copy(
                    setsCsv = values.joinToString(","),
                    nextReps = draft.targetRepsPerSet,
                    touchedAt = System.currentTimeMillis()
                )
            )
            withContext(Dispatchers.Main) { showToast("第 ${values.size} 组已记录") }
        }
    }

    fun undoLastRepSet() {
        viewModelScope.launch(Dispatchers.IO) {
            val draft = db.repDraftDao().get() ?: return@launch
            if (draft.sets.isEmpty()) return@launch
            val values = draft.sets.dropLast(1)
            val removed = draft.sets.last()
            db.repDraftDao().upsert(
                draft.copy(
                    setsCsv = values.joinToString(","),
                    nextReps = removed,
                    touchedAt = System.currentTimeMillis()
                )
            )
            withContext(Dispatchers.Main) { showToast("已撤销上一组") }
        }
    }

    fun editRepSet(index: Int, reps: Int) {
        if (reps !in 1..999) {
            showToast("请输入 1～999 的实际次数")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val draft = db.repDraftDao().get() ?: return@launch
            val values = draft.sets.toMutableList()
            if (index !in values.indices) return@launch
            values[index] = reps
            db.repDraftDao().upsert(
                draft.copy(
                    setsCsv = values.joinToString(","),
                    touchedAt = System.currentTimeMillis()
                )
            )
            withContext(Dispatchers.Main) {
                _state.update { it.copy(sheet = null) }
                showToast("这组次数已更新")
            }
        }
    }

    fun removeRepSet(index: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val draft = db.repDraftDao().get() ?: return@launch
            val values = draft.sets.toMutableList()
            if (index !in values.indices) return@launch
            values.removeAt(index)
            db.repDraftDao().upsert(
                draft.copy(
                    setsCsv = values.joinToString(","),
                    touchedAt = System.currentTimeMillis()
                )
            )
            withContext(Dispatchers.Main) {
                _state.update { it.copy(sheet = null) }
                showToast("已删除这组")
            }
        }
    }

    fun discardRepDraft() {
        viewModelScope.launch(Dispatchers.IO) {
            db.repDraftDao().delete()
            TrainingAlerts.cancelNotification(appContext)
            withContext(Dispatchers.Main) {
                _state.update { it.copy(sheet = null) }
                showToast("草稿已放弃")
            }
        }
    }

    fun finishReps(note: String) {
        val draft = _state.value.repDraft ?: return
        if (draft.sets.isEmpty()) {
            showToast("至少记录 1 组再保存")
            return
        }
        val project = _state.value.projects.firstOrNull { it.id == draft.projectId } ?: run {
            showToast("项目不存在，无法保存")
            return
        }
        val now = System.currentTimeMillis()
        val sessionId = UUID.randomUUID().toString()
        val session = SessionEntity(
            id = sessionId,
            projectId = project.id,
            startedAt = null,
            endedAt = now,
            targetSec = 0,
            actualSec = 0,
            pauseCount = 0,
            pausedSec = 0,
            source = SessionEntity.SOURCE_REPS,
            note = note.trim().take(300),
            createdAt = now,
            updatedAt = now,
            mode = SessionEntity.MODE_REPS,
            plannedSets = draft.plannedSets,
            targetRepsPerSet = draft.targetRepsPerSet
        )
        val sets = draft.sets.mapIndexed { index, reps ->
            RepSetEntity(
                id = "$sessionId:${index + 1}",
                sessionId = sessionId,
                ordinal = index + 1,
                actualReps = reps
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            db.withTransaction {
                db.sessionDao().upsertBumped(session)
                db.repSetDao().upsertAll(sets)
                db.repDraftDao().delete()
            }
            syncManager.enqueueSession(sessionId, SyncOutboxEntity.OP_UPSERT)
            withContext(Dispatchers.Main) {
                _state.update {
                    it.copy(
                        sheet = null,
                        tab = Tab.HISTORY,
                        settings = it.settings.copy(lastProjectFilter = "current")
                    )
                }
                showToast("训练已保存，做得好！")
            }
        }
    }

    fun pauseTraining() {
        val active = _state.value.active ?: return
        if (active.interrupted) return
        if (TimerMath.isPaused(active)) return
        val nowElapsed = SystemClock.elapsedRealtime()
        val paused = TimerMath.pause(active, nowElapsed)
        TrainingAlerts.cancelAlarm(appContext, alarmSessionId(active))
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
        scheduleTargetAlarm(resumed)
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
        TrainingAlerts.cancelAll(appContext, alarmSessionId(active))
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
            deletedAt = null,
            mode = SessionEntity.MODE_TIME,
            plannedSets = 3,
            targetRepsPerSet = 10
        )
        viewModelScope.launch(Dispatchers.IO) {
            db.withTransactionSave(session, clearActive = true)
            TrainingAlerts.cancelAll(appContext, alarmSessionId(active))
            TrainingServiceController.stop(appContext)
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
            TrainingAlerts.cancelAll(appContext, alarmSessionId(active))
            TrainingServiceController.stop(appContext)
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
                TrainingAlerts.cancelAll(appContext, alarmSessionId(active))
                TrainingServiceController.stop(appContext)
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
            updatedAt = now,
            mode = SessionEntity.MODE_TIME,
            plannedSets = 3,
            targetRepsPerSet = 10
        )
        viewModelScope.launch(Dispatchers.IO) {
            db.withTransactionSave(session, clearActive = true)
            TrainingAlerts.cancelAll(appContext, alarmSessionId(active))
            TrainingServiceController.stop(appContext)
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
        val dismissed = _state.value.sheet
        _state.update { it.copy(sheet = null) }
        if (dismissed is Sheet.FinishConfirm) {
            val active = _state.value.active
            if (active != null && !active.interrupted && !TimerMath.isPaused(active)) {
                scheduleTargetAlarm(active)
            }
        }
    }

    private fun alarmSessionId(active: ActiveSessionEntity): String =
        "${active.projectId}:${active.startedAtWall}"

    private fun scheduleTargetAlarm(active: ActiveSessionEntity) {
        if (active.interrupted) return
        val name = _state.value.projects.firstOrNull { it.id == active.projectId }?.name
        val elapsed = TimerMath.elapsedMs(active, SystemClock.elapsedRealtime())
        TrainingAlerts.schedule(
            appContext,
            alarmSessionId(active),
            name,
            active.targetSec,
            active.targetSec * 1000L - elapsed
        )
    }

    fun testTargetAlert() {
        val project = _state.value.activeProject ?: _state.value.selectedProject
        TrainingAlerts.notifyTarget(
            appContext,
            project?.name,
            project?.defaultTargetSec ?: 60
        )
        showToast("已发送测试到点提醒")
    }

    fun notificationsAllowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    fun exactAlarmsAllowed(): Boolean = TrainingAlerts.isExactAlarmAllowed(appContext)

    fun openNotificationSettings() {
        runCatching {
            appContext.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, appContext.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        runCatching {
            appContext.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${appContext.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    fun saveEntry(draft: EntryDraft) {
        val project = _state.value.projects.firstOrNull { it.id == draft.projectId }
        if (draft.projectId.isBlank() || project == null) {
            showToast("请选择所属项目")
            return
        }
        if (project.isReps) {
            showToast("计次记录请逐组填写")
            return
        }
        val existing = draft.id?.let { id -> _state.value.sessions.firstOrNull { it.id == id } }
        if (existing?.isReps == true) {
            showToast("计次记录请逐组编辑")
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
            deletedAt = null,
            mode = SessionEntity.MODE_TIME,
            plannedSets = 3,
            targetRepsPerSet = 10
        )
        viewModelScope.launch(Dispatchers.IO) {
            db.sessionDao().upsertBumped(session)
            syncManager.enqueueSession(session.id, SyncOutboxEntity.OP_UPSERT)
            withContext(Dispatchers.Main) {
                _state.update { it.copy(sheet = null) }
                showToast(if (existing == null) "已补录记录" else "记录已更新")
            }
        }
    }

    fun repEntryDraftFor(sessionId: String?): RepEntryDraft {
        val session = sessionId?.let { id -> _state.value.sessions.firstOrNull { it.id == id } }
        if (session != null) {
            return RepEntryDraft(
                id = session.id,
                projectId = session.projectId,
                endedAtMs = session.endedAt,
                plannedSets = session.plannedSets,
                targetRepsPerSet = session.targetRepsPerSet,
                sets = session.sets,
                note = session.note
            )
        }
        val project = _state.value.selectedProject?.takeIf { it.isReps }
            ?: _state.value.projects.firstOrNull { !it.isArchived && it.isReps }
        val plannedSets = project?.plannedSets ?: 3
        val targetReps = project?.targetRepsPerSet ?: 10
        return RepEntryDraft(
            id = null,
            projectId = project?.id.orEmpty(),
            endedAtMs = System.currentTimeMillis(),
            plannedSets = plannedSets,
            targetRepsPerSet = targetReps,
            sets = List(plannedSets) { targetReps },
            note = ""
        )
    }

    fun saveRepEntry(draft: RepEntryDraft) {
        val project = _state.value.projects.firstOrNull { it.id == draft.projectId }
        if (project == null || !project.isReps) {
            showToast("请选择计次项目")
            return
        }
        if (draft.sets.isEmpty() || draft.sets.size > 40) {
            showToast("请填写 1～40 组实际次数")
            return
        }
        if (draft.sets.any { it !in 1..999 }) {
            showToast("每组次数须为 1～999")
            return
        }
        if (draft.endedAtMs <= 0 || draft.endedAtMs > System.currentTimeMillis() + 86_400_000L) {
            showToast("请选择正确的完成时间")
            return
        }
        val existing = draft.id?.let { id -> _state.value.sessions.firstOrNull { it.id == id } }
        if (existing != null && !existing.isReps) {
            showToast("已有计时记录不能改成计次，请删除后重新补录")
            return
        }
        val now = System.currentTimeMillis()
        val sessionId = draft.id ?: UUID.randomUUID().toString()
        val session = SessionEntity(
            id = sessionId,
            projectId = project.id,
            startedAt = existing?.startedAt,
            endedAt = draft.endedAtMs,
            targetSec = 0,
            actualSec = 0,
            pauseCount = existing?.pauseCount ?: 0,
            pausedSec = existing?.pausedSec ?: 0,
            source = existing?.source ?: SessionEntity.SOURCE_MANUAL,
            note = draft.note.trim().take(300),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            deletedAt = null,
            mode = SessionEntity.MODE_REPS,
            plannedSets = draft.plannedSets,
            targetRepsPerSet = draft.targetRepsPerSet
        )
        val sets = draft.sets.mapIndexed { index, reps ->
            RepSetEntity(
                id = "$sessionId:${index + 1}",
                sessionId = sessionId,
                ordinal = index + 1,
                actualReps = reps
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            db.withTransaction {
                db.sessionDao().upsertBumped(session)
                db.repSetDao().deleteForSession(sessionId)
                db.repSetDao().upsertAll(sets)
            }
            syncManager.enqueueSession(sessionId, SyncOutboxEntity.OP_UPSERT)
            withContext(Dispatchers.Main) {
                _state.update { it.copy(sheet = null) }
                showToast(if (existing == null) "已补录计次记录" else "计次记录已更新")
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
            syncManager.enqueueSession(sheet.sessionId, SyncOutboxEntity.OP_DELETE)
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
            syncManager.enqueueSession(id, SyncOutboxEntity.OP_UPSERT)
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
        viewModelScope.launch(Dispatchers.IO) { settingsRepo.setProjectFilter(filter); autoBackup.requestBackup() }
        _state.update { it.copy(settings = it.settings.copy(lastProjectFilter = filter)) }
    }

    fun setRangeDays(days: Int?) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepo.setRangeDays(days); autoBackup.requestBackup() }
        _state.update { it.copy(settings = it.settings.copy(lastRangeDays = days)) }
    }

    fun setChartMode(mode: ChartMode) {
        viewModelScope.launch(Dispatchers.IO) { settingsRepo.setChart(mode); autoBackup.requestBackup() }
        _state.update { it.copy(settings = it.settings.copy(lastChart = mode)) }
    }

    fun toggleTheme() {
        val next = if (_state.value.settings.theme == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK
        viewModelScope.launch(Dispatchers.IO) { settingsRepo.setTheme(next); autoBackup.requestBackup() }
    }

    fun toggleSound() {
        val next = !_state.value.settings.soundEnabled
        viewModelScope.launch(Dispatchers.IO) { settingsRepo.setSound(next); autoBackup.requestBackup() }
    }

    fun toggleVibration() {
        val next = !_state.value.settings.vibrationEnabled
        viewModelScope.launch(Dispatchers.IO) { settingsRepo.setVibration(next); autoBackup.requestBackup() }
    }

    fun toggleVoice() {
        val next = !_state.value.settings.voiceEnabled
        viewModelScope.launch(Dispatchers.IO) { settingsRepo.setVoice(next); autoBackup.requestBackup() }
    }

    fun setFontScalePercent(percent: Int) {
        val clamped = com.wanghoufan.zaijiuyidian.domain.FontScaleOptions.clamp(percent)
        viewModelScope.launch(Dispatchers.IO) { settingsRepo.setFontScalePercent(clamped); autoBackup.requestBackup() }
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
        if (!TrainingServiceState.active.value) {
            TrainingServiceController.start(appContext)
        }
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
        if (_state.value.active != null) {
            showToast("请先结束当前训练再恢复")
            return
        }
        _state.update { it.copy(busy = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val protected = autoBackup.withRestoreLock {
                    val safety = autoBackup.writeSafetySnapshot()
                    if (safety == null) {
                        false
                    } else {
                        backupRepo.replace(data, _state.value.settings)
                        true
                    }
                }
                if (!protected) {
                    withContext(Dispatchers.Main) {
                        _state.update { it.copy(busy = false) }
                        showToast("无法创建可访问的保护点，恢复已取消；请先在「备份与恢复」选择备份文件夹")
                    }
                    return@launch
                }
                applyRestoredSettings(data)
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
                    showToast(
                        if (data.version >= BackupCodec.SCHEMA_VERSION) {
                            "备份恢复成功：${data.projects.size} 个项目、${data.sessions.size} 条记录"
                        } else {
                            "旧版备份已恢复主题与提示；语音、字体和筛选保持当前设置"
                        }
                    )
                }
                loadBackupEntries()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _state.update { it.copy(busy = false) }
                    showToast("恢复失败，现有数据已保留")
                }
            }
        }
    }

    private suspend fun applyRestoredSettings(data: BackupData) {
        val settings = data.settings
        val isV2 = data.version >= BackupCodec.V2_SCHEMA_VERSION
        settingsRepo.setTheme(if (settings.theme == "dark") ThemeMode.DARK else ThemeMode.LIGHT)
        settingsRepo.setSound(settings.soundEnabled)
        settingsRepo.setVibration(settings.vibrationEnabled)
        var restoredFilter = _state.value.settings.lastProjectFilter ?: "current"
        if (isV2) {
            settingsRepo.setVoice(settings.voiceEnabled)
            settingsRepo.setFontScalePercent(settings.fontScalePercent)
            settingsRepo.setRangeDays(settings.lastRangeDays)
            val chart = runCatching { ChartMode.valueOf(settings.lastChart) }.getOrDefault(ChartMode.TREND)
            settingsRepo.setChart(chart)
            val filter = settings.lastProjectFilter
            restoredFilter = if (
                filter == "all" || filter == "current" || data.projects.any { it.id == filter }
            ) {
                filter ?: "current"
            } else {
                "current"
            }
            settingsRepo.setProjectFilter(restoredFilter)
        }
        if (data.version >= BackupCodec.SCHEMA_VERSION) {
            settings.lastTimeProjectId?.let { settingsRepo.setLastTimeProject(it) }
            settings.lastRepsProjectId?.let { settingsRepo.setLastRepsProject(it) }
        }
        data.projectId?.let { settingsRepo.setSelectedProject(it) }
        val projects = db.projectDao().getAll().map { it.toModel() }
        val setsBySession = db.repSetDao().getAll().groupBy { it.sessionId }
            .mapValues { (_, rows) -> rows.sortedBy { it.ordinal }.map { it.actualReps } }
        val allSessions = db.sessionDao().getAll().map { it.toModel(setsBySession[it.id].orEmpty()) }
        withContext(Dispatchers.Main) {
            val deleted = allSessions.filter { it.deletedAt != null }
                .sortedByDescending { it.deletedAt }
            _state.update { current ->
                val base = current.settings.copy(
                    selectedProjectId = data.projectId ?: current.settings.selectedProjectId,
                    theme = if (settings.theme == "dark") ThemeMode.DARK else ThemeMode.LIGHT,
                    soundEnabled = settings.soundEnabled,
                    vibrationEnabled = settings.vibrationEnabled,
                    lastProjectFilter = restoredFilter
                )
                current.copy(
                    projects = projects,
                    sessions = allSessions.filter { it.deletedAt == null },
                    deletedSessions = deleted,
                    settings = if (isV2) base.copy(
                        voiceEnabled = settings.voiceEnabled,
                        fontScalePercent = settings.fontScalePercent,
                        lastRangeDays = settings.lastRangeDays,
                        lastChart = runCatching { ChartMode.valueOf(settings.lastChart) }
                            .getOrDefault(ChartMode.TREND)
                    ) else base
                )
            }
        }
    }

    private suspend fun saveProject(entity: ProjectEntity) {
        db.projectDao().updateBumped(entity)
        syncManager.enqueueProject(entity.id, SyncOutboxEntity.OP_UPSERT)
    }

    private suspend fun saveSession(entity: SessionEntity) {
        db.sessionDao().upsertBumped(entity)
        syncManager.enqueueSession(entity.id, SyncOutboxEntity.OP_UPSERT)
    }

    fun openCloudSync() {
        _state.update { it.copy(sheet = Sheet.SyncInfo) }
        viewModelScope.launch {
            syncManager.refreshPending()
            if (_state.value.sync.signedIn && _state.value.sync.phase == SyncPhase.NEED_UPLOAD) {
                syncManager.prepareInitialPreview()
            }
        }
    }

    fun cloudSignIn(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            showToast("请输入账号和密码")
            return
        }
        viewModelScope.launch {
            syncManager.signIn(username, password)
                .onSuccess {
                    syncManager.prepareInitialPreview()
                    showToast("已登录，请确认首次上传")
                }
                .onFailure { showToast(it.message ?: "登录失败") }
        }
    }

    fun cloudUploadInitial() {
        viewModelScope.launch {
            syncManager.uploadInitial()
                .onSuccess { showToast("首次上传完成，已开启云同步") }
                .onFailure { showToast(syncManager.status.value.message ?: "上传失败") }
        }
    }

    fun cloudSyncNow() {
        viewModelScope.launch {
            syncManager.syncNow()
                .onSuccess { showToast("已同步") }
                .onFailure { showToast(syncManager.status.value.message ?: "同步失败") }
        }
    }

    fun cloudSignOut() {
        viewModelScope.launch {
            syncManager.signOut()
            showToast("已退出云同步，本机数据未删除")
        }
    }

    fun resolveSyncConflictKeepLocal(item: SyncConflictItem) {
        viewModelScope.launch {
            syncManager.resolveKeepLocal(item.entity, item.recordId)
                .onSuccess { showToast("已用本机版本覆盖云端") }
                .onFailure { showToast(it.message ?: "处理冲突失败") }
        }
    }

    fun resolveSyncConflictUseCloud(item: SyncConflictItem) {
        viewModelScope.launch {
            syncManager.resolveUseCloud(item.entity, item.recordId)
                .onSuccess { showToast("已用云端版本覆盖本机") }
                .onFailure { showToast(it.message ?: "处理冲突失败") }
        }
    }

    fun openBackupManager() {
        _state.update { it.copy(sheet = Sheet.BackupManager) }
        loadBackupEntries()
    }

    fun backupNow() {
        if (!_state.value.settings.autoBackupEnabled) {
            showToast("请先开启自动备份并选择文件夹")
            return
        }
        _state.update { it.copy(busy = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = autoBackup.performBackup()
            withContext(Dispatchers.Main) {
                _state.update { it.copy(busy = false) }
                showToast(if (ok) "自动备份已生成并校验" else "自动备份失败，请查看状态")
            }
            loadBackupEntries()
        }
    }

    fun loadBackupEntries() {
        _state.update { it.copy(backupEntriesLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val entries = autoBackup.listEntries()
            withContext(Dispatchers.Main) {
                _state.update { it.copy(backupEntries = entries, backupEntriesLoading = false) }
            }
        }
    }

    fun onBackupTreeSelected(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                appContext.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                val writable = appContext.contentResolver.persistedUriPermissions.any {
                    it.uri == uri && it.isWritePermission
                }
                if (!writable) {
                    withContext(Dispatchers.Main) {
                        showToast("该文件夹不支持持久写入，请换一个文件夹")
                    }
                    return@launch
                }
                settingsRepo.setAutoBackup(true, uri.toString())
                settingsRepo.setAutoBackupError(null)
                withContext(Dispatchers.Main) {
                    _state.update {
                        it.copy(
                            settings = it.settings.copy(
                                autoBackupEnabled = true,
                                autoBackupTreeUri = uri.toString(),
                                autoBackupLastError = null
                            )
                        )
                    }
                    showToast("备份文件夹已授权，正在生成首个自动备份")
                }
                autoBackup.requestBackup()
                loadBackupEntries()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("目录授权失败：${e.message ?: "请重试或换一个文件夹"}")
                }
            }
        }
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        val tree = _state.value.settings.autoBackupTreeUri
        if (enabled && tree.isNullOrBlank()) {
            showToast("请先选择备份文件夹")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.setAutoBackup(enabled, tree)
            withContext(Dispatchers.Main) {
                _state.update {
                    it.copy(settings = it.settings.copy(autoBackupEnabled = enabled))
                }
                showToast(if (enabled) "自动备份已开启" else "自动备份已关闭，已有文件不会删除")
            }
            if (enabled) autoBackup.requestBackup()
        }
    }

    fun requestRestoreBackup(entry: BackupEntry) {
        if (!entry.valid) {
            showToast("这个备份文件校验失败，不能恢复")
            return
        }
        _state.update { it.copy(sheet = Sheet.BackupRestorePreview(entry)) }
    }

    fun confirmRestoreBackup(entry: BackupEntry) {
        if (_state.value.active != null) {
            showToast("请先结束当前训练再恢复")
            return
        }
        _state.update { it.copy(busy = true, sheet = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val data = autoBackup.withRestoreLock {
                    val safety = autoBackup.writeSafetySnapshot()
                    if (safety == null) {
                        null
                    } else {
                        val text = readBackupText(entry.uri)
                        val parsed = BackupCodec.parse(text).data
                        backupRepo.replace(parsed, _state.value.settings)
                        parsed
                    }
                }
                if (data == null) {
                    withContext(Dispatchers.Main) {
                        _state.update { it.copy(busy = false) }
                        showToast("无法创建可访问的保护点，恢复已取消：${_state.value.settings.autoBackupLastError ?: "请重试"}")
                    }
                    return@launch
                }
                applyRestoredSettings(data)
                withContext(Dispatchers.Main) {
                    _state.update { it.copy(busy = false, tab = Tab.HISTORY) }
                    showToast("已恢复到所选备份；恢复前保护点已写入备份文件夹")
                }
                loadBackupEntries()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _state.update { it.copy(busy = false) }
                    showToast("恢复失败，现有数据已保留：${e.message ?: "未知错误"}")
                }
            }
        }
    }

    private fun readBackupText(uri: Uri): String {
        val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("无法读取备份文件")
        if (bytes.size > BackupCodec.MAX_FILE_BYTES) throw IllegalStateException("备份文件超过 15MB")
        return bytes.toString(Charsets.UTF_8)
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
