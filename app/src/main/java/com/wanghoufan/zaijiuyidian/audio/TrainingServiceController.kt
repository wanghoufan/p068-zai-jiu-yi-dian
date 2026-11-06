package com.wanghoufan.zaijiuyidian.audio

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow

object TrainingServiceState {
    val active = MutableStateFlow(false)
}

object TrainingServiceController {

    fun start(context: Context): Boolean = runCatching {
        ContextCompat.startForegroundService(
            context,
            Intent(context, TrainingForegroundService::class.java)
        )
        true
    }.getOrDefault(false)

    fun stop(context: Context) {
        runCatching { context.stopService(Intent(context, TrainingForegroundService::class.java)) }
    }
}
