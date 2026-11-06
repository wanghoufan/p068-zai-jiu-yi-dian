package com.wanghoufan.zaijiuyidian

import com.wanghoufan.zaijiuyidian.data.db.ActiveSessionEntity
import com.wanghoufan.zaijiuyidian.domain.TimerMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerMathTest {

    private fun session(
        segmentStart: Long? = 1_000L,
        accumulated: Long = 0L,
        pausedAt: Long? = null,
        pausedMs: Long = 0L,
        pauseCount: Int = 0,
        bootCount: Int = 7,
        bootWallBase: Long = 1_700_000_000_000L,
        lastKnown: Long = 0L,
        lastKnownWall: Long = 1_700_000_000_000L,
        interrupted: Boolean = false
    ) = ActiveSessionEntity(
        projectId = "p1",
        targetSec = 1500,
        accumulatedMs = accumulated,
        segmentStartedElapsedMs = segmentStart,
        pausedAtElapsedMs = pausedAt,
        pauseCount = pauseCount,
        pausedMs = pausedMs,
        bootWallBase = bootWallBase,
        bootCount = bootCount,
        startedAtWall = 1_700_000_000_000L,
        notifiedAtTarget = false,
        lastKnownElapsedMs = lastKnown,
        lastKnownAtWall = lastKnownWall,
        interrupted = interrupted
    )

    @Test
    fun elapsed_derives_from_monotonic_anchor() {
        val s = session(segmentStart = 1_000L)
        assertEquals(0L, TimerMath.elapsedMs(s, 1_000L))
        assertEquals(59_000L, TimerMath.elapsedMs(s, 60_000L))
        assertEquals(1_500_000L, TimerMath.elapsedMs(s, 1_501_000L))
    }

    @Test
    fun pause_freezes_elapsed_and_counts_pause() {
        val s = session(segmentStart = 1_000L)
        val paused = TimerMath.pause(s, 61_000L)
        assertEquals(60_000L, paused.accumulatedMs)
        assertEquals(1, paused.pauseCount)
        assertEquals(60_000L, TimerMath.isPaused(paused).let { if (it) TimerMath.elapsedMs(paused, 120_000L) else -1 })
        assertEquals(60_000L, TimerMath.elapsedMs(paused, 300_000L))
        assertEquals(239_000L, TimerMath.pausedMs(paused, 300_000L))
    }

    @Test
    fun resume_excludes_pause_gap() {
        val s = session(segmentStart = 1_000L)
        val paused = TimerMath.pause(s, 61_000L)
        val resumed = TimerMath.resume(paused, 121_000L)
        assertEquals(60_000L, TimerMath.elapsedMs(resumed, 121_000L))
        assertEquals(120_000L, TimerMath.elapsedMs(resumed, 181_000L))
        assertEquals(60_000L, resumed.pausedMs)
        assertFalse(TimerMath.isPaused(resumed))
    }

    @Test
    fun finish_floors_seconds_and_reports_pause_totals() {
        val s = session(segmentStart = 1_000L)
        val paused = TimerMath.pause(s, 30_999L)
        val resumed = TimerMath.resume(paused, 40_000L)
        val snapshot = TimerMath.finish(resumed, 100_999L, 1_700_000_100_999L)
        assertEquals(90, snapshot.actualSec)
        assertEquals(9, snapshot.pausedSec)
        assertEquals(1, snapshot.pauseCount)
    }

    @Test
    fun reboot_detected_by_boot_count_or_wall_base_shift() {
        val s = session(bootCount = 7, bootWallBase = 1_700_000_000_000L, segmentStart = 5_000L)
        assertFalse(TimerMath.isRebooted(s, 1_700_000_060_000L, 65_000L, 7))
        assertTrue(TimerMath.isRebooted(s, 1_700_000_060_000L, 65_000L, 8))
        assertTrue(TimerMath.isRebooted(s, 1_800_000_060_000L, 65_000L, 7))
    }

    @Test
    fun interrupt_restores_last_reliable_elapsed_only() {
        val s = session(
            segmentStart = 5_000L,
            lastKnown = 30_000L,
            lastKnownWall = 1_700_000_030_000L
        )
        val interrupted = TimerMath.markInterrupted(s)
        assertTrue(interrupted.interrupted)
        assertEquals(30_000L, interrupted.accumulatedMs)
        assertEquals(null, interrupted.segmentStartedElapsedMs)
        // Even with a much later monotonic value, no invented duration is added.
        assertEquals(30_000L, TimerMath.elapsedMs(interrupted, 900_000L))
    }

    @Test
    fun interval_mark_steps_every_three_minutes() {
        assertEquals(0, TimerMath.intervalMark(0))
        assertEquals(0, TimerMath.intervalMark(179_999))
        assertEquals(1, TimerMath.intervalMark(180_000))
        assertEquals(1, TimerMath.intervalMark(359_999))
        assertEquals(2, TimerMath.intervalMark(360_000))
        assertEquals(5, TimerMath.intervalMark(900_000))
    }

    @Test
    fun checkpoint_uses_current_elapsed_for_recovery() {
        val s = session(segmentStart = 1_000L)
        val checkpoint = TimerMath.checkpoint(s, 46_000L, 1_700_000_045_000L)
        assertEquals(45_000L, checkpoint.lastKnownElapsedMs)
        assertEquals(1_700_000_045_000L, checkpoint.lastKnownAtWall)
    }
}
