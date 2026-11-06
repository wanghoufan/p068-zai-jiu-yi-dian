package com.wanghoufan.zaijiuyidian

import com.wanghoufan.zaijiuyidian.data.backup.BackupCodec
import com.wanghoufan.zaijiuyidian.data.backup.BackupException
import com.wanghoufan.zaijiuyidian.data.db.ProjectEntity
import com.wanghoufan.zaijiuyidian.data.db.SessionEntity
import com.wanghoufan.zaijiuyidian.domain.AppSettings
import com.wanghoufan.zaijiuyidian.domain.ChartMode
import com.wanghoufan.zaijiuyidian.domain.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupCodecTest {

    private val project = ProjectEntity(
        id = "p1",
        name = "平板支撑",
        defaultTargetSec = 1500,
        presetsSec = "900,1200,1500",
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L
    )

    private val session = SessionEntity(
        id = "s1",
        projectId = "p1",
        startedAt = 1_700_000_000_000L,
        endedAt = 1_700_000_600_000L,
        targetSec = 1500,
        actualSec = 1200,
        pauseCount = 1,
        pausedSec = 30,
        source = SessionEntity.SOURCE_TIMER,
        note = "呼吸稳定",
        createdAt = 1_700_000_600_000L,
        updatedAt = 1_700_000_600_000L
    )

    private val settings = AppSettings(
        selectedProjectId = "p1",
        theme = ThemeMode.DARK,
        soundEnabled = false,
        vibrationEnabled = true,
        lastRangeDays = 30,
        lastChart = ChartMode.WEEKLY,
        lastProjectFilter = "all"
    )

    @Test
    fun roundtrip_preserves_projects_sessions_and_settings() {
        val json = BackupCodec.encode(listOf(project), listOf(session), "p1", settings, "1.0.0", 1_700_001_000_000L)
        val parsed = BackupCodec.parse(json).data
        assertEquals(1, parsed.projects.size)
        assertEquals("平板支撑", parsed.projects[0].name)
        assertEquals(listOf(900, 1200, 1500), parsed.projects[0].presetsSec)
        assertEquals(1, parsed.sessions.size)
        assertEquals(1200, parsed.sessions[0].actualSec)
        assertEquals(1, parsed.sessions[0].pauseCount)
        assertEquals("dark", parsed.settings.theme)
        assertEquals(false, parsed.settings.soundEnabled)
        assertEquals("p1", parsed.projectId)
    }

    @Test
    fun tampered_checksum_is_rejected() {
        val json = BackupCodec.encode(listOf(project), listOf(session), "p1", settings, "1.0.0", 1_700_001_000_000L)
        val tampered = json.replace("\"actualSec\": 1200", "\"actualSec\": 1300")
        try {
            BackupCodec.parse(tampered)
            fail("Expected BackupException")
        } catch (e: BackupException) {
            assertTrue(e.message!!.contains("校验"))
        }
    }

    @Test
    fun wrong_app_or_version_is_rejected() {
        val json = BackupCodec.encode(listOf(project), listOf(session), "p1", settings, "1.0.0", 1_700_001_000_000L)
        try {
            BackupCodec.parse(json.replace("\"version\": 1", "\"version\": 99"))
            fail("Expected BackupException")
        } catch (e: BackupException) {
            assertTrue(e.message!!.contains("版本"))
        }
        try {
            BackupCodec.parse(json.replace("\"app\": \"duration-progress\"", "\"app\": \"other\""))
            fail("Expected BackupException")
        } catch (e: BackupException) {
            assertTrue(e.message!!.contains("再久一点"))
        }
    }

    @Test
    fun merge_never_overwrites_existing_ids_or_tombstones() {
        val json = BackupCodec.encode(listOf(project), listOf(session), "p1", settings, "1.0.0", 1_700_001_000_000L)
        val data = BackupCodec.parse(json).data
        val (projects, sessions) = BackupCodec.merge(
            data,
            existingProjectIds = setOf("p1"),
            existingSessionIds = setOf("s1")
        )
        assertTrue(projects.isEmpty())
        assertTrue(sessions.isEmpty())
        val preview = BackupCodec.preview(data, setOf("p1"), setOf("s1"))
        assertEquals(0, preview.newProjects)
        assertEquals(0, preview.newSessions)
        assertEquals(1, preview.conflictProjects)
        assertEquals(1, preview.conflictSessions)
    }

    @Test
    fun invalid_project_reference_is_rejected() {
        val base = BackupCodec.parse(
            BackupCodec.encode(listOf(project), listOf(session), "p1", settings, "1.0.0", 1_700_001_000_000L)
        ).data
        val broken = base.copy(sessions = listOf(base.sessions[0].copy(projectId = "ghost")))
        try {
            BackupCodec.parse(BackupCodec.encodeData(broken))
            fail("Expected BackupException")
        } catch (e: BackupException) {
            assertTrue(e.message!!.contains("不存在的项目"))
        }
    }

    @Test
    fun duplicate_session_uuid_is_rejected() {
        val base = BackupCodec.parse(
            BackupCodec.encode(listOf(project), listOf(session), "p1", settings, "1.0.0", 1_700_001_000_000L)
        ).data
        val duplicated = base.copy(sessions = listOf(base.sessions[0], base.sessions[0]))
        try {
            BackupCodec.parse(BackupCodec.encodeData(duplicated))
            fail("Expected BackupException")
        } catch (e: BackupException) {
            assertTrue(e.message!!.contains("重复"))
        }
    }

    @Test
    fun tombstone_sessions_survive_roundtrip() {
        val deleted = session.copy(deletedAt = 1_700_000_800_000L)
        val json = BackupCodec.encode(listOf(project), listOf(deleted), "p1", settings, "1.0.0", 1_700_001_000_000L)
        val parsed = BackupCodec.parse(json).data
        assertEquals(1_700_000_800_000L, parsed.sessions[0].deletedAt)
    }
}
