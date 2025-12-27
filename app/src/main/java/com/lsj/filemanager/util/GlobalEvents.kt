package com.lsj.filemanager.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object GlobalEvents {
    private val _refreshApps = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val refreshApps: SharedFlow<Unit> = _refreshApps

    private val _startCacheCleaning = MutableSharedFlow<List<String>>(extraBufferCapacity = 1)
    val startCacheCleaning: SharedFlow<List<String>> = _startCacheCleaning

    private val _cacheCleaningStatus = MutableSharedFlow<String?>(extraBufferCapacity = 1)
    val cacheCleaningStatus: SharedFlow<String?> = _cacheCleaningStatus

    private val _isCacheCleaningRunning = MutableStateFlow(false)
    val isCacheCleaningRunning: StateFlow<Boolean> = _isCacheCleaningRunning.asStateFlow()

    private val _stopCacheCleaning = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val stopCacheCleaning: SharedFlow<Unit> = _stopCacheCleaning

    suspend fun triggerRefreshApps() {
        _refreshApps.emit(Unit)
    }

    suspend fun triggerCacheCleaning(packageName: List<String>) {
        _startCacheCleaning.emit(packageName)
    }

    suspend fun triggerStopCacheCleaning() {
        _stopCacheCleaning.emit(Unit)
    }

    fun updateCacheCleaningRunning(isRunning: Boolean) {
        _isCacheCleaningRunning.value = isRunning
    }

    suspend fun updateCacheCleaningStatus(status: String?) {
        _cacheCleaningStatus.emit(status)
    }
}
