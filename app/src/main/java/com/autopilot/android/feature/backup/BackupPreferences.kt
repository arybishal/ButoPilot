package com.autopilot.android.feature.backup

import android.content.Context
import android.content.SharedPreferences

enum class AutoBackupFrequency(val displayName: String, val intervalHours: Long) {
    DAILY("Daily (24 Hours)", 24L),
    WEEKLY("Weekly (7 Days)", 168L)
}

data class AutoBackupSettings(
    val isEnabled: Boolean = false,
    val frequency: AutoBackupFrequency = AutoBackupFrequency.DAILY,
    val retentionCount: Int = 5,
    val includeHistory: Boolean = true
)

class BackupPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "autopilot_backup_preferences"
        private const val KEY_ENABLED = "auto_backup_enabled"
        private const val KEY_FREQUENCY = "auto_backup_frequency"
        private const val KEY_RETENTION = "auto_backup_retention_count"
        private const val KEY_INCLUDE_HISTORY = "auto_backup_include_history"
    }

    fun getSettings(): AutoBackupSettings {
        val enabled = prefs.getBoolean(KEY_ENABLED, false)
        val freqStr = prefs.getString(KEY_FREQUENCY, AutoBackupFrequency.DAILY.name)
        val freq = try {
            AutoBackupFrequency.valueOf(freqStr ?: AutoBackupFrequency.DAILY.name)
        } catch (e: Exception) {
            AutoBackupFrequency.DAILY
        }
        val retention = prefs.getInt(KEY_RETENTION, 5)
        val includeHistory = prefs.getBoolean(KEY_INCLUDE_HISTORY, true)

        return AutoBackupSettings(
            isEnabled = enabled,
            frequency = freq,
            retentionCount = retention.coerceIn(1, 20),
            includeHistory = includeHistory
        )
    }

    fun saveSettings(settings: AutoBackupSettings) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, settings.isEnabled)
            .putString(KEY_FREQUENCY, settings.frequency.name)
            .putInt(KEY_RETENTION, settings.retentionCount)
            .putBoolean(KEY_INCLUDE_HISTORY, settings.includeHistory)
            .apply()
    }
}
