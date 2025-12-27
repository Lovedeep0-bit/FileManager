package com.lsj.filemanager.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppTheme {
    DARK,
    LIGHT,
    OLED
}

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    
    private val _currentTheme = MutableStateFlow(loadTheme())
    val currentTheme: StateFlow<AppTheme> = _currentTheme.asStateFlow()

    private val _showHiddenFiles = MutableStateFlow(loadShowHiddenFiles())
    val showHiddenFiles: StateFlow<Boolean> = _showHiddenFiles.asStateFlow()

    private fun loadTheme(): AppTheme {
        val themeName = prefs.getString("theme", AppTheme.DARK.name) ?: AppTheme.DARK.name
        return try {
            AppTheme.valueOf(themeName)
        } catch (e: Exception) {
            AppTheme.DARK
        }
    }

    private fun loadShowHiddenFiles(): Boolean {
        return prefs.getBoolean("show_hidden_files", false)
    }

    fun setTheme(theme: AppTheme) {
        prefs.edit().putString("theme", theme.name).apply()
        _currentTheme.value = theme
    }

    fun setShowHiddenFiles(show: Boolean) {
        prefs.edit().putBoolean("show_hidden_files", show).apply()
        _showHiddenFiles.value = show
    }

    fun getLockerCode(): String? {
        return prefs.getString("locker_code", null)
    }

    fun setLockerCode(code: String?) {
        prefs.edit().putString("locker_code", code).apply()
    }

    fun isLockerEnabled(): Boolean {
        return !prefs.getString("locker_code", null).isNullOrEmpty()
    }
}
