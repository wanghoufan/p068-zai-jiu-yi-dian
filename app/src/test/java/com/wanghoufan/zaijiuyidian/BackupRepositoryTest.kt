package com.wanghoufan.zaijiuyidian

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wanghoufan.zaijiuyidian.data.backup.BackupCodec
import com.wanghoufan.zaijiuyidian.data.backup.BackupRepository
import com.wanghoufan.zaijiuyidian.data.db.AppDatabase
import com.wanghoufan.zaijiuyidian.data.db.ProjectEntity
import com.wanghoufan.zaijiuyidian.data.db.RepDraftEntity
import com.wanghoufan.zaijiuyidian.data.db.RepSetEntity
import com.wanghoufan.zaijiuyidian.data.db.SessionEntity
import com.wanghoufan.zaijiuyidian.domain.DefaultSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val databases = mutableListOf<AppDatabase>()

    private fun newDb(): AppDatabase {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        databases.add(db)
        return db
    }

    @After
    fun tearDown() {
        databases.forEach { it.close() }
    }

    @Test
    fun v3_export_replace_keeps_reps_sets_and_draft() = runBlocking {
        val source = newDb()
        val now = 1_700_000_000_000L
        source.projectDao().upsertAll(
            listOf(
                ProjectEntity(
                    id = "t1",
                    name = "平板支撑",
                    defaultTargetSec = 900,
                    presetsSec = "600,900",
                    createdAt = now,
                    updatedAt = now
                ),
                ProjectEntity(
                    id = "r1",
                    name = "引体向上",
                    defaultTargetSec = 900,
                    presetsSec = "600,900",
                    createdAt = now + 1,
                    updatedAt = now + 1,
                    mode = ProjectEntity.MODE_REPS,
                    plannedSets = 4,
                    targetRepsPerSet = 10
                )
            )
        )
        source.sessionDao().upsertAll(
            listOf(
                SessionEntity(
                    id = "ts1",
                    projectId = "t1",
                    startedAt = now,
                    endedAt = now + 60_000,
                    targetSec = 900,
                    actualSec = 60,
                    createdAt = now,
                    updatedAt = now
                ),
                SessionEntity(
                    id = "rs1",
                    projectId = "r1",
                    startedAt = null,
                    endedAt = now + 120_000,
                    targetSec = 0,
                    actualSec = 0,
                    source = SessionEntity.SOURCE_REPS,
                    createdAt = now + 120_000,
                    updatedAt = now + 120_000,
                    mode = SessionEntity.MODE_REPS,
                    plannedSets = 4,
                    targetRepsPerSet = 10
                )
            )
        )
        source.repSetDao().upsertAll(
            listOf(12, 10, 10, 8).mapIndexed { index, reps ->
                RepSetEntity(
                    id = "rs1:${index + 1}",
                    sessionId = "rs1",
                    ordinal = index + 1,
                    actualReps = reps
                )
            }
        )
        source.repDraftDao().upsert(
            RepDraftEntity(
                projectId = "r1",
                plannedSets = 3,
                targetRepsPerSet = 10,
                setsCsv = "10,9",
                nextReps = 10,
                touchedAt = now + 200_000
            )
        )

        val json = BackupRepository(context, source).exportJson(
            appVersion = "1.1.0",
            settings = DefaultSettings.copy(selectedProjectId = "r1"),
            nowMs = now + 300_000
        )
        val parsed = BackupCodec.parse(json).data
        assertEquals(3, parsed.version)
        assertEquals(listOf(12, 10, 10, 8), parsed.sessions.first { it.id == "rs1" }.sets)
        assertEquals(listOf(10, 9), parsed.draft?.sets)

        val target = newDb()
        BackupRepository(context, target).replace(parsed, DefaultSettings)
        assertEquals(2, target.projectDao().getAll().size)
        assertEquals(2, target.sessionDao().getAll().size)
        assertEquals(4, target.repSetDao().forSession("rs1").size)
        assertNotNull(target.repDraftDao().get())
        assertEquals(listOf(10, 9), target.repDraftDao().get()!!.sets)

        val reExported = BackupCodec.parse(
            BackupRepository(context, target).exportJson(
                appVersion = "1.1.0",
                settings = DefaultSettings.copy(selectedProjectId = "r1"),
                nowMs = now + 400_000
            )
        ).data
        assertEquals(listOf(12, 10, 10, 8), reExported.sessions.first { it.id == "rs1" }.sets)
        assertEquals("REPS", reExported.projects.first { it.id == "r1" }.mode)
    }
}
