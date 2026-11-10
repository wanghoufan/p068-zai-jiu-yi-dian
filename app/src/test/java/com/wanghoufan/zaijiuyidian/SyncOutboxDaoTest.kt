package com.wanghoufan.zaijiuyidian

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wanghoufan.zaijiuyidian.data.db.AppDatabase
import com.wanghoufan.zaijiuyidian.data.db.SyncOutboxEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncOutboxDaoTest {

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

    private fun mutation(payload: String = """{"id":"p1"}""") = SyncOutboxEntity(
        mutationId = "m1",
        entity = SyncOutboxEntity.ENTITY_PROJECT,
        recordId = "p1",
        op = SyncOutboxEntity.OP_UPSERT,
        payload = payload,
        baseRevision = 1,
        createdAt = now
    )

    @Test
    fun enqueueWithSameMutationId_keepsOnlyFirstRow() = runBlocking {
        val dao = db.syncOutboxDao()
        dao.enqueue(mutation())

        dao.enqueue(mutation(payload = """{"id":"p1","name":"重复入队"}"""))

        assertEquals(1, dao.count())
        assertEquals("""{"id":"p1"}""", dao.getAll().single().payload)
    }

    @Test
    fun markFailed_recordsAttemptAndLastError() = runBlocking {
        val dao = db.syncOutboxDao()
        dao.enqueue(mutation())
        assertNull(dao.getAll().single().lastError)
        assertEquals(0, dao.getAll().single().attempts)

        dao.markFailed("m1", "网络不可用")

        val failed = dao.getAll().single()
        assertEquals(1, failed.attempts)
        assertEquals("网络不可用", failed.lastError)
    }
}