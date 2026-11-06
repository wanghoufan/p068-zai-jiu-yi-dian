package com.wanghoufan.zaijiuyidian

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
