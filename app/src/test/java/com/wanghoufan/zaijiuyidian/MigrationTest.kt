package com.wanghoufan.zaijiuyidian

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wanghoufan.zaijiuyidian.data.db.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    @Test
    fun v1_to_v2_keeps_existing_time_data_and_adds_reps_tables() = runBlocking {
        db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

        val project = db!!.projectDao().findById("p1")
        assertNotNull(project)
        assertEquals("TIME", project!!.mode)
        assertEquals(3, project.plannedSets)
        assertEquals(10, project.targetRepsPerSet)

        val sessions = db!!.sessionDao().getAll()
        assertEquals(1, sessions.size)
        assertEquals("TIME", sessions[0].mode)
        assertEquals(1200, sessions[0].actualSec)
        assertEquals("旧记录", sessions[0].note)

        val active = db!!.activeSessionDao().get()
        assertNotNull(active)
        assertEquals(120000L, active!!.accumulatedMs)

        assertTrue(db!!.repSetDao().getAll().isEmpty())
        assertFalse(db!!.repDraftDao().get() != null)
    }
}
