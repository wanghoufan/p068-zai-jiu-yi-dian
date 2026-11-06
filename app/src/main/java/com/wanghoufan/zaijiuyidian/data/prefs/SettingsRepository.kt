package com.wanghoufan.zaijiuyidian.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wanghoufan.zaijiuyidian.domain.AppSettings
import com.wanghoufan.zaijiuyidian.domain.ChartMode
import com.wanghoufan.zaijiuyidian.domain.DefaultSettings
import com.wanghoufan.zaijiuyidian.domain.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val selectedProject = stringPreferencesKey("selected_project_id")
        val theme = stringPreferencesKey("theme")
        val sound = booleanPreferencesKey("sound_enabled")
        val vibration = booleanPreferencesKey("vibration_enabled")
        val voice = booleanPreferencesKey("voice_enabled")
        val rangeDays = intPreferencesKey("range_days")
        val chart = stringPreferencesKey("chart_mode")
        val projectFilter = stringPreferencesKey("project_filter")
        val fontScale = intPreferencesKey("font_scale_percent")
    }

    val settings: Flow<AppSettings> = context.settingsStore.data.map { prefs ->
        AppSettings(
            selectedProjectId = prefs[Keys.selectedProject],
            theme = when (prefs[Keys.theme]) {
                ThemeMode.DARK.name -> ThemeMode.DARK
                else -> ThemeMode.LIGHT
            },
            soundEnabled = prefs[Keys.sound] ?: DefaultSettings.soundEnabled,
            vibrationEnabled = prefs[Keys.vibration] ?: DefaultSettings.vibrationEnabled,
            voiceEnabled = prefs[Keys.voice] ?: DefaultSettings.voiceEnabled,
            lastRangeDays = prefs[Keys.rangeDays]?.takeIf { it > 0 },
            lastChart = runCatching { ChartMode.valueOf(prefs[Keys.chart] ?: "") }.getOrDefault(ChartMode.TREND),
            lastProjectFilter = prefs[Keys.projectFilter] ?: "current",
            fontScalePercent = com.wanghoufan.zaijiuyidian.domain.FontScaleOptions.clamp(
                prefs[Keys.fontScale] ?: DefaultSettings.fontScalePercent
            )
        )
    }

    suspend fun setSelectedProject(id: String) {
        context.settingsStore.edit { it[Keys.selectedProject] = id }
    }

    suspend fun setTheme(mode: ThemeMode) {
        context.settingsStore.edit { it[Keys.theme] = mode.name }
    }

    suspend fun setSound(enabled: Boolean) {
        context.settingsStore.edit { it[Keys.sound] = enabled }
    }

    suspend fun setVibration(enabled: Boolean) {
        context.settingsStore.edit { it[Keys.vibration] = enabled }
    }

    suspend fun setVoice(enabled: Boolean) {
        context.settingsStore.edit { it[Keys.voice] = enabled }
    }

    suspend fun setRangeDays(days: Int?) {
        context.settingsStore.edit { prefs ->
            if (days == null) prefs.remove(Keys.rangeDays) else prefs[Keys.rangeDays] = days
        }
    }

    suspend fun setChart(mode: ChartMode) {
        context.settingsStore.edit { it[Keys.chart] = mode.name }
    }

    suspend fun setProjectFilter(filter: String) {
        context.settingsStore.edit { it[Keys.projectFilter] = filter }
    }

    suspend fun setFontScalePercent(percent: Int) {
        context.settingsStore.edit {
            it[Keys.fontScale] = com.wanghoufan.zaijiuyidian.domain.FontScaleOptions.clamp(percent)
        }
    }
}
