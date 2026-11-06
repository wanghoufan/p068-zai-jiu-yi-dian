package com.wanghoufan.zaijiuyidian.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Formatters {

    fun clock(totalSeconds: Long): String {
        val s = totalSeconds.coerceAtLeast(0)
        val h = s / 3600
        val m = s % 3600 / 60
        val sec = s % 60
        return if (h > 0) {
            String.format(Locale.CHINA, "%02d:%02d:%02d", h, m, sec)
        } else {
            String.format(Locale.CHINA, "%02d:%02d", m, sec)
        }
    }

    fun minutes(seconds: Long): String {
        val value = seconds / 60.0
        val rounded = Math.round(value * 10) / 10.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else String.format(
            Locale.CHINA,
            "%.1f",
            rounded
        )
    }

    fun minutes(seconds: Int): String = minutes(seconds.toLong())

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA)
    private val dateFormatter = DateTimeFormatter.ofPattern("MM-dd", Locale.CHINA)
    private val isoFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    fun dateTime(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        dateTimeFormatter.format(Instant.ofEpochMilli(epochMs).atZone(zone))

    fun dateShort(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        dateFormatter.format(Instant.ofEpochMilli(epochMs).atZone(zone))

    fun iso(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        isoFormatter.format(Instant.ofEpochMilli(epochMs).atZone(zone))

    fun percent(value: Int): String = "$value%"
}
