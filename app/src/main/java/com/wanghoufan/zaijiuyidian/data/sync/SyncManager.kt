package com.wanghoufan.zaijiuyidian.data.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.room.withTransaction
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.wanghoufan.zaijiuyidian.BuildConfig
import com.wanghoufan.zaijiuyidian.data.db.AppDatabase
import com.wanghoufan.zaijiuyidian.data.db.ProjectEntity
import com.wanghoufan.zaijiuyidian.data.db.RepSetEntity
import com.wanghoufan.zaijiuyidian.data.db.SessionEntity
import com.wanghoufan.zaijiuyidian.data.db.SyncOutboxEntity
import com.wanghoufan.zaijiuyidian.data.db.SyncStateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.util.UUID

enum class SyncPhase { OFF, NEED_UPLOAD, IDLE, SYNCING, ERROR }

data class SyncConflictItem(
    val entity: String,
    val recordId: String,
    val remoteRevision: Int,
    val localPayload: String,
    val remotePayload: String,
    val remoteDeletedAt: String?
)

data class SyncStatus(
    val configured: Boolean = BuildConfig.CLOUDBASE_ENV_ID.isNotBlank() &&
        BuildConfig.CLOUDBASE_PUBLISHABLE_KEY.isNotBlank(),
    val signedIn: Boolean = false,
    val account: String? = null,
    val phase: SyncPhase = SyncPhase.OFF,
    val pending: Int = 0,
    val lastSyncAt: Long? = null,
    val message: String? = null,
    val conflictItems: List<SyncConflictItem> = emptyList(),
    val previewProjects: Int = 0,
    val previewSessions: Int = 0
) {
    val conflicts: Int get() = conflictItems.size
}

