package com.notificationlogger.util

import android.content.Context
import android.content.SharedPreferences

class PrefsHelper(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("notification_logger_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_CONTENT_LOGGING  = "content_logging_enabled"
        const val KEY_RETENTION_DAYS   = "retention_days"
        const val KEY_FILTER_MODE      = "filter_mode"   // "NONE" | "BLACKLIST" | "WHITELIST"
        const val KEY_BIOMETRIC        = "biometric_enabled"
        const val KEY_DOOM_THRESHOLD   = "doomscroll_threshold"

        const val DEFAULT_RETENTION    = 30
        const val DEFAULT_THRESHOLD    = 5
    }

    fun isContentLoggingEnabled() = prefs.getBoolean(KEY_CONTENT_LOGGING, true)
    fun setContentLogging(enabled: Boolean) = prefs.edit().putBoolean(KEY_CONTENT_LOGGING, enabled).apply()

    fun getRetentionDays() = prefs.getInt(KEY_RETENTION_DAYS, DEFAULT_RETENTION)
    fun setRetentionDays(days: Int) = prefs.edit().putInt(KEY_RETENTION_DAYS, days).apply()

    fun getFilterMode() = prefs.getString(KEY_FILTER_MODE, "NONE") ?: "NONE"
    fun setFilterMode(mode: String) = prefs.edit().putString(KEY_FILTER_MODE, mode).apply()

    fun isBiometricEnabled() = prefs.getBoolean(KEY_BIOMETRIC, false)
    fun setBiometricEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_BIOMETRIC, enabled).apply()

    fun getDoomScrollThreshold() = prefs.getInt(KEY_DOOM_THRESHOLD, DEFAULT_THRESHOLD)
    fun setDoomScrollThreshold(n: Int) = prefs.edit().putInt(KEY_DOOM_THRESHOLD, n).apply()
}
