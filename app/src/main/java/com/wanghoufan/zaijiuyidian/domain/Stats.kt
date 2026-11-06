package com.wanghoufan.zaijiuyidian.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

object Stats {

    data class Kpi(
        val count: Int,
        val avgSec: Long,
        val bestSec: Long,
        val achievedCount: Int,
        val ratePercent: Int
    ) {
        val hasData: Boolean get() = count > 0
    }

    data class WeekPoint(
        val weekStartMs: Long,
        val count: Int,
        val totalSec: Long,
        val avgSec: Double?,
        val achievedCount: Int
    )

    data class GroupRow(
        val perWeek: Int,
        val weeks: Int,
        val sessions: Int,
        val avgSec: Double?,
        val achieved: Int,
        val ratePercent: Int?
    )

    fun kpi(sessions: List<TrainingSession>): Kpi {
        if (sessions.isEmpty()) return Kpi(0, 0, 0, 0, 0)
        val total = sessions.sumOf { it.actualSec.toLong() }
        val best = sessions.maxOf { it.actualSec.toLong() }
        val achieved = sessions.count { it.achieved }
        return Kpi(
            count = sessions.size,
            avgSec = Math.round(total.toDouble() / sessions.size),
            bestSec = best,
            achievedCount = achieved,
            ratePercent = Math.round(achieved * 100f / sessions.size)
        )
    }

    private fun dayStart(epochMs: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    fun weekStart(epochMs: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(epochMs)
            .atZone(zone)
            .toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

    fun filter(
        sessions: List<TrainingSession>,
        projectFilter: String,
        currentProjectId: String?,
        rangeDays: Int?,
        query: String,
        nowMs: Long,
        zone: ZoneId,
        projectNames: Map<String, String> = emptyMap()
    ): List<TrainingSession> {
        val trimmed = query.trim()
        val byProject = when (projectFilter) {
            "all" -> sessions
            "current" -> currentProjectId?.let { id -> sessions.filter { it.projectId == id } } ?: sessions
            else -> sessions.filter { it.projectId == projectFilter }
        }
        val byRange = if (rangeDays == null) {
            byProject
        } else {
            val todayStart = dayStart(nowMs, zone)
            val cutoff = Instant.ofEpochMilli(todayStart)
                .atZone(zone)
                .toLocalDate()
                .minusDays((rangeDays - 1).toLong())
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
            byProject.filter { it.endedAt >= cutoff && it.endedAt <= nowMs + 86_400_000L }
        }
        val byQuery = if (trimmed.isEmpty()) byRange else byRange.filter { session ->
            session.note.contains(trimmed, ignoreCase = true) ||
                (projectNames[session.projectId]?.contains(trimmed, ignoreCase = true) == true)
        }
        return byQuery.sortedWith(compareByDescending<TrainingSession> { it.endedAt }.thenByDescending { it.id })
    }

    fun weekly(sessions: List<TrainingSession>, zone: ZoneId): List<WeekPoint> {
        if (sessions.isEmpty()) return emptyList()
        val map = LinkedHashMap<Long, MutableList<TrainingSession>>()
        sessions.forEach { session ->
            val key = weekStart(session.endedAt, zone)
            map.getOrPut(key) { mutableListOf() }.add(session)
        }
        return map.entries.sortedBy { it.key }.map { (week, items) ->
            val total = items.sumOf { it.actualSec.toLong() }
            WeekPoint(
                weekStartMs = week,
                count = items.size,
                totalSec = total,
                avgSec = total.toDouble() / items.size,
                achievedCount = items.count { it.achieved }
            )
        }
    }

    fun weeklyWithGaps(
        sessions: List<TrainingSession>,
        zone: ZoneId,
        nowMs: Long,
        weeks: Int
    ): List<WeekPoint> {
        val currentWeek = weekStart(nowMs, zone)
        val byWeek = weekly(sessions, zone).associateBy { it.weekStartMs }
        val result = mutableListOf<WeekPoint>()
        val currentDate = Instant.ofEpochMilli(currentWeek).atZone(zone).toLocalDate()
        for (offset in (weeks - 1) downTo 0) {
            val date = currentDate.minusWeeks(offset.toLong())
            val startMs = date.atStartOfDay(zone).toInstant().toEpochMilli()
            result.add(
                byWeek[startMs] ?: WeekPoint(
                    weekStartMs = startMs,
                    count = 0,
                    totalSec = 0,
                    avgSec = null,
                    achievedCount = 0
                )
            )
        }
        return result
    }

    fun scatter(sessions: List<TrainingSession>, zone: ZoneId): List<WeekPoint> =
        weekly(sessions, zone).filter { it.count > 0 }

    fun groupByWeeklyFrequency(sessions: List<TrainingSession>, zone: ZoneId): List<GroupRow> {
        val weeks = weekly(sessions, zone).filter { it.count > 0 }
        if (weeks.isEmpty()) return emptyList()
        val byWeek = LinkedHashMap<Long, MutableList<TrainingSession>>()
        sessions.forEach { session ->
            byWeek.getOrPut(weekStart(session.endedAt, zone)) { mutableListOf() }.add(session)
        }
        val groups = weeks.groupBy { it.count }
        return groups.entries.sortedBy { it.key }.map { (perWeek, points) ->
            val weekSessions = points.flatMap { byWeek[it.weekStartMs].orEmpty() }
            val total = weekSessions.sumOf { it.actualSec.toLong() }
            val achieved = weekSessions.count { it.achieved }
            GroupRow(
                perWeek = perWeek,
                weeks = points.size,
                sessions = weekSessions.size,
                avgSec = if (weekSessions.isEmpty()) null else total.toDouble() / weekSessions.size,
                achieved = achieved,
                ratePercent = if (weekSessions.isEmpty()) null else Math.round(achieved * 100f / weekSessions.size)
            )
        }
    }

    fun dayStartFor(epochMs: Long, zone: ZoneId): Long = dayStart(epochMs, zone)

    fun localDate(epochMs: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
}
