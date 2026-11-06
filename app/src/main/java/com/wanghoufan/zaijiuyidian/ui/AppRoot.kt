package com.wanghoufan.zaijiuyidian.ui

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wanghoufan.zaijiuyidian.audio.VoiceCues
import com.wanghoufan.zaijiuyidian.domain.Formatters
import com.wanghoufan.zaijiuyidian.ui.components.BottomNav
import com.wanghoufan.zaijiuyidian.ui.components.ConfirmDialog
import com.wanghoufan.zaijiuyidian.ui.components.ToastHost
import com.wanghoufan.zaijiuyidian.ui.screens.HistoryScreen
import com.wanghoufan.zaijiuyidian.ui.screens.SettingsScreen
import com.wanghoufan.zaijiuyidian.ui.screens.SheetsHost
import com.wanghoufan.zaijiuyidian.ui.screens.TrainScreen
import com.wanghoufan.zaijiuyidian.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private data class PendingExport(val name: String, val content: String)

@Composable
fun AppRoot(state: UiState, vm: AppViewModel) {
    val p = AppTheme.palette
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pendingExport by remember { mutableStateOf<PendingExport?>(null) }

    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val pending = pendingExport
        pendingExport = null
        if (uri != null && pending != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(pending.content.toByteArray(Charsets.UTF_8))
                        } ?: error("no stream")
                    }.isSuccess
                }
                vm.showToast(if (ok) "完整备份已导出" else "导出失败，请重试")
            }
        }
    }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val pending = pendingExport
        pendingExport = null
        if (uri != null && pending != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(pending.content.toByteArray(Charsets.UTF_8))
                        } ?: error("no stream")
                    }.isSuccess
                }
                vm.showToast(if (ok) "CSV 已导出" else "导出失败，请重试")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val bytes = input.readBytes()
                        if (bytes.size > 15 * 1024 * 1024) null else bytes.toString(Charsets.UTF_8)
                    }
                }.getOrNull()
            }
            if (text == null) vm.showToast("无法导入：文件超过 15MB 或读取失败")
            else vm.handleImportText(text)
        }
    }

    val tone = remember {
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 70) }.getOrNull()
    }
    DisposableEffect(Unit) {
        onDispose { tone?.release() }
    }
    val vibrator = remember {
        runCatching { context.getSystemService(Vibrator::class.java) }.getOrNull()
    }
    LaunchedEffect(state.voiceCue?.id) {
        val cue = state.voiceCue ?: return@LaunchedEffect
        val pattern = when (cue.kind) {
            VoiceKind.START -> longArrayOf(60)
            VoiceKind.INTERVAL -> longArrayOf(50)
            VoiceKind.TARGET -> longArrayOf(100, 70, 100)
        }
        if (state.settings.vibrationEnabled && vibrator != null) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern, -1)
                }
            }
        }
        val voicePlayed = if (state.settings.voiceEnabled) {
            when (cue.kind) {
                VoiceKind.START -> VoiceCues.playStart()
                VoiceKind.INTERVAL -> VoiceCues.playInterval(cue.minute)
                VoiceKind.TARGET -> VoiceCues.playTarget()
            }
        } else {
            false
        }
        if (!voicePlayed && state.settings.soundEnabled) {
            runCatching {
                tone?.startTone(
                    if (cue.kind == VoiceKind.TARGET) ToneGenerator.TONE_PROP_BEEP
                    else ToneGenerator.TONE_PROP_ACK,
                    160
                )
            }
        }
        android.util.Log.i(
            "P068Voice",
            "cue=${cue.kind} minute=${cue.minute} voicePlayed=$voicePlayed sound=${state.settings.soundEnabled} voice=${state.settings.voiceEnabled}"
        )
        when (cue.kind) {
            VoiceKind.TARGET -> vm.showToast("已达到目标，可以继续坚持！")
            VoiceKind.INTERVAL -> if (!voicePlayed) vm.showToast("已训练 ${cue.minute} 分钟")
            VoiceKind.START -> Unit
        }
    }

    LaunchedEffect(state.toast?.id) {
        val toast = state.toast ?: return@LaunchedEffect
        delay(4200)
        vm.clearToast(toast.id)
    }

    state.interruptedPrompt?.let { interrupted ->
        val seconds = (interrupted.lastKnownElapsedMs / 1000).toInt()
        ConfirmDialog(
            title = "计时被设备重启中断",
            message = "无法确认重启期间的时长。最后可靠记录为 ${Formatters.clock(seconds.toLong())}，完成时间 ${Formatters.dateTime(interrupted.lastKnownAtWall)}。可以按这条记录保存，或放弃本次训练。",
            confirmText = "按此保存",
            onConfirm = { vm.resolveInterrupted(true) },
            onDismiss = { vm.resolveInterrupted(false) }
        )
    }

    Scaffold(
        containerColor = p.bg,
        contentWindowInsets = WindowInsets.statusBars,
        bottomBar = {
            BottomNav(state.tab) { vm.selectTab(it) }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (state.tab) {
                Tab.TRAIN -> TrainScreen(state, vm)
                Tab.HISTORY -> HistoryScreen(state, vm)
                Tab.SETTINGS -> SettingsScreen(
                    state = state,
                    vm = vm,
                    onExportJson = {
                        scope.launch {
                            val content = vm.buildBackupJson()
                            val name = "zai-jiu-yi-dian-backup-${LocalDate.now()}.json"
                            pendingExport = PendingExport(name, content)
                            jsonLauncher.launch(name)
                        }
                    },
                    onExportCsv = {
                        scope.launch {
                            val content = vm.buildCsv()
                            val name = "zai-jiu-yi-dian-history-${LocalDate.now()}.csv"
                            pendingExport = PendingExport(name, content)
                            csvLauncher.launch(name)
                        }
                    },
                    onImport = {
                        importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    }
                )
            }

            ToastHost(
                toast = state.toast,
                onAction = { vm.undoDelete() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp)
            )
        }
    }

    SheetsHost(state, vm)
}
