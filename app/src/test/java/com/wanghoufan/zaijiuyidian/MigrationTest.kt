package com.wanghoufan.zaijiuyidian

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wanghoufan.zaijiuyidian.data.db.AppDatabase
import com.wanghoufan.zaijiuyidian.data.db.SyncOutboxEntity
import com.wanghoufan.zaijiuyidian.data.db.SyncStateEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    private val dbName = "migration-v1-v2.db"
    private lateinit var context: Context
    private var db: AppDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        createV1Database()
    }

    @After
    fun tearDown() {
        db?.close()
        context.deleteDatabase(dbName)
    }

    private fun createV1Database() {
        val raw = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `projects` (" +
                "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `defaultTargetSec` INTEGER NOT NULL, " +
                "`presetsSec` TEXT NOT NULL, `archivedAt` INTEGER, `createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `sessions` (" +
                "`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `startedAt` INTEGER, " +
                "`endedAt` INTEGER NOT NULL, `targetSec` INTEGER NOT NULL, `actualSec` INTEGER NOT NULL, " +
                "`pauseCount` INTEGER NOT NULL, `pausedSec` INTEGER NOT NULL, `source` TEXT NOT NULL, " +
                "`note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "`deletedAt` INTEGER, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE RESTRICT)"
        )
        raw.execSQL("CREATE INDEX IF NOT EXISTS `index_sessions_projectId` ON `sessions` (`projectId`)")
        raw.execSQL("CREATE INDEX IF NOT EXISTS `index_sessions_endedAt` ON `sessions` (`endedAt`)")
        raw.execSQL("CREATE INDEX IF NOT EXISTS `index_sessions_deletedAt` ON `sessions` (`deletedAt`)")
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `active_session` (" +
                "`id` INTEGER NOT NULL, `projectId` TEXT NOT NULL, `targetSec` INTEGER NOT NULL, " +
                "`accumulatedMs` INTEGER NOT NULL, `segmentStartedElapsedMs` INTEGER, " +
                "`pausedAtElapsedMs` INTEGER, `pauseCount` INTEGER NOT NULL, `pausedMs` INTEGER NOT NULL, " +
                "`bootWallBase` INTEGER NOT NULL, `bootCount` INTEGER NOT NULL, " +
                "`startedAtWall` INTEGER NOT NULL, `notifiedAtTarget` INTEGER NOT NULL, " +
                "`lastKnownElapsedMs` INTEGER NOT NULL, `lastKnownAtWall` INTEGER NOT NULL, " +
                "`interrupted` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        raw.execSQL(
            "INSERT INTO projects (id, name, defaultTargetSec, presetsSec, archivedAt, createdAt, updatedAt) " +
                "VALUES ('p1', '平板支撑', 1500, '900,1200,1500', NULL, 1700000000000, 1700000000000)"
        )
        raw.execSQL(
            "INSERT INTO sessions (id, projectId, startedAt, endedAt, targetSec, actualSec, pauseCount, " +
                "pausedSec, source, note, createdAt, updatedAt, deletedAt) VALUES " +
                "('s1', 'p1', 1700000000000, 1700000600000, 1500, 1200, 1, 30, 'timer', '旧记录', " +
                "1700000600000, 1700000600000, NULL)"
        )
        raw.execSQL(
            "INSERT INTO active_session (id, projectId, targetSec, accumulatedMs, segmentStartedElapsedMs, " +
                "pausedAtElapsedMs, pauseCount, pausedMs, bootWallBase, bootCount, startedAtWall, " +
                "notifiedAtTarget, lastKnownElapsedMs, lastKnownAtWall, interrupted) VALUES " +
                "(1, 'p1', 1500, 120000, 500000, NULL, 0, 0, 1699999000000, 3, " +
                "1700000000000, 0, 120000, 1700000120000, 0)"
        )
        raw.version = 1
        raw.close()
    }

    private fun createV2Database() {
        context.deleteDatabase(dbName)
        val now = 1_700_000_000_000L
        val raw = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `projects` (" +
                "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `defaultTargetSec` INTEGER NOT NULL, " +
                "`presetsSec` TEXT NOT NULL, `archivedAt` INTEGER, `createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, `mode` TEXT NOT NULL DEFAULT 'TIME', " +
                "`plannedSets` INTEGER NOT NULL DEFAULT 3, " +
                "`targetRepsPerSet` INTEGER NOT NULL DEFAULT 10, PRIMARY KEY(`id`))"
        )
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `sessions` (" +
                "`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `startedAt` INTEGER, " +
                "`endedAt` INTEGER NOT NULL, `targetSec` INTEGER NOT NULL, `actualSec` INTEGER NOT NULL, " +
                "`pauseCount` INTEGER NOT NULL, `pausedSec` INTEGER NOT NULL, `source` TEXT NOT NULL, " +
                "`note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "`deletedAt` INTEGER, `mode` TEXT NOT NULL DEFAULT 'TIME', " +
                "`plannedSets` INTEGER NOT NULL DEFAULT 3, " +
                "`targetRepsPerSet` INTEGER NOT NULL DEFAULT 10, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE RESTRICT)"
        )
        raw.execSQL("CREATE INDEX IF NOT EXISTS `index_sessions_projectId` ON `sessions` (`projectId`)")
        raw.execSQL("CREATE INDEX IF NOT EXISTS `index_sessions_endedAt` ON `sessions` (`endedAt`)")
        raw.execSQL("CREATE INDEX IF NOT EXISTS `index_sessions_deletedAt` ON `sessions` (`deletedAt`)")
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `active_session` (" +
                "`id` INTEGER NOT NULL, `projectId` TEXT NOT NULL, `targetSec` INTEGER NOT NULL, " +
                "`accumulatedMs` INTEGER NOT NULL, `segmentStartedElapsedMs` INTEGER, " +
                "`pausedAtElapsedMs` INTEGER, `pauseCount` INTEGER NOT NULL, `pausedMs` INTEGER NOT NULL, " +
                "`bootWallBase` INTEGER NOT NULL, `bootCount` INTEGER NOT NULL, " +
                "`startedAtWall` INTEGER NOT NULL, `notifiedAtTarget` INTEGER NOT NULL, " +
                "`lastKnownElapsedMs` INTEGER NOT NULL, `lastKnownAtWall` INTEGER NOT NULL, " +
                "`interrupted` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `rep_sets` (" +
                "`id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `ordinal` INTEGER NOT NULL, " +
                "`actualReps` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        raw.execSQL("CREATE INDEX IF NOT EXISTS `index_rep_sets_sessionId` ON `rep_sets` (`sessionId`)")
        raw.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_rep_sets_sessionId_ordinal` " +
                "ON `rep_sets` (`sessionId`, `ordinal`)"
        )
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `rep_draft` (" +
                "`id` INTEGER NOT NULL, `projectId` TEXT NOT NULL, " +
                "`plannedSets` INTEGER NOT NULL, `targetRepsPerSet` INTEGER NOT NULL, " +
                "`setsCsv` TEXT NOT NULL, `nextReps` INTEGER NOT NULL, " +
                "`touchedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        raw.execSQL(
            "INSERT INTO projects (id, name, defaultTargetSec, presetsSec, archivedAt, createdAt, " +
                "updatedAt, mode, plannedSets, targetRepsPerSet) VALUES " +
                "('p2', '引体向上', 900, '600,900', NULL, $now, $now, 'REPS', 4, 8)"
        )
        raw.execSQL(
            "INSERT INTO sessions (id, projectId, startedAt, endedAt, targetSec, actualSec, pauseCount, " +
                "pausedSec, source, note, createdAt, updatedAt, deletedAt, mode, plannedSets, " +
                "targetRepsPerSet) VALUES ('s2', 'p2', $now, ${now + 600_000}, 900, 600, 0, 0, " +
                "'reps', 'V2 记录', ${now + 600_000}, ${now + 600_000}, NULL, 'REPS', 4, 8)"
        )
        raw.execSQL(
            "INSERT INTO rep_sets (id, sessionId, ordinal, actualReps) VALUES ('r1', 's2', 1, 8)"
        )
        raw.version = 2
        raw.close()
    }

    @Test
    fun v2_to_v3_adds_revision_soft_delete_and_sync_tables() = runBlocking {
        createV2Database()
        db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        val project = db!!.projectDao().findByIdAny("p2")
        assertNotNull(project)
        assertEquals("引体向上", project!!.name)
        assertEquals("REPS", project.mode)
        assertEquals(4, project.plannedSets)
        assertEquals(1, project.revision)
        assertNull(project.deletedAt)

        val session = db!!.sessionDao().findById("s2")
        assertNotNull(session)
        assertEquals(600, session!!.actualSec)
        assertEquals("V2 记录", session.note)
        assertEquals(1, session.revision)
        assertNull(session.deletedAt)

        val repSets = db!!.repSetDao().getAll()
        assertEquals(1, repSets.size)
        assertEquals(8, repSets[0].actualReps)
        assertNull(repSets[0].deletedAt)

        db!!.syncOutboxDao().enqueue(
            SyncOutboxEntity(
                mutationId = "m-after-migration",
                entity = SyncOutboxEntity.ENTITY_SESSION,
                recordId = "s2",
                op = SyncOutboxEntity.OP_UPSERT,
                payload = """{"id":"s2"}""",
                baseRevision = 1,
                createdAt = 1_700_000_100_000L
            )
        )
        assertEquals(1, db!!.syncOutboxDao().count())

        db!!.syncStateDao().put(SyncStateEntity(key = "lastPullAt", value = "1700000100000"))
        assertEquals("1700000100000", db!!.syncStateDao().get("lastPullAt"))
    }

    @Test
    fun v1_to_v3_keeps_existing_time_data_and_adds_reps_tables() = runBlocking {
        db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        val project = db!!.projectDao().findById("p1")
        assertNotNull(project)
        assertEquals("TIME", project!!.mode)
        assertEquals(3, project.plannedSets)
        assertEquals(10, project.targetRepsPerSet)
        assertEquals(1, project.revision)
        assertNull(project.deletedAt)

        val sessions = db!!.sessionDao().getAll()
        assertEquals(1, sessions.size)
        assertEquals("TIME", sessions[0].mode)
        assertEquals(1200, sessions[0].actualSec)
        assertEquals("旧记录", sessions[0].note)
        assertEquals(1, sessions[0].revision)

        val active = db!!.activeSessionDao().get()
        assertNotNull(active)
        assertEquals(120000L, active!!.accumulatedMs)

        assertTrue(db!!.repSetDao().getAll().isEmpty())
        assertFalse(db!!.repDraftDao().get() != null)
    }
}
