package com.lsj.filemanager.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lsj.filemanager.data.AppTheme
import com.lsj.filemanager.data.SettingsRepository
import kotlinx.coroutines.flow.StateFlow

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)
    
    val currentTheme: StateFlow<AppTheme> = repository.currentTheme
    val showHiddenFiles: StateFlow<Boolean> = repository.showHiddenFiles

    fun setTheme(theme: AppTheme) {
        repository.setTheme(theme)
    }

    fun setShowHiddenFiles(show: Boolean) {
        repository.setShowHiddenFiles(show)
    }
}
