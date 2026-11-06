package com.wanghoufan.zaijiuyidian.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.wanghoufan.zaijiuyidian.MainActivity
import com.wanghoufan.zaijiuyidian.R
import com.wanghoufan.zaijiuyidian.data.db.AppDatabase
import com.wanghoufan.zaijiuyidian.data.prefs.SettingsRepository
import com.wanghoufan.zaijiuyidian.domain.Formatters
import com.wanghoufan.zaijiuyidian.domain.TimerMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 训练前台服务：用户在前台开始训练后，锁屏/切后台仍按单调时钟播报语音。
 * 不监听开机广播，不承诺被系统强制停止后自动恢复。
 */
class TrainingForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var db: AppDatabase
    private lateinit var settingsRepo: SettingsRepository
    private var ticker: Job? = null
    private var lastIntervalMark = 0
    private var sessionStartedWall: Long? = null
    private var tone: ToneGenerator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastNotificationSec = -1L

    private val bootCount: Int by lazy {
        runCatching {
            Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1)
        }.getOrDefault(-1)
    }

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.get(this)
        settingsRepo = SettingsRepository(this)
        TrainingServiceState.active.value = true
        createChannel()
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification("准备中", 0, 0, paused = false),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                }
            )
        } catch (e: Exception) {
            android.util.Log.e("P068Voice", "startForeground failed", e)
            TrainingServiceState.active.value = false
            stopSelf()
            return
        }
        acquireWakeLock()
        ticker = scope.launch { tick() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 系统重建或用户重新打开 APP 时读取数据库；没有活动训练立即停止。
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        ticker?.cancel()
        scope.cancel()
        releaseWakeLock()
        runCatching { tone?.release() }
        tone = null
        TrainingServiceState.active.value = false
        super.onDestroy()
    }

    private suspend fun tick() {
        while (scope.isActive) {
            val active = db.activeSessionDao().get()
            if (active == null || active.interrupted) {
                stopSelf()
                return
            }
            val nowElapsed = SystemClock.elapsedRealtime()
            val nowWall = System.currentTimeMillis()
            if (TimerMath.isRebooted(active, nowWall, nowElapsed, bootCount)) {
                stopSelf()
                return
            }
            if (sessionStartedWall != active.startedAtWall) {
                sessionStartedWall = active.startedAtWall
                lastIntervalMark = TimerMath.intervalMark(TimerMath.elapsedMs(active, nowElapsed))
            }
            val elapsed = TimerMath.elapsedMs(active, nowElapsed)
            val paused = TimerMath.isPaused(active)
            val settings = settingsRepo.settings.first()

            if (!paused) {
                val mark = TimerMath.intervalMark(elapsed)
                if (mark > lastIntervalMark) {
                    lastIntervalMark = mark
                    if (mark >= 1) {
                        playCue(
                            settings.voiceEnabled,
                            settings.soundEnabled,
                            settings.vibrationEnabled,
                            interval = mark
                        )
                    }
                }
                if (elapsed >= active.targetSec * 1000L && !active.notifiedAtTarget) {
                    db.activeSessionDao().upsert(active.copy(notifiedAtTarget = true))
                    playCue(
                        settings.voiceEnabled,
                        settings.soundEnabled,
                        settings.vibrationEnabled,
                        target = true
                    )
                }
            }

            updateNotification(active, elapsed, paused)
            delay(500)
        }
    }

    private fun playCue(
        voiceEnabled: Boolean,
        soundEnabled: Boolean,
        vibrationEnabled: Boolean,
        interval: Int = 0,
        target: Boolean = false
    ) {
        if (vibrationEnabled) {
            vibrate(if (target) longArrayOf(100, 70, 100) else longArrayOf(50))
        }
        val played = if (voiceEnabled) {
            if (target) VoiceCues.playTarget() else VoiceCues.playInterval(interval)
        } else {
            false
        }
        if (!played && soundEnabled) {
            runCatching {
                if (tone == null) tone = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
                tone?.startTone(
                    if (target) ToneGenerator.TONE_PROP_BEEP else ToneGenerator.TONE_PROP_ACK,
                    160
                )
            }
        }
        android.util.Log.i(
            "P068Voice",
            "service cue interval=$interval target=$target voicePlayed=$played voice=$voiceEnabled sound=$soundEnabled"
        )
    }

    private fun vibrate(pattern: LongArray) {
        val vibrator = runCatching { getSystemService(Vibrator::class.java) }.getOrNull() ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        }
    }

    private fun updateNotification(
        active: com.wanghoufan.zaijiuyidian.data.db.ActiveSessionEntity,
        elapsedMs: Long,
        paused: Boolean
    ) {
        val sec = elapsedMs / 1000
        if (sec == lastNotificationSec) return
        lastNotificationSec = sec
        val title = when {
            paused -> "训练已暂停"
            sec >= active.targetSec -> "目标已达到"
            else -> "训练进行中"
        }
        val text = if (paused) {
            "${Formatters.clock(sec)} · 目标 ${Formatters.clock(active.targetSec.toLong())}"
        } else {
            "${Formatters.clock(sec)} / ${Formatters.clock(active.targetSec.toLong())}"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(title, sec, active.targetSec, paused, text))
    }

    private fun buildNotification(
        title: String,
        elapsedSec: Long,
        targetSec: Int,
        paused: Boolean,
        text: String? = null
    ): Notification {
        val content = text ?: "$elapsedSec / $targetSec"
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "训练计时",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "训练进行中与暂停状态；语音由应用内播放"
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "P068:training").apply {
            setReferenceCounted(false)
            acquire(30 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
    }

    companion object {
        const val CHANNEL_ID = "training_timer"
        const val NOTIFICATION_ID = 6801
    }
}
