package com.wanghoufan.zaijiuyidian.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects WHERE deletedAt IS NULL ORDER BY archivedAt IS NOT NULL, createdAt ASC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id AND deletedAt IS NULL")
    suspend fun findById(id: String): ProjectEntity?

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun findByIdAny(id: String): ProjectEntity?

    @Query("SELECT * FROM projects WHERE deletedAt IS NULL")
    suspend fun getAll(): List<ProjectEntity>

    @Query("SELECT * FROM projects")
    suspend fun getAllIncludingDeleted(): List<ProjectEntity>

    @Query("SELECT COUNT(*) FROM sessions WHERE projectId = :projectId")
    suspend fun sessionCount(projectId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(project: ProjectEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(projects: List<ProjectEntity>)

    @Update
    suspend fun update(project: ProjectEntity)

    @Transaction
    suspend fun updateBumped(project: ProjectEntity) {
        val current = findByIdAny(project.id) ?: return
        update(project.copy(revision = current.revision + 1))
    }

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE projects SET deletedAt = :now, updatedAt = :now, revision = revision + 1 WHERE id = :id AND deletedAt IS NULL")
    suspend fun softDelete(id: String, now: Long)

    @Query("DELETE FROM projects")
    suspend fun deleteAll()
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE deletedAt IS NULL")
    fun observeAllLive(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions")
    suspend fun getAll(): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun findById(id: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE deletedAt IS NULL ORDER BY endedAt DESC, id DESC")
    suspend fun getLive(): List<SessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: SessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(sessions: List<SessionEntity>)

    @Transaction
    suspend fun upsertBumped(session: SessionEntity) {
        val current = findById(session.id)
        upsert(session.copy(revision = (current?.revision ?: 0) + 1))
    }

    @Query("UPDATE sessions SET deletedAt = :deletedAt, updatedAt = :deletedAt, revision = revision + 1 WHERE id = :id AND deletedAt IS NULL")
    suspend fun softDelete(id: String, deletedAt: Long)

    @Query("UPDATE sessions SET deletedAt = NULL, updatedAt = :now, revision = revision + 1 WHERE id = :id")
    suspend fun restore(id: String, now: Long)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("DELETE FROM sessions")
    suspend fun deleteAll()
}

@Dao
interface RepSetDao {
    @Query("SELECT * FROM rep_sets")
    fun observeAll(): Flow<List<RepSetEntity>>

    @Query("SELECT * FROM rep_sets")
    suspend fun getAll(): List<RepSetEntity>

    @Query("SELECT * FROM rep_sets WHERE sessionId = :sessionId ORDER BY ordinal ASC")
    suspend fun forSession(sessionId: String): List<RepSetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(sets: List<RepSetEntity>)

    @Query("DELETE FROM rep_sets WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: String)

    @Query("DELETE FROM rep_sets")
    suspend fun deleteAll()
}

@Dao
interface RepDraftDao {
    @Query("SELECT * FROM rep_draft WHERE id = 1")
    fun observe(): Flow<RepDraftEntity?>

    @Query("SELECT * FROM rep_draft WHERE id = 1")
    suspend fun get(): RepDraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(draft: RepDraftEntity)

    @Query("DELETE FROM rep_draft")
    suspend fun delete()
}

@Dao
interface ActiveSessionDao {    @Query("SELECT * FROM active_session WHERE id = 1")
    suspend fun get(): ActiveSessionEntity?

    @Query("SELECT * FROM active_session WHERE id = 1")
    fun observe(): Flow<ActiveSessionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: ActiveSessionEntity)

    @Query("DELETE FROM active_session")
    suspend fun delete()

    @Transaction
    suspend fun replace(session: ActiveSessionEntity?) {
        delete()
        if (session != null) upsert(session)
    }
}

@Dao
interface SyncOutboxDao {
    @Query("SELECT * FROM sync_outbox ORDER BY createdAt ASC")
    suspend fun getAll(): List<SyncOutboxEntity>

    @Query("SELECT COUNT(*) FROM sync_outbox")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_outbox")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(item: SyncOutboxEntity)

    @Query("DELETE FROM sync_outbox WHERE mutationId = :mutationId")
    suspend fun delete(mutationId: String)

    @Query("UPDATE sync_outbox SET attempts = attempts + 1, lastError = :error WHERE mutationId = :mutationId")
    suspend fun markFailed(mutationId: String, error: String?)

    @Query("DELETE FROM sync_outbox")
    suspend fun deleteAll()
}

@Dao
interface SyncStateDao {
    @Query("SELECT value FROM sync_state WHERE key = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(state: SyncStateEntity)

    @Query("DELETE FROM sync_state")
    suspend fun deleteAll()
}
