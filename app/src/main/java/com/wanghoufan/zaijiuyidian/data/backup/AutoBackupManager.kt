package com.wanghoufan.zaijiuyidian.data.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.wanghoufan.zaijiuyidian.BuildConfig
import com.wanghoufan.zaijiuyidian.data.db.AppDatabase
import com.wanghoufan.zaijiuyidian.data.prefs.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class BackupSource { AUTO, SAFETY }

data class BackupEntry(
    val uri: Uri,
    val name: String,
    val source: BackupSource,
    val timeMs: Long,
    val version: Int,
    val projectCount: Int,
    val sessionCount: Int,
    val deletedCount: Int,
    val sizeBytes: Long,
    val valid: Boolean,
    val error: String? = null
)

/**
 * SAF 自动备份：串行、合并、幂等；只轮转 zjyd-auto-*，绝不删除手动文件或安全回退点。
 */
class AutoBackupManager(
    private val context: Context,
    private val db: AppDatabase,
    private val backupRepo: BackupRepository,
    private val settingsRepo: SettingsRepository
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    @Volatile private var scheduled = false
    @Volatile private var restoring = false
    @Volatile private var pendingAfterRestore = false

    fun requestBackup() {
        if (restoring) {
            pendingAfterRestore = true
            return
        }
        if (scheduled) return
        scheduled = true
        scope.launch {
            delay(1200)
            scheduled = false
            performBackup()
        }
    }

    suspend fun performBackup(): Boolean = mutex.withLock {
        if (restoring) {
            pendingAfterRestore = true
            return@withLock false
        }
        val settings = settingsRepo.settings.first()
        val tree = settings.autoBackupTreeUri?.let(Uri::parse) ?: return@withLock false
        if (!settings.autoBackupEnabled) return@withLock false
        try {
            requireWritable(tree)
            val json = backupRepo.exportJson(BuildConfig.VERSION_NAME, settings)
            val uri = writeVerified(tree, "zjyd-auto-${stamp()}.json", json)
            settingsRepo.setAutoBackupLastSuccess(System.currentTimeMillis())
            runCatching { rotate(tree) }.onFailure {
                settingsRepo.setAutoBackupError("备份成功，但清理旧版本失败：${friendly(it)}")
            }
            true
        } catch (e: Exception) {
            settingsRepo.setAutoBackupError(friendly(e))
            false
        }
    }

    /** 覆盖恢复前调用；调用方必须已持有 restore mutex，避免不可重入死锁。 */
    suspend fun writeSafetySnapshot(): Uri? {
        val settings = settingsRepo.settings.first()
        val tree = settings.autoBackupTreeUri?.let(Uri::parse)
        return try {
            android.util.Log.i("P068Backup", "safety: tree=$tree write=${tree?.let { hasWritePermission(it) }}")
            val json = backupRepo.exportJson(BuildConfig.VERSION_NAME, settings)
            val uri = if (tree != null && hasWritePermission(tree)) {
                writeVerified(tree, "zjyd-safety-${stamp()}.json", json)
            } else {
                writePrivateSafety(json)
            }
            android.util.Log.i("P068Backup", "safety ok: $uri")
            uri
        } catch (e: Exception) {
            android.util.Log.e("P068Backup", "safety failed", e)
            settingsRepo.setAutoBackupError(friendly(e))
            null
        }
    }

    suspend fun <T> withRestoreLock(block: suspend () -> T): T = mutex.withLock {
        restoring = true
        try {
            block()
        } finally {
            restoring = false
            if (pendingAfterRestore) {
                pendingAfterRestore = false
                requestBackup()
            }
        }
    }

    suspend fun listEntries(): List<BackupEntry> {
        val settings = settingsRepo.settings.first()
        val tree = settings.autoBackupTreeUri?.let(Uri::parse)
        if (tree == null || !hasWritePermission(tree)) {
            android.util.Log.i("P068Backup", "list: no writable tree; private safety only")
            return legacyPrivateSafety().sortedByDescending { it.timeMs }
        }
        return try {
            val children = queryChildren(tree)
            android.util.Log.i("P068Backup", "list: tree=$tree children=${children.size} names=${children.map { it.first }}")
            val remote = children
                .filter { it.first.startsWith("zjyd-auto-") || it.first.startsWith("zjyd-safety-") }
                .map { (name, docId, modified) ->
                    val uri = DocumentsContract.buildDocumentUriUsingTree(tree, docId)
                    val source = if (name.startsWith("zjyd-safety-")) BackupSource.SAFETY else BackupSource.AUTO
                    runCatching {
                        val text = readText(uri)
                        val data = BackupCodec.parse(text).data
                        val exported = runCatching {
                            java.time.OffsetDateTime.parse(data.exportedAt).toInstant().toEpochMilli()
                        }.getOrDefault(modified)
                        BackupEntry(
                            uri = uri,
                            name = name,
                            source = source,
                            timeMs = exported,
                            version = data.version,
                            projectCount = data.projects.size,
                            sessionCount = data.sessions.count { it.deletedAt == null },
                            deletedCount = data.sessions.count { it.deletedAt != null },
                            sizeBytes = text.toByteArray(Charsets.UTF_8).size.toLong(),
                            valid = true
                        )
                    }.getOrElse {
                        BackupEntry(
                            uri = uri,
                            name = name,
                            source = source,
                            timeMs = modified,
                            version = 0,
                            projectCount = 0,
                            sessionCount = 0,
                            deletedCount = 0,
                            sizeBytes = 0,
                            valid = false,
                            error = friendly(it)
                        )
                    }
                }
            val legacy = legacyPrivateSafety()
            (remote + legacy)
                .sortedByDescending { it.timeMs }
                .also { android.util.Log.i("P068Backup", "list: entries=${it.size}") }
        } catch (e: Exception) {
            android.util.Log.e("P068Backup", "list failed", e)
            settingsRepo.setAutoBackupError(friendly(e))
            emptyList()
        }
    }

    private fun hasWritePermission(tree: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any {
            it.uri == tree && it.isWritePermission
        }

    private fun requireWritable(tree: Uri) {
        if (!hasWritePermission(tree)) {
            throw IOException("目录授权已失效，请重新选择备份文件夹")
        }
    }

    private fun writePrivateSafety(json: String): Uri {
        val dir = java.io.File(context.filesDir, "safety-backups").apply { mkdirs() }
        val file = java.io.File(dir, "safety-${stamp()}.json")
        file.writeText(json)
        BackupCodec.parse(file.readText())
        return Uri.fromFile(file)
    }

    private fun writeVerified(tree: Uri, name: String, json: String): Uri {
        val resolver = context.contentResolver
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
        val uri = DocumentsContract.createDocument(resolver, parent, "application/json", name)
            ?: throw IOException("无法在所选目录创建备份文件")
        resolver.openOutputStream(uri, "w")?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            ?: throw IOException("无法写入备份文件")
        val readBack = readText(uri)
        BackupCodec.parse(readBack)
        return uri
    }

    private fun legacyPrivateSafety(): List<BackupEntry> {
        val dir = java.io.File(context.filesDir, "safety-backups")
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            .orEmpty()
            .map { file ->
                runCatching {
                    val text = file.readText()
                    val data = BackupCodec.parse(text).data
                    BackupEntry(
                        uri = Uri.fromFile(file),
                        name = file.name,
                        source = BackupSource.SAFETY,
                        timeMs = file.lastModified(),
                        version = data.version,
                        projectCount = data.projects.size,
                        sessionCount = data.sessions.count { it.deletedAt == null },
                        deletedCount = data.sessions.count { it.deletedAt != null },
                        sizeBytes = file.length(),
                        valid = true
                    )
                }.getOrElse {
                    BackupEntry(
                        uri = Uri.fromFile(file),
                        name = file.name,
                        source = BackupSource.SAFETY,
                        timeMs = file.lastModified(),
                        version = 0,
                        projectCount = 0,
                        sessionCount = 0,
                        deletedCount = 0,
                        sizeBytes = file.length(),
                        valid = false,
                        error = friendly(it)
                    )
                }
            }
    }

    private fun readText(uri: Uri): String {
        val bytes = if (uri.scheme == "file") {
            val path = uri.path ?: throw IOException("无法读取备份文件")
            java.io.File(path).readBytes()
        } else {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } ?: throw IOException("无法读取备份文件")
        if (bytes.size > BackupCodec.MAX_FILE_BYTES) throw IOException("备份文件超过 15MB")
        return bytes.toString(Charsets.UTF_8)
    }

    private fun queryChildren(tree: Uri): List<Triple<String, String, Long>> {
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId)
        val result = mutableListOf<Triple<String, String, Long>>()
        context.contentResolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            ),
            null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                val modified = if (cursor.isNull(2)) 0L else cursor.getLong(2)
                result.add(Triple(name, id, modified))
            }
        }
        return result
    }

    private fun rotate(tree: Uri) {
        val auto = queryChildren(tree)
            .filter { it.first.startsWith("zjyd-auto-") }
            .sortedByDescending { it.first }
        auto.drop(MAX_AUTO_BACKUPS).forEach { (_, docId, _) ->
            val uri = DocumentsContract.buildDocumentUriUsingTree(tree, docId)
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
        }
    }

    private fun stamp(): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    private fun friendly(e: Throwable): String = when (e) {
        is SecurityException -> "目录授权已失效，请重新选择备份文件夹"
        is IOException -> e.message ?: "备份失败"
        else -> "备份失败：${e.message ?: "未知错误"}"
    }

    companion object {
        const val MAX_AUTO_BACKUPS = 7
    }
}
