package com.wanghoufan.zaijiuyidian.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 到点兜底闹钟：只负责发提醒，不读写数据库、不启动训练逻辑。
 */
class TargetAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val projectName = intent.getStringExtra(TrainingAlerts.EXTRA_PROJECT_NAME)
        val targetSec = intent.getIntExtra(TrainingAlerts.EXTRA_TARGET_SEC, 0)
        TrainingAlerts.notifyTarget(context, projectName, targetSec)
    }
}