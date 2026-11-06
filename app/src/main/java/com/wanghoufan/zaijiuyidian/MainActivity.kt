package com.wanghoufan.zaijiuyidian

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wanghoufan.zaijiuyidian.domain.ThemeMode
import com.wanghoufan.zaijiuyidian.ui.AppRoot
import com.wanghoufan.zaijiuyidian.ui.AppViewModel
import com.wanghoufan.zaijiuyidian.ui.theme.ZaiJiuTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 拒绝时前台服务仍尝试启动；不可用则仅在应用内提醒 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            val dark = state.settings.theme == ThemeMode.DARK
            ZaiJiuTheme(
                darkTheme = dark,
                fontScale = state.settings.fontScalePercent / 100f
            ) {
                val view = LocalView.current
                SideEffect {
                    val window = this.window
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
                    WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !dark
                }
                LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.onForeground() }
                LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.onBackground() }
                AppRoot(state, vm)
            }
        }
    }
}
