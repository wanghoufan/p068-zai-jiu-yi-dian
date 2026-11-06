package com.wanghoufan.zaijiuyidian

import com.wanghoufan.zaijiuyidian.domain.Stats
import com.wanghoufan.zaijiuyidian.domain.TrainingMode
import com.wanghoufan.zaijiuyidian.domain.TrainingSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class StatsTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun epoch(y: Int, m: Int, d: Int, h: Int, min: Int): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    private fun session(
        id: String,
        endedAt: Long,
        targetSec: Int = 600,
        actualSec: Int = 600,
        projectId: String = "p1",
        pauseCount: Int = 0,
        note: String = ""
    ) = TrainingSession(
        id = id,
        projectId = projectId,
        startedAt = endedAt - actualSec * 1000L,
        endedAt = endedAt,
        targetSec = targetSec,
        actualSec = actualSec,
        pauseCount = pauseCount,
        pausedSec = 0,
        source = "timer",
        note = note,
        createdAt = endedAt,
        updatedAt = endedAt
    )

    @Test
    fun kpi_uses_actual_values_and_achievement_rate() {
        val list = listOf(
            session("a", epoch(2026, 10, 5, 10, 0), targetSec = 600, actualSec = 300),
            session("b", epoch(2026, 10, 6, 10, 0), targetSec = 600, actualSec = 600),
            session("c", epoch(2026, 10, 7, 10, 0), targetSec = 600, actualSec = 900)
        )
        val kpi = Stats.kpi(list)
        assertEquals(3, kpi.count)
        assertEquals(600L, kpi.avgSec)
        assertEquals(900L, kpi.bestSec)
        assertEquals(2, kpi.achievedCount)
        assertEquals(67, kpi.ratePercent)
    }

    @Test
    fun kpi_empty_is_not_zero_rate() {
        val kpi = Stats.kpi(emptyList())
        assertEquals(0, kpi.count)
        assertEquals(0L, kpi.avgSec)
        assertTrue(!kpi.hasData)
    }

    @Test
    fun week_start_is_monday_local() {
        // 2026-10-05 is a Monday.
        val sunday = epoch(2026, 10, 11, 23, 59)
        val monday = epoch(2026, 10, 5, 0, 0)
        assertEquals(monday, Stats.weekStart(sunday, zone))
        assertEquals(monday, Stats.weekStart(epoch(2026, 10, 5, 0, 0), zone))
    }

    @Test
    fun weekly_groups_and_keeps_zero_weeks_with_null_average() {
        val list = listOf(
            session("a", epoch(2026, 10, 5, 10, 0), actualSec = 600),
            session("b", epoch(2026, 10, 6, 10, 0), actualSec = 900),
            // Skip a week entirely.
            session("c", epoch(2026, 10, 19, 10, 0), actualSec = 1200)
        )
        val now = epoch(2026, 10, 19, 12, 0)
        val weeks = Stats.weeklyWithGaps(list, zone, now, 3)
        assertEquals(3, weeks.size)
        assertEquals(2, weeks[0].count)
        assertEquals(750.0, weeks[0].avgSec!!, 0.001)
        assertEquals(0, weeks[1].count)
        assertNull(weeks[1].avgSec)
        assertEquals(1, weeks[2].count)
        assertEquals(1200.0, weeks[2].avgSec!!, 0.001)
    }

    @Test
    fun filter_by_project_range_and_query() {
        val list = listOf(
            session("a", epoch(2026, 10, 10, 10, 0), projectId = "p1", note = "呼吸稳定"),
            session("b", epoch(2026, 10, 11, 10, 0), projectId = "p2", note = "腿有点酸"),
            session("c", epoch(2026, 9, 1, 10, 0), projectId = "p1", note = "")
        )
        val now = epoch(2026, 10, 12, 12, 0)
        val filtered = Stats.filter(
            list,
            projectFilter = "current",
            currentProjectId = "p1",
            rangeDays = 7,
            query = "",
            nowMs = now,
            zone = zone,
            projectNames = mapOf("p1" to "平板支撑", "p2" to "深蹲")
        )
        assertEquals(listOf("a"), filtered.map { it.id })

        val byQuery = Stats.filter(
            list,
            projectFilter = "all",
            currentProjectId = "p1",
            rangeDays = null,
            query = "深蹲",
            nowMs = now,
            zone = zone,
            projectNames = mapOf("p1" to "平板支撑", "p2" to "深蹲")
        )
        assertEquals(listOf("b"), byQuery.map { it.id })
    }

    @Test
    fun frequency_groups_allow_checking_denominators() {
        val list = listOf(
            session("a", epoch(2026, 10, 5, 10, 0), actualSec = 600),
            session("b", epoch(2026, 10, 6, 10, 0), actualSec = 900),
            session("c", epoch(2026, 10, 12, 10, 0), actualSec = 1200),
            session("d", epoch(2026, 10, 13, 10, 0), actualSec = 1200),
            session("e", epoch(2026, 10, 14, 10, 0), actualSec = 1200)
        )
        val rows = Stats.groupByWeeklyFrequency(list, zone)
        assertEquals(listOf(2, 3), rows.map { it.perWeek })
        assertEquals(1, rows[0].weeks)
        assertEquals(2, rows[0].sessions)
        assertEquals(750.0, rows[0].avgSec!!, 0.001)
        assertEquals(1, rows[1].weeks)
        assertEquals(3, rows[1].sessions)
        assertEquals(1200.0, rows[1].avgSec!!, 0.001)
    }

    @Test
    fun cross_midnight_session_counts_by_completion_week() {
        // Sunday 23:50 session that ends Monday 00:10 belongs to the new week.
        val endedMonday = epoch(2026, 10, 12, 0, 10)
        val list = listOf(session("late", endedMonday, actualSec = 1200))
        val weeks = Stats.weekly(list, zone)
        assertEquals(epoch(2026, 10, 12, 0, 0) - 0, weeks.single().weekStartMs)
    }

    private fun repSession(
        id: String,
        endedAt: Long,
        sets: List<Int>,
        plannedSets: Int = sets.size,
        targetReps: Int = 10,
        projectId: String = "r1"
    ) = TrainingSession(
        id = id,
        projectId = projectId,
        startedAt = null,
        endedAt = endedAt,
        targetSec = 0,
        actualSec = 0,
        pauseCount = 0,
        pausedSec = 0,
        source = "reps",
        note = "",
        createdAt = endedAt,
        updatedAt = endedAt,
        mode = TrainingMode.REPS,
        plannedSets = plannedSets,
        targetRepsPerSet = targetReps,
        sets = sets
    )

    @Test
    fun reps_kpi_matches_hand_calculated_week_sample() {
        val list = listOf(
            repSession("a", epoch(2026, 10, 5, 10, 0), listOf(10, 10, 10), plannedSets = 3),
            repSession("b", epoch(2026, 10, 7, 10, 0), listOf(10, 10, 10, 10), plannedSets = 4),
            repSession("c", epoch(2026, 10, 9, 10, 0), listOf(12, 10, 10, 8), plannedSets = 4),
            repSession("d", epoch(2026, 10, 11, 10, 0), listOf(10, 10, 10, 10, 10), plannedSets = 5)
        )
        val kpi = Stats.repsKpi(list)
        assertEquals(4, kpi.count)
        assertEquals(160, kpi.totalReps)
        assertEquals(50, kpi.bestSessionTotal)
        assertEquals(12, kpi.bestSet)
        assertEquals(5, kpi.maxSets)

        val weeks = Stats.valueWeekly(list, TrainingMode.REPS, zone)
        assertEquals(1, weeks.size)
        assertEquals(4, weeks[0].count)
        assertEquals(40.0, weeks[0].avgValue!!, 0.001)
        assertEquals(4, weeks[0].activeDays)
    }

    @Test
    fun mixed_scope_only_combines_counts_days_and_types() {
        val list = listOf(
            session("t1", epoch(2026, 10, 5, 10, 0), projectId = "p1", actualSec = 600),
            session("t2", epoch(2026, 10, 5, 12, 0), projectId = "p1", actualSec = 900),
            repSession("r1", epoch(2026, 10, 5, 18, 0), listOf(10, 10, 10)),
            repSession("r2", epoch(2026, 10, 6, 18, 0), listOf(8, 8))
        )
        val kpi = Stats.mixedKpi(list, zone)
        assertEquals(4, kpi.count)
        assertEquals(2, kpi.days)
        assertEquals(2, kpi.timeCount)
        assertEquals(2, kpi.repsCount)

        val weekly = Stats.valueWeekly(list, null, zone)
        assertEquals(1, weekly.size)
        assertEquals(4, weekly[0].count)
        assertEquals(2.0, weekly[0].avgValue!!, 0.001)
        assertEquals(2, weekly[0].activeDays)
    }

    @Test
    fun reps_group_rows_use_actual_total_not_planned() {
        val list = listOf(
            repSession("a", epoch(2026, 10, 5, 10, 0), listOf(10, 10, 10), plannedSets = 3),
            repSession("b", epoch(2026, 10, 6, 10, 0), listOf(10, 10, 10, 10), plannedSets = 4),
            repSession("c", epoch(2026, 10, 12, 10, 0), listOf(12, 10, 10, 8, 5), plannedSets = 4)
        )
        val rows = Stats.valueGroups(list, TrainingMode.REPS, zone)
        assertEquals(listOf(1, 2), rows.map { it.perWeek })
        assertEquals(45.0, rows[0].avgValue!!, 0.001)
        assertEquals(35.0, rows[1].avgValue!!, 0.001)
        assertEquals(1, rows[0].sessions)
        assertEquals(2, rows[1].sessions)
    }
}
