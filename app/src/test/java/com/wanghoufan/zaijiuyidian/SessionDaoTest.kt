package com.wanghoufan.zaijiuyidian

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wanghoufan.zaijiuyidian.data.db.AppDatabase
import com.wanghoufan.zaijiuyidian.data.db.ProjectEntity
import com.wanghoufan.zaijiuyidian.data.db.SessionEntity
import kotlinx.coroutines.runBlocking
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
class SessionDaoTest {

    private val now = 1_700_000_000_000L
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

    private suspend fun seedProject() {
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
    }

    private fun session(updatedAt: Long = now, note: String = "训练") = SessionEntity(
        id = "s1",
        projectId = "p1",
        startedAt = now,
        endedAt = now + 600_000,
        targetSec = 1500,
        actualSec = 1200,
        createdAt = now,
        updatedAt = updatedAt,
        note = note
    )

    @Test
    fun upsertBumped_startsAtOneAndIncrementsOnUpdate() = runBlocking {
        seedProject()

        db.sessionDao().upsertBumped(session())
        val inserted = db.sessionDao().findById("s1")
        assertNotNull(inserted)
        assertEquals(1, inserted!!.revision)

        db.sessionDao().upsertBumped(session(updatedAt = now + 1, note = "改一次"))
        val updated = db.sessionDao().findById("s1")
        assertNotNull(updated)
        assertEquals(2, updated!!.revision)
        assertEquals("改一次", updated.note)
        assertEquals(1, db.sessionDao().getAll().size)
    }

    @Test
    fun softDeleteThenRestore_keepsBumpingRevision() = runBlocking {
        seedProject()
        db.sessionDao().upsertBumped(session())

        db.sessionDao().softDelete("s1", now + 10)
        val deleted = db.sessionDao().findById("s1")
        assertNotNull(deleted)
        assertEquals(now + 10, deleted!!.deletedAt)
        assertEquals(2, deleted.revision)
        assertTrue(db.sessionDao().getLive().isEmpty())

        db.sessionDao().restore("s1", now + 20)
        val restored = db.sessionDao().findById("s1")
        assertNotNull(restored)
        assertNull(restored!!.deletedAt)
        assertEquals(3, restored.revision)
        assertEquals(1, db.sessionDao().getLive().size)
    }
}