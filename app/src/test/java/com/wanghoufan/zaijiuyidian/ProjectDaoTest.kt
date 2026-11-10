package com.wanghoufan.zaijiuyidian

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wanghoufan.zaijiuyidian.data.db.AppDatabase
import com.wanghoufan.zaijiuyidian.data.db.ProjectEntity
import com.wanghoufan.zaijiuyidian.data.db.SessionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
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
class ProjectDaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun updateProjectWithHistory_doesNotBreakForeignKeyOrHistory() = runBlocking {
        val now = 1_700_000_000_000L
        val project = ProjectEntity(
            id = "p1",
            name = "平板支撑",
            defaultTargetSec = 1500,
            presetsSec = "900,1200,1500",
            createdAt = now,
            updatedAt = now
        )
        db.projectDao().upsert(project)
        db.sessionDao().upsert(
            SessionEntity(
                id = "s1",
                projectId = "p1",
                startedAt = now,
                endedAt = now + 600_000,
                targetSec = 1500,
                actualSec = 1200,
                createdAt = now,
                updatedAt = now
            )
        )

        // Renaming/archiving must use UPDATE semantics; REPLACE would transiently
        // delete the parent row and violate the sessions -> projects foreign key.
        db.projectDao().update(project.copy(name = "平板支撑 改名", updatedAt = now + 1))
        db.projectDao().update(
            project.copy(name = "平板支撑 改名", archivedAt = now + 2, updatedAt = now + 2)
        )

        val updated = db.projectDao().findById("p1")
        assertNotNull(updated)
        assertEquals("平板支撑 改名", updated!!.name)
        assertNotNull(updated.archivedAt)
        assertEquals(1, db.sessionDao().getLive().size)
        assertEquals("s1", db.sessionDao().getLive().first().id)
    }

    @Test
    fun updateBumped_bumpsRevisionOnEveryWrite() = runBlocking {
        val now = 1_700_000_000_000L
        val project = ProjectEntity(
            id = "p1",
            name = "平板支撑",
            defaultTargetSec = 1500,
            presetsSec = "900,1200,1500",
            createdAt = now,
            updatedAt = now
        )
        db.projectDao().upsert(project)
        db.projectDao().updateBumped(project.copy(name = "第一次改名", updatedAt = now + 1))
        db.projectDao().updateBumped(project.copy(name = "第二次改名", updatedAt = now + 2))

        val updated = db.projectDao().findByIdAny("p1")
        assertNotNull(updated)
        assertEquals("第二次改名", updated!!.name)
        assertEquals(3, updated.revision)
    }

    @Test
    fun softDelete_hidesFromListsButKeepsRowForSync() = runBlocking {
        val now = 1_700_000_000_000L
        db.projectDao().upsert(
            ProjectEntity(
                id = "p1",
                name = "平板支撑",
                defaultTargetSec = 1500,
                presetsSec = "900,1200,1500",
                createdAt = now,
                updatedAt = now
            )
        )

        db.projectDao().softDelete("p1", now + 10)

        assertTrue(db.projectDao().getAll().isEmpty())
        assertTrue(withTimeout(10_000) { db.projectDao().observeAll().first() }.isEmpty())
        assertNull(db.projectDao().findById("p1"))

        val any = db.projectDao().findByIdAny("p1")
        assertNotNull(any)
        assertEquals(now + 10, any!!.deletedAt)
        assertEquals(2, any.revision)
        assertEquals(1, db.projectDao().getAllIncludingDeleted().size)
    }

    @Test
    fun replacingProjectWithHistory_isRejected() = runBlocking {
        val now = 1_700_000_000_000L
        db.projectDao().upsert(
            ProjectEntity(
                id = "p1",
                name = "原有项目",
                defaultTargetSec = 600,
                presetsSec = "300,600",
                createdAt = now,
                updatedAt = now
            )
        )
        db.sessionDao().upsert(
            SessionEntity(
                id = "s1",
                projectId = "p1",
                startedAt = now,
                endedAt = now + 60_000,
                targetSec = 600,
                actualSec = 60,
                createdAt = now,
                updatedAt = now
            )
        )
        val failed = runCatching {
            db.projectDao().upsert(
                ProjectEntity(
                    id = "p1",
                    name = "覆盖项目",
                    defaultTargetSec = 900,
                    presetsSec = "600,900",
                    createdAt = now,
                    updatedAt = now
                )
            )
        }.isFailure
        assertEquals(true, failed)
        assertEquals(1, db.sessionDao().getLive().size)
    }
}
