package com.wanghoufan.zaijiuyidian.audio

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.wanghoufan.zaijiuyidian.MainActivity
import com.wanghoufan.zaijiuyidian.R
import com.wanghoufan.zaijiuyidian.domain.Formatters

/**
 * 到点提醒：独立高优先级通知（闹钟音频流 + 震动）与精确定时兜底。
 * 通知与闹钟只会更新同一条通知，重复触发不会叠加。
 */
object TrainingAlerts {

    const val CHANNEL_ID = "training_alert"
    const val NOTIFICATION_ID = 6802

    const val EXTRA_SESSION_ID = "session_id"
    const val EXTRA_PROJECT_NAME = "project_name"
    const val EXTRA_TARGET_SEC = "target_sec"

    private val vibrationPattern = longArrayOf(0L, 500L, 300L, 500L, 300L, 500L)

    @Volatile
    private var lastNotifyElapsed = 0L

    private fun manager(context: Context): NotificationManager? =
        runCatching { context.getSystemService(NotificationManager::class.java) }.getOrNull()

    private fun alarmManager(context: Context): AlarmManager? =
        runCatching { context.getSystemService(AlarmManager::class.java) }.getOrNull()

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = manager(context) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "训练到点提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "训练达到目标时提醒：闹钟提示音、长震动，锁屏可见；仅在本训练到点时触发"
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            // 震动由 TrainingAlerts.vibrate 统一控制，避免渠道与代码各震一次
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableLights(true)
        }
        runCatching { nm.createNotificationChannel(channel) }
    }

    fun notifyTarget(context: Context, projectName: String?, targetSec: Int, vibrate: Boolean = true) {
        // 服务与精确闹钟可能在到点瞬间同时触发，2 秒内只发一次，避免被系统判为噪声应用
        val now = SystemClock.elapsedRealtime()
        if (now - lastNotifyElapsed < 2_000L) return
        lastNotifyElapsed = now
        ensureChannel(context)
        val nm = manager(context) ?: return
        val name = projectName?.takeIf { it.isNotBlank() } ?: "本次训练"
        val body = "「$name」目标 ${Formatters.clock(targetSec.toLong())} 已到"
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            1,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("训练到点了")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(NOTIFICATION_ID, notification) }
        if (vibrate) vibrate(context)
    }

    fun cancelNotification(context: Context) {
        runCatching { manager(context)?.cancel(NOTIFICATION_ID) }
    }

    fun schedule(
        context: Context,
        sessionId: String,
        projectName: String?,
        targetSec: Int,
        remainingMs: Long
    ) {
        if (remainingMs <= 0L) return
        val am = alarmManager(context) ?: return
        val intent = Intent(context, TargetAlarmReceiver::class.java).apply {
            putExtra(EXTRA_SESSION_ID, sessionId)
            putExtra(EXTRA_PROJECT_NAME, projectName)
            putExtra(EXTRA_TARGET_SEC, targetSec)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            sessionId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = System.currentTimeMillis() + remainingMs
        runCatching {
            if (shouldDowngradeExact(am)) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        }
    }

    fun cancelAlarm(context: Context, sessionId: String) {
        val am = alarmManager(context) ?: return
        val intent = Intent(context, TargetAlarmReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            sessionId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        runCatching { am.cancel(pending) }
        runCatching { pending.cancel() }
    }

    fun cancelAll(context: Context, sessionId: String?) {
        if (sessionId != null) cancelAlarm(context, sessionId)
        cancelNotification(context)
    }

    fun isExactAlarmAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = alarmManager(context) ?: return false
        return runCatching { am.canScheduleExactAlarms() }.getOrDefault(false)
    }

    private fun shouldDowngradeExact(am: AlarmManager): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return runCatching { !am.canScheduleExactAlarms() }.getOrDefault(true)
    }

    private fun vibrate(context: Context) {
        val vibrator = runCatching { context.getSystemService(Vibrator::class.java) }.getOrNull() ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(vibrationPattern, -1)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(vibrationPattern, -1)
            }
        }
    }
}