class SyncManager(
    private val context: Context,
    private val db: AppDatabase
) {
    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val tokens = TokenStore(context)
    private val base = "https://${BuildConfig.CLOUDBASE_ENV_ID}.api.tcloudbasegateway.com"
    private val rest = "$base/v1/rdb/rest"
    private val projectTable = "p068_projects"
    private val sessionTable = "p068_sessions"

    private companion object {
        const val CONFLICT_KEY = "conflicts"
    }

    private val outbox get() = db.syncOutboxDao()
    private val stateDao get() = db.syncStateDao()
    private val projectDao get() = db.projectDao()
    private val sessionDao get() = db.sessionDao()
    private val repSetDao get() = db.repSetDao()

    init {
        _status.update {
            it.copy(
                signedIn = tokens.accessToken != null,
                account = tokens.account,
                phase = if (tokens.accessToken != null && tokens.uploadConfirmed) SyncPhase.IDLE else if (tokens.accessToken != null) SyncPhase.NEED_UPLOAD else SyncPhase.OFF
            )
        }
    }

    suspend fun refreshPending() {
        val count = outbox.count()
        val conflicts = readConflicts()
        _status.update { it.copy(pending = count, conflictItems = conflicts) }
    }

    suspend fun signIn(username: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            outbox.deleteAll()
            val body = JSONObject().put("username", username).put("password", password)
            val (code, text) = request(
                "POST", "$base/auth/v1/signin",
                token = BuildConfig.CLOUDBASE_PUBLISHABLE_KEY,
                body = body.toString()
            )
            if (code !in 200..299) error(httpMessage(code, text))
            val json = JSONObject(text)
            val access = json.optString("access_token").ifBlank { error("登录响应缺少 access_token") }
            val refresh = json.optString("refresh_token")
            val sub = json.optString("sub")
            tokens.save(access, refresh, sub, username, uploadConfirmed = false)
            _status.update {
                it.copy(
                    signedIn = true,
                    account = username,
                    phase = SyncPhase.NEED_UPLOAD,
                    message = "已登录，首次上传前请确认预览"
                )
            }
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        tokens.clear()
        outbox.deleteAll()
        stateDao.put(SyncStateEntity("cursor_projects", ""))
        stateDao.put(SyncStateEntity("cursor_sessions", ""))
        stateDao.put(SyncStateEntity(CONFLICT_KEY, "[]"))
        _status.update { SyncStatus(configured = it.configured) }
    }

    suspend fun prepareInitialPreview(): Result<SyncStatus> = withContext(Dispatchers.IO) {
        runCatching {
            val projects = projectDao.getAllIncludingDeleted().size
            val sessions = sessionDao.getAll().size
            _status.update {
                it.copy(
                    phase = SyncPhase.NEED_UPLOAD,
                    previewProjects = projects,
                    previewSessions = sessions,
                    message = "本机有 $projects 个项目、$sessions 条记录，确认后上传"
                )
            }
            _status.value
        }
    }

    suspend fun uploadInitial(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            requireSignedIn()
            val projects = projectDao.getAllIncludingDeleted()
            val sessions = sessionDao.getAll()
            projects.forEach { p ->
                val op = if (p.deletedAt != null) SyncOutboxEntity.OP_DELETE else SyncOutboxEntity.OP_UPSERT
                runCatching { pushProject(p, op, baseRevision = 0) }
                    .getOrElse { if (it !is SyncConflict) throw it }
            }
            sessions.forEach { s ->
                val op = if (s.deletedAt != null) SyncOutboxEntity.OP_DELETE else SyncOutboxEntity.OP_UPSERT
                runCatching {
                    pushSession(s, repSetDao.forSession(s.id), op, baseRevision = 0)
                }.getOrElse { if (it !is SyncConflict) throw it }
            }
            pull("project")
            pull("session")
            outbox.deleteAll()
            tokens.uploadConfirmed = true
            _status.update {
                it.copy(
                    phase = SyncPhase.IDLE,
                    pending = 0,
                    message = "首次上传完成",
                    lastSyncAt = System.currentTimeMillis()
                )
            }
        }.onFailure { e ->
            _status.update { it.copy(phase = SyncPhase.ERROR, message = friendly(e)) }
        }
    }

    suspend fun enqueueProject(id: String, op: String) = withContext(Dispatchers.IO) {
        if (!signedIn() || !tokens.uploadConfirmed) return@withContext
        val entity = projectDao.findByIdAny(id) ?: return@withContext
        outbox.enqueue(
            SyncOutboxEntity(
                mutationId = UUID.randomUUID().toString(),
                entity = SyncOutboxEntity.ENTITY_PROJECT,
                recordId = id,
                op = op,
                payload = projectJson(entity).toString(),
                baseRevision = (entity.revision - 1).coerceAtLeast(0),
                createdAt = System.currentTimeMillis()
            )
        )
        refreshPending()
        syncNow()
    }

    suspend fun enqueueSession(id: String, op: String) = withContext(Dispatchers.IO) {
        if (!signedIn() || !tokens.uploadConfirmed) return@withContext
        val entity = sessionDao.findById(id) ?: return@withContext
        outbox.enqueue(
            SyncOutboxEntity(
                mutationId = UUID.randomUUID().toString(),
                entity = SyncOutboxEntity.ENTITY_SESSION,
                recordId = id,
                op = op,
                payload = sessionJson(entity, repSetDao.forSession(id)).toString(),
                baseRevision = (entity.revision - 1).coerceAtLeast(0),
                createdAt = System.currentTimeMillis()
            )
        )
        refreshPending()
        syncNow()
    }

    suspend fun syncNow(): Result<Unit> = withContext(Dispatchers.IO) {
        if (!signedIn()) return@withContext Result.failure(IllegalStateException("未登录"))
        val stored = readConflicts()
        _status.update {
            it.copy(phase = SyncPhase.SYNCING, message = "同步中", conflictItems = stored)
        }
        runCatching {
            refreshTokenIfNeeded()
            drainOutbox()
            pull("project")
            pull("session")
            val conflicts = readConflicts()
            _status.update {
                it.copy(
                    phase = if (conflicts.isNotEmpty()) SyncPhase.ERROR else SyncPhase.IDLE,
                    pending = outbox.count(),
                    conflictItems = conflicts,
                    message = if (conflicts.isNotEmpty()) {
                        "${conflicts.size} 条改动与云端冲突，已保留本机版本"
                    } else {
                        "已同步"
                    },
                    lastSyncAt = System.currentTimeMillis()
                )
            }
        }.onFailure { e ->
            val conflicts = readConflicts()
            _status.update {
                it.copy(
                    phase = SyncPhase.ERROR,
                    pending = outbox.count(),
                    conflictItems = conflicts,
                    message = friendly(e)
                )
            }
        }
    }

    private suspend fun drainOutbox(): Int {
        val items = outbox.getAll()
        var conflicts = 0
        for (item in items) {
            val entity = when (item.entity) {
                SyncOutboxEntity.ENTITY_PROJECT -> projectDao.findByIdAny(item.recordId)
                else -> sessionDao.findById(item.recordId)
            } ?: run { outbox.delete(item.mutationId); continue }

            val result = when (item.entity) {
                SyncOutboxEntity.ENTITY_PROJECT -> runCatching {
                    pushProject(entity as ProjectEntity, item.op, item.baseRevision)
                }
                else -> runCatching {
                    val session = entity as SessionEntity
                    pushSession(session, repSetDao.forSession(session.id), item.op, item.baseRevision)
                }
            }
            result.onSuccess {
                outbox.delete(item.mutationId)
            }.onFailure { e ->
                if (e is SyncConflict) {
                    conflicts++
                    outbox.markFailed(item.mutationId, "冲突：云端版本已变化")
                    recordConflict(item.entity, item.recordId)
                } else {
                    outbox.markFailed(item.mutationId, e.message)
                    throw e
                }
            }
        }
        return conflicts
    }

    private suspend fun pushProject(project: ProjectEntity, op: String, baseRevision: Int) {
        val table = projectTable
        if (baseRevision == 0) {
            val body = JSONObject()
                .put("id", project.id)
                .put("revision", 1)
                .put("payload", projectJson(project))
            body.put("deleted_at", if (op == SyncOutboxEntity.OP_DELETE) Instant.now().toString() else JSONObject.NULL)
            val (code, text) = request("POST", "$rest/$table", accessToken(), body.toString(), representation = true)
            if (code == 409) throw SyncConflict()
            if (code !in 200..299) error(httpMessage(code, text))
        } else {
            val query = "?id=eq.${enc(project.id)}&revision=eq.$baseRevision"
            val body = JSONObject()
                .put("revision", baseRevision + 1)
                .put("payload", projectJson(project))
            body.put("deleted_at", if (op == SyncOutboxEntity.OP_DELETE) Instant.now().toString() else JSONObject.NULL)
            val (code, text) = request("PATCH", "$rest/$table$query", accessToken(), body.toString(), representation = true)
            if (code !in 200..299) error(httpMessage(code, text))
            if (JSONArray(text).length() == 0) throw SyncConflict()
        }
    }

    private suspend fun pushSession(session: SessionEntity, sets: List<RepSetEntity>, op: String, baseRevision: Int) {
        val table = sessionTable
        val payload = sessionJson(session, sets)
        if (baseRevision == 0) {
            val body = JSONObject().put("id", session.id).put("revision", 1).put("payload", payload)
            body.put("deleted_at", if (op == SyncOutboxEntity.OP_DELETE) Instant.now().toString() else JSONObject.NULL)
            val (code, text) = request("POST", "$rest/$table", accessToken(), body.toString(), representation = true)
            if (code == 409) throw SyncConflict()
            if (code !in 200..299) error(httpMessage(code, text))
        } else {
            val query = "?id=eq.${enc(session.id)}&revision=eq.$baseRevision"
            val body = JSONObject().put("revision", baseRevision + 1).put("payload", payload)
            body.put("deleted_at", if (op == SyncOutboxEntity.OP_DELETE) Instant.now().toString() else JSONObject.NULL)
            val (code, text) = request("PATCH", "$rest/$table$query", accessToken(), body.toString(), representation = true)
            if (code !in 200..299) error(httpMessage(code, text))
            if (JSONArray(text).length() == 0) throw SyncConflict()
        }
    }

    private fun tableFor(entity: String): String? = when (entity) {
        SyncOutboxEntity.ENTITY_PROJECT -> projectTable
        SyncOutboxEntity.ENTITY_SESSION -> sessionTable
        else -> null
    }

    private suspend fun readConflicts(): List<SyncConflictItem> {
        val raw = stateDao.get(CONFLICT_KEY).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val rows = JSONArray(raw)
            val list = ArrayList<SyncConflictItem>(rows.length())
            for (i in 0 until rows.length()) {
                val o = rows.getJSONObject(i)
                list += SyncConflictItem(
                    entity = o.optString("entity"),
                    recordId = o.optString("recordId"),
                    remoteRevision = o.optInt("remoteRevision", 0),
                    localPayload = o.optString("localPayload", "{}"),
                    remotePayload = o.optString("remotePayload", "{}"),
                    remoteDeletedAt = o.optString("remoteDeletedAt")
                        .takeIf { it.isNotBlank() && it != "null" }
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    private suspend fun writeConflicts(items: List<SyncConflictItem>) {
        val rows = JSONArray()
        items.forEach { c ->
            rows.put(
                JSONObject()
                    .put("entity", c.entity)
                    .put("recordId", c.recordId)
                    .put("remoteRevision", c.remoteRevision)
                    .put("localPayload", c.localPayload)
                    .put("remotePayload", c.remotePayload)
                    .put("remoteDeletedAt", c.remoteDeletedAt ?: JSONObject.NULL)
            )
        }
        stateDao.put(SyncStateEntity(CONFLICT_KEY, rows.toString()))
        _status.update { it.copy(conflictItems = items) }
    }

    private suspend fun removeConflict(entity: String, recordId: String) {
        val remaining = readConflicts().filterNot { it.entity == entity && it.recordId == recordId }
        writeConflicts(remaining)
    }

    private suspend fun deleteOutboxFor(entity: String, recordId: String) {
        outbox.getAll()
            .filter { it.entity == entity && it.recordId == recordId }
            .forEach { outbox.delete(it.mutationId) }
    }

    private suspend fun localPayload(entity: String, recordId: String): JSONObject? = when (entity) {
        SyncOutboxEntity.ENTITY_PROJECT -> projectDao.findByIdAny(recordId)?.let { projectJson(it) }
        SyncOutboxEntity.ENTITY_SESSION -> sessionDao.findById(recordId)
            ?.let { sessionJson(it, repSetDao.forSession(it.id)) }
        else -> null
    }

    private suspend fun localDeletedAt(entity: String, recordId: String): Long? = when (entity) {
        SyncOutboxEntity.ENTITY_PROJECT -> projectDao.findByIdAny(recordId)?.deletedAt
        SyncOutboxEntity.ENTITY_SESSION -> sessionDao.findById(recordId)?.deletedAt
        else -> null
    }

    private suspend fun fetchRemoteRow(entity: String, recordId: String): JSONObject? {
        val table = tableFor(entity) ?: error("未知的同步实体")
        val query = "?select=id,revision,deleted_at,payload&id=eq.${enc(recordId)}&limit=1"
        val (code, text) = request("GET", "$rest/$table$query", accessToken())
        if (code !in 200..299) error(httpMessage(code, text))
        val rows = JSONArray(text)
        return if (rows.length() > 0) rows.getJSONObject(0) else null
    }

    private fun rowPayload(row: JSONObject): JSONObject {
        row.optJSONObject("payload")?.let { return it }
        val raw = row.optString("payload")
        return if (raw.isBlank()) JSONObject() else runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
    }

    private suspend fun recordConflict(entity: String, recordId: String) {
        val local = localPayload(entity, recordId)?.toString() ?: return
        val row = runCatching { fetchRemoteRow(entity, recordId) }.getOrNull()
        val remoteDeleted = row?.optString("deleted_at")?.takeIf { it.isNotBlank() && it != "null" }
        val item = SyncConflictItem(
            entity = entity,
            recordId = recordId,
            remoteRevision = row?.optInt("revision", 1) ?: 0,
            localPayload = local,
            remotePayload = row?.let { rowPayload(it).toString() } ?: "{}",
            remoteDeletedAt = remoteDeleted
        )
        val merged = readConflicts()
            .filterNot { it.entity == entity && it.recordId == recordId } + item
        writeConflicts(merged)
    }

    suspend fun resolveKeepLocal(entity: String, recordId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            requireSignedIn()
            val table = tableFor(entity) ?: error("未知的同步实体")
            val row = fetchRemoteRow(entity, recordId) ?: error("云端记录不存在")
            val remoteRev = row.optInt("revision", 1)
            val payload = localPayload(entity, recordId) ?: error("本机记录不存在")
            val deletedAtMs = localDeletedAt(entity, recordId)
            val body = JSONObject()
                .put("revision", remoteRev + 1)
                .put("payload", payload)
            body.put("deleted_at", deletedAtMs?.let { Instant.ofEpochMilli(it).toString() } ?: JSONObject.NULL)
            val query = "?id=eq.${enc(recordId)}&revision=eq.$remoteRev"
            val (code, text) = request("PATCH", "$rest/$table$query", accessToken(), body.toString(), representation = true)
            if (code == 409) throw SyncConflict()
            if (code !in 200..299) error(httpMessage(code, text))
            if (JSONArray(text).length() == 0) throw SyncConflict()
            val newRevision = remoteRev + 1
            when (entity) {
                SyncOutboxEntity.ENTITY_PROJECT -> projectDao.findByIdAny(recordId)
                    ?.let { projectDao.upsert(it.copy(revision = newRevision)) }
                else -> sessionDao.findById(recordId)
                    ?.let { sessionDao.upsert(it.copy(revision = newRevision)) }
            }
            removeConflict(entity, recordId)
            deleteOutboxFor(entity, recordId)
            _status.update { it.copy(pending = outbox.count()) }
            syncNow()
            Unit
        }
    }

    suspend fun resolveUseCloud(entity: String, recordId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            requireSignedIn()
            if (tableFor(entity) == null) error("未知的同步实体")
            val row = fetchRemoteRow(entity, recordId) ?: error("云端记录不存在")
            val revision = row.optInt("revision", 1)
            val deleted = row.optString("deleted_at").takeIf { it.isNotBlank() && it != "null" }
            val payload = rowPayload(row)
            when (entity) {
                SyncOutboxEntity.ENTITY_PROJECT -> projectDao.upsert(projectFromJson(payload, revision, deleted))
                else -> {
                    val session = sessionFromJson(payload, revision, deleted)
                    db.withTransaction {
                        sessionDao.upsert(session)
                        repSetDao.deleteForSession(recordId)
                        val sets = payload.optJSONArray("sets") ?: JSONArray()
                        val list = ArrayList<RepSetEntity>()
                        for (j in 0 until sets.length()) {
                            val s = sets.getJSONObject(j)
                            list += RepSetEntity(
                                id = s.optString("id", "$recordId:${s.optInt("ordinal")}"),
                                sessionId = recordId,
                                ordinal = s.optInt("ordinal"),
                                actualReps = s.optInt("actualReps")
                            )
                        }
                        repSetDao.upsertAll(list)
                    }
                }
            }
            removeConflict(entity, recordId)
            deleteOutboxFor(entity, recordId)
            _status.update { it.copy(pending = outbox.count()) }
        }
    }

    private suspend fun pull(kind: String) {
        val table = if (kind == "project") projectTable else sessionTable
        val cursorKey = if (kind == "project") "cursor_projects" else "cursor_sessions"
        val cursor = stateDao.get(cursorKey).orEmpty()
        val query = buildString {
            append("?select=id,revision,deleted_at,payload,server_updated_at")
            append("&order=server_updated_at.asc")
            if (cursor.isNotBlank()) append("&server_updated_at=gt.${enc(cursor)}")
        }
        val (code, text) = request("GET", "$rest/$table$query", accessToken())
        if (code !in 200..299) error(httpMessage(code, text))
        val rows = JSONArray(text)
        var maxCursor = cursor
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val id = row.getString("id")
            val remoteRevision = row.optInt("revision", 1)
            val pending = outbox.getAll().any { it.recordId == id }
            if (pending) continue
            if (kind == "project") {
                val local = projectDao.findByIdAny(id)
                if (local != null && local.revision > remoteRevision) continue
                if (local != null && local.revision == remoteRevision) continue
                val payload = row.optJSONObject("payload") ?: JSONObject()
                val deleted = row.optString("deleted_at").takeIf { it.isNotBlank() && it != "null" }
                projectDao.upsert(projectFromJson(payload, remoteRevision, deleted))
            } else {
                val local = sessionDao.findById(id)
                if (local != null && local.revision > remoteRevision) continue
                if (local != null && local.revision == remoteRevision) continue
                val payload = row.optJSONObject("payload") ?: JSONObject()
                val deleted = row.optString("deleted_at").takeIf { it.isNotBlank() && it != "null" }
                val session = sessionFromJson(payload, remoteRevision, deleted)
                db.withTransaction {                    sessionDao.upsert(session)
                    repSetDao.deleteForSession(id)
                    val sets = payload.optJSONArray("sets") ?: JSONArray()
                    val list = ArrayList<RepSetEntity>()
                    for (j in 0 until sets.length()) {
                        val s = sets.getJSONObject(j)
                        list += RepSetEntity(
                            id = s.optString("id", "$id:${s.optInt("ordinal")}"),
                            sessionId = id,
                            ordinal = s.optInt("ordinal"),
                            actualReps = s.optInt("actualReps")
                        )
                    }
                    repSetDao.upsertAll(list)
                }
            }
            val su = row.optString("server_updated_at")
            if (su > maxCursor) maxCursor = su
        }
        if (maxCursor.isNotBlank()) stateDao.put(SyncStateEntity(cursorKey, maxCursor))
    }

    private suspend fun refreshTokenIfNeeded() {
        val access = tokens.accessToken ?: throw IllegalStateException("未登录")
        val (code, _) = request("GET", "$rest/$projectTable?select=id&limit=1", access)
        if (code == 401) {
            val refresh = tokens.refreshToken ?: throw IllegalStateException("登录已过期，请重新登录")
            val (rc, rt) = request(
                "POST", "$base/auth/v1/token", BuildConfig.CLOUDBASE_PUBLISHABLE_KEY,
                JSONObject().put("grant_type", "refresh_token").put("refresh_token", refresh).toString()
            )
            if (rc !in 200..299) throw IllegalStateException("登录已过期，请重新登录")
            val json = JSONObject(rt)
            tokens.save(
                json.getString("access_token"),
                json.optString("refresh_token", refresh),
                json.optString("sub", tokens.account),
                tokens.account ?: "",
                tokens.uploadConfirmed
            )
        }
    }

    private fun signedIn(): Boolean = tokens.accessToken != null
    private fun accessToken(): String = tokens.accessToken ?: throw IllegalStateException("未登录")

    private fun requireSignedIn() {
        if (!signedIn()) throw IllegalStateException("未登录")
    }

    private fun request(
        method: String,
        url: String,
        token: String?,
        body: String? = null,
        representation: Boolean = false
    ): Pair<Int, String> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
            if (representation) setRequestProperty("Prefer", "return=representation")
        }
        return try {
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            code to text
        } finally {
            conn.disconnect()
        }
    }

    private fun httpMessage(code: Int, text: String): String {
        val hint = when (code) {
            401 -> "登录已过期"
            403 -> "没有权限"
            404 -> "云端表不存在，请先建表"
            409 -> "云端已有同一条记录"
            else -> "网络或云端错误"
        }
        return "$hint（HTTP $code）"
    }

    private fun friendly(e: Throwable): String = when (e) {
        is java.net.UnknownHostException, is java.net.ConnectException ->
            "网络不可用，请检查网络后重试"
        is java.net.SocketTimeoutException -> "网络超时，请稍后重试"
        is javax.net.ssl.SSLException -> "安全连接失败，请检查网络"
        else -> e.message?.takeIf { it.isNotBlank() } ?: "同步失败"
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun projectJson(p: ProjectEntity): JSONObject = JSONObject()
        .put("id", p.id)
        .put("name", p.name)
        .put("defaultTargetSec", p.defaultTargetSec)
        .put("presetsSec", p.presetsSec)
        .put("archivedAt", p.archivedAt)
        .put("createdAt", p.createdAt)
        .put("updatedAt", p.updatedAt)
        .put("mode", p.mode)
        .put("plannedSets", p.plannedSets)
        .put("targetRepsPerSet", p.targetRepsPerSet)
        .put("revision", p.revision)
        .put("deletedAt", p.deletedAt)

    private fun sessionJson(s: SessionEntity, sets: List<RepSetEntity>): JSONObject {
        val arr = JSONArray()
        sets.forEach {
            arr.put(JSONObject().put("id", it.id).put("ordinal", it.ordinal).put("actualReps", it.actualReps))
        }
        return JSONObject()
            .put("id", s.id)
            .put("projectId", s.projectId)
            .put("startedAt", s.startedAt)
            .put("endedAt", s.endedAt)
            .put("targetSec", s.targetSec)
            .put("actualSec", s.actualSec)
            .put("pauseCount", s.pauseCount)
            .put("pausedSec", s.pausedSec)
            .put("source", s.source)
            .put("note", s.note)
            .put("createdAt", s.createdAt)
            .put("updatedAt", s.updatedAt)
            .put("deletedAt", s.deletedAt)
            .put("mode", s.mode)
            .put("plannedSets", s.plannedSets)
            .put("targetRepsPerSet", s.targetRepsPerSet)
            .put("revision", s.revision)
            .put("sets", arr)
    }

    private fun projectFromJson(j: JSONObject, revision: Int, deletedAt: String?): ProjectEntity =
        ProjectEntity(
            id = j.getString("id"),
            name = j.optString("name"),
            defaultTargetSec = j.optInt("defaultTargetSec", 900),
            presetsSec = j.optString("presetsSec", "600,900,1200,1500"),
            archivedAt = j.optLongOrNull("archivedAt"),
            createdAt = j.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = j.optLong("updatedAt", System.currentTimeMillis()),
            mode = j.optString("mode", ProjectEntity.MODE_TIME),
            plannedSets = j.optInt("plannedSets", 3),
            targetRepsPerSet = j.optInt("targetRepsPerSet", 10),
            revision = revision,
            deletedAt = deletedAt?.let { Instant.parse(it).toEpochMilli() }
        )

    private fun sessionFromJson(j: JSONObject, revision: Int, deletedAt: String?): SessionEntity =
        SessionEntity(
            id = j.getString("id"),
            projectId = j.getString("projectId"),
            startedAt = j.optLongOrNull("startedAt"),
            endedAt = j.optLong("endedAt", System.currentTimeMillis()),
            targetSec = j.optInt("targetSec"),
            actualSec = j.optInt("actualSec"),
            pauseCount = j.optInt("pauseCount"),
            pausedSec = j.optInt("pausedSec"),
            source = j.optString("source", SessionEntity.SOURCE_TIMER),
            note = j.optString("note"),
            createdAt = j.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = j.optLong("updatedAt", System.currentTimeMillis()),
            deletedAt = deletedAt?.let { Instant.parse(it).toEpochMilli() } ?: j.optLongOrNull("deletedAt"),
            mode = j.optString("mode", SessionEntity.MODE_TIME),
            plannedSets = j.optInt("plannedSets", 3),
            targetRepsPerSet = j.optInt("targetRepsPerSet", 10),
            revision = revision
        )

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (isNull(key)) null else optLong(key)

    private class SyncConflict : Exception("云端版本已变化")

    private class TokenStore(context: Context) {
        private val prefs: SharedPreferences = runCatching {
            val key = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "p068_sync_tokens",
                key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrElse {
            context.getSharedPreferences("p068_sync_tokens_plain", Context.MODE_PRIVATE)
        }

        val accessToken: String? get() = prefs.getString("access", null)
        val refreshToken: String? get() = prefs.getString("refresh", null)
        val account: String? get() = prefs.getString("account", null)
        var uploadConfirmed: Boolean
            get() = prefs.getBoolean("upload_confirmed", false)
            set(value) { prefs.edit().putBoolean("upload_confirmed", value).apply() }

        fun save(access: String, refresh: String, sub: String, account: String, uploadConfirmed: Boolean) {
            prefs.edit()
                .putString("access", access)
                .putString("refresh", refresh)
                .putString("sub", sub)
                .putString("account", account)
                .putBoolean("upload_confirmed", uploadConfirmed)
                .apply()
        }

        fun clear() {
            prefs.edit().clear().apply()
        }
    }
}
