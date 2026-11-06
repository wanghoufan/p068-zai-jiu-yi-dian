package com.wanghoufan.zaijiuyidian.domain

import com.wanghoufan.zaijiuyidian.data.db.ActiveSessionEntity

/**
 * Pure timing rules. Duration always derives from the monotonic clock; wall time is only
 * used for the saved completion timestamp and for boot detection.
 */
object TimerMath {

    data class FinishSnapshot(
        val actualSec: Int,
        val pausedSec: Int,
        val pauseCount: Int,
        val endedAtWall: Long
    )

    fun bootWallBase(nowWall: Long, nowElapsed: Long): Long = nowWall - nowElapsed

    fun intervalMark(elapsedMs: Long, intervalMs: Long = 180_000L): Int =
        (elapsedMs / intervalMs).toInt().coerceAtLeast(0)

    fun isPaused(session: ActiveSessionEntity): Boolean = session.pausedAtElapsedMs != null

    fun elapsedMs(session: ActiveSessionEntity, nowElapsed: Long): Long {
        val currentSegment = session.segmentStartedElapsedMs
        return if (currentSegment != null && !session.interrupted) {
            session.accumulatedMs + (nowElapsed - currentSegment).coerceAtLeast(0)
        } else {
            session.accumulatedMs
        }
    }

    fun pausedMs(session: ActiveSessionEntity, nowElapsed: Long): Long {
        val openPause = session.pausedAtElapsedMs
        return session.pausedMs + if (openPause != null) (nowElapsed - openPause).coerceAtLeast(0) else 0
    }

    fun isRebooted(session: ActiveSessionEntity, nowWall: Long, nowElapsed: Long, nowBootCount: Int): Boolean {
        if (session.interrupted) return false
        if (session.bootCount >= 0 && nowBootCount >= 0 && session.bootCount != nowBootCount) return true
        val currentBase = bootWallBase(nowWall, nowElapsed)
        return kotlin.math.abs(currentBase - session.bootWallBase) > 10_000
    }

    fun pause(session: ActiveSessionEntity, nowElapsed: Long): ActiveSessionEntity {
        if (isPaused(session)) return session
        val elapsed = elapsedMs(session, nowElapsed)
        return session.copy(
            accumulatedMs = elapsed,
            segmentStartedElapsedMs = null,
            pausedAtElapsedMs = nowElapsed,
            pauseCount = session.pauseCount + 1,
            lastKnownElapsedMs = elapsed,
            lastKnownAtWall = session.lastKnownAtWall
        )
    }

    fun resume(session: ActiveSessionEntity, nowElapsed: Long): ActiveSessionEntity {
        if (!isPaused(session)) return session
        val extraPause = (nowElapsed - (session.pausedAtElapsedMs ?: nowElapsed)).coerceAtLeast(0)
        return session.copy(
            segmentStartedElapsedMs = nowElapsed,
            pausedAtElapsedMs = null,
            pausedMs = session.pausedMs + extraPause
        )
    }

    fun checkpoint(session: ActiveSessionEntity, nowElapsed: Long, nowWall: Long): ActiveSessionEntity {
        if (session.interrupted) return session
        return session.copy(
            lastKnownElapsedMs = elapsedMs(session, nowElapsed),
            lastKnownAtWall = nowWall
        )
    }

    fun markInterrupted(session: ActiveSessionEntity): ActiveSessionEntity = session.copy(
        interrupted = true,
        accumulatedMs = session.lastKnownElapsedMs,
        segmentStartedElapsedMs = null,
        pausedAtElapsedMs = null
    )

    fun finish(session: ActiveSessionEntity, nowElapsed: Long, nowWall: Long): FinishSnapshot {
        val elapsed = elapsedMs(session, nowElapsed)
        return FinishSnapshot(
            actualSec = (elapsed / 1000).toInt(),
            pausedSec = (pausedMs(session, nowElapsed) / 1000).toInt(),
            pauseCount = session.pauseCount,
            endedAtWall = nowWall
        )
    }
}
