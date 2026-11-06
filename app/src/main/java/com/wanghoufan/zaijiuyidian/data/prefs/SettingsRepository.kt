package com.wanghoufan.zaijiuyidian.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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
        val lastTimeProject = stringPreferencesKey("last_time_project_id")
        val lastRepsProject = stringPreferencesKey("last_reps_project_id")
        val theme = stringPreferencesKey("theme")
        val sound = booleanPreferencesKey("sound_enabled")
        val vibration = booleanPreferencesKey("vibration_enabled")
        val voice = booleanPreferencesKey("voice_enabled")
        val rangeDays = intPreferencesKey("range_days")
        val chart = stringPreferencesKey("chart_mode")
        val projectFilter = stringPreferencesKey("project_filter")
        val fontScale = intPreferencesKey("font_scale_percent")
        val autoBackupEnabled = booleanPreferencesKey("auto_backup_enabled")
        val autoBackupTreeUri = stringPreferencesKey("auto_backup_tree_uri")
        val autoBackupLastSuccess = longPreferencesKey("auto_backup_last_success")
        val autoBackupLastError = stringPreferencesKey("auto_backup_last_error")
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
            ),
            autoBackupEnabled = prefs[Keys.autoBackupEnabled] ?: false,
            autoBackupTreeUri = prefs[Keys.autoBackupTreeUri],
            autoBackupLastSuccessAt = prefs[Keys.autoBackupLastSuccess],
            autoBackupLastError = prefs[Keys.autoBackupLastError],
            lastTimeProjectId = prefs[Keys.lastTimeProject],
            lastRepsProjectId = prefs[Keys.lastRepsProject]
        )
    }

    suspend fun setSelectedProject(id: String) {
        context.settingsStore.edit { it[Keys.selectedProject] = id }
    }

    suspend fun setLastTimeProject(id: String) {
        context.settingsStore.edit { it[Keys.lastTimeProject] = id }
    }

    suspend fun setLastRepsProject(id: String) {
        context.settingsStore.edit { it[Keys.lastRepsProject] = id }
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

    suspend fun setAutoBackup(enabled: Boolean, treeUri: String?) {
        context.settingsStore.edit {
            it[Keys.autoBackupEnabled] = enabled
            if (treeUri == null) it.remove(Keys.autoBackupTreeUri) else it[Keys.autoBackupTreeUri] = treeUri
        }
    }

    suspend fun setAutoBackupLastSuccess(at: Long) {
        context.settingsStore.edit {
            it[Keys.autoBackupLastSuccess] = at
            it.remove(Keys.autoBackupLastError)
        }
    }

    suspend fun setAutoBackupError(message: String?) {
        context.settingsStore.edit {
            if (message == null) it.remove(Keys.autoBackupLastError) else it[Keys.autoBackupLastError] = message
        }
    }
}
