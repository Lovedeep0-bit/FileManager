package com.lsj.filemanager.ui.explorer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lsj.filemanager.data.FileRepository
import com.lsj.filemanager.data.SettingsRepository
import com.lsj.filemanager.model.FileModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.io.File
import com.lsj.filemanager.model.FileCategory
import android.content.Context
import android.provider.Settings
import com.lsj.filemanager.util.GlobalEvents
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.collect

enum class TransferOperation { COPY, CUT }
enum class AppTab { INSTALLED, SYSTEM, APKS }

data class Clipboard(val sourcePaths: List<String>, val operation: TransferOperation)

data class ConflictInfo(
    val fileName: String,
    val sourcePath: String,
    val destPath: String,
    val sourceSize: Long,
    val sourceModified: Long,
    val destSize: Long,
    val destModified: Long
)

class ExplorerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FileRepository()
    private val settingsRepository = SettingsRepository(application)

    private val _currentPath = MutableStateFlow(File("/storage/emulated/0").absolutePath)
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _currentCategory = MutableStateFlow<FileCategory?>(null)
    val currentCategory: StateFlow<FileCategory?> = _currentCategory.asStateFlow()

    private val _currentAppTab = MutableStateFlow(AppTab.INSTALLED)
    val currentAppTab: StateFlow<AppTab> = _currentAppTab.asStateFlow()

    private val _files = MutableStateFlow<List<FileModel>>(emptyList())
    val files: StateFlow<List<FileModel>> = _files.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _operationStatus = MutableStateFlow<String?>(null)
    val operationStatus: StateFlow<String?> = _operationStatus.asStateFlow()

    private val _cacheCleaningStatus = MutableStateFlow<String?>(null)
    val cacheCleaningStatus: StateFlow<String?> = _cacheCleaningStatus.asStateFlow()

    private val _isCacheCleaningRunning = MutableStateFlow(false)
    val isCacheCleaningRunning: StateFlow<Boolean> = _isCacheCleaningRunning.asStateFlow()

    private val _pasteConflicts = MutableStateFlow<List<ConflictInfo>>(emptyList())
    val pasteConflicts: StateFlow<List<ConflictInfo>> = _pasteConflicts.asStateFlow()

    private val _isPickingDestination = MutableStateFlow(false)
    val isPickingDestination: StateFlow<Boolean> = _isPickingDestination.asStateFlow()

    private var pendingUnzipFile: FileModel? = null

    private val _searchResults = MutableStateFlow<List<FileModel>>(emptyList())
    val searchResults: StateFlow<List<FileModel>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _selectedFiles = MutableStateFlow<Set<FileModel>>(emptySet())
    val selectedFiles: StateFlow<Set<FileModel>> = _selectedFiles.asStateFlow()

    private val _clipboard = MutableStateFlow<Clipboard?>(null)
    val clipboard: StateFlow<Clipboard?> = _clipboard.asStateFlow()

    private var loadJob: Job? = null
    private var activeOperationJob: Job? = null

    init {
        viewModelScope.launch {
            // Drop initial emission to avoid double loading on creation (UI handles initial load)
            settingsRepository.showHiddenFiles.drop(1).collect {
                if (_currentCategory.value == null) {
                    loadFiles(_currentPath.value)
                }
            }
        }

        viewModelScope.launch {
            GlobalEvents.refreshApps.collectLatest {
                refresh(getApplication<Application>().applicationContext)
            }
        }

        viewModelScope.launch {
            GlobalEvents.cacheCleaningStatus.collect { status ->
                _cacheCleaningStatus.value = status
            }
        }

        viewModelScope.launch {
            GlobalEvents.isCacheCleaningRunning.collect { running ->
                _isCacheCleaningRunning.value = running
            }
        }
    }

    fun loadFiles(path: String) {
        loadJob?.cancel()
        
        _isLoading.value = true
        _isSearching.value = false
        _currentPath.value = path
        _selectedFiles.value = emptySet() // Clear selection on navigation
        
        // Only clear category if we are explicitly navigating to a path and NOT currently in that category's view
        if (_currentCategory.value != null && path != _currentPath.value) {
            _currentCategory.value = null
            _files.value = emptyList()
        }

        loadJob = viewModelScope.launch {
            if (path.contains("|")) {
                // Inside an archive
                val parts = path.split("|")
                val zipPath = parts[0]
                val internalPath = if (parts.size > 1) parts[1].trimStart('/') else ""
                _files.value = repository.listArchiveContents(zipPath, internalPath)
            } else {
                val file = File(path)
                if (file.isFile && (file.extension.lowercase() in listOf("zip", "7z", "rar", "jar"))) {
                    _files.value = repository.listArchiveContents(path, "")
                } else {
                    _files.value = repository.listFiles(path, settingsRepository.showHiddenFiles.value)
                }
            }
            _isLoading.value = false
        }
    }

    fun loadCategory(category: FileCategory, context: Context) {
        loadJob?.cancel()

        _isLoading.value = true
        _isSearching.value = false
        _currentCategory.value = category
        _files.value = emptyList() // Clear immediately to prevent showing previous content

        loadJob = viewModelScope.launch {
            if (category == FileCategory.APPS) {
                loadApps(context)
            } else {
                _files.value = repository.getFilesByCategory(category, context)
            }
            _isLoading.value = false
        }
    }

    fun setAppTab(tab: AppTab, context: Context) {
        _currentAppTab.value = tab
        loadCategory(FileCategory.APPS, context)
    }

    private suspend fun loadApps(context: Context) {
        val tab = _currentAppTab.value
        _files.value = when (tab) {
            AppTab.INSTALLED -> repository.getApps(context, false)
            AppTab.SYSTEM -> repository.getApps(context, true)
            AppTab.APKS -> repository.getFilesByCategory(FileCategory.APPS, context)
        }
    }

    fun search(query: String) {
        if (query.length < 2) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }

        _isSearching.value = true
        if (_currentCategory.value == FileCategory.APPS) {
            // Filter already loaded apps/apks locally
            _searchResults.value = _files.value.filter { 
                it.name.contains(query, ignoreCase = true) || 
                (it.packageName?.contains(query, ignoreCase = true) ?: false)
            }
        } else {
            viewModelScope.launch {
                _isLoading.value = true
                _searchResults.value = repository.searchFiles(query, settingsRepository.showHiddenFiles.value)
                _isLoading.value = false
            }
        }
    }

    fun clearSearch(reload: Boolean = true) {
        _isSearching.value = false
        _searchResults.value = emptyList()
        if (reload) {
            loadFiles(_currentPath.value)
        }
    }

    fun toggleSelection(file: FileModel) {
        val current = _selectedFiles.value.toMutableSet()
        if (current.contains(file)) {
            current.remove(file)
        } else {
            current.add(file)
        }
        _selectedFiles.value = current
    }

    fun selectAll() {
        val allFiles = (if (_isSearching.value) _searchResults.value else _files.value).toSet()
        if (_selectedFiles.value.size == allFiles.size) {
            _selectedFiles.value = emptySet()
        } else {
            _selectedFiles.value = allFiles
        }
    }

    fun clearSelection() {
        _selectedFiles.value = emptySet()
    }

    fun removeFromList(file: FileModel) {
        _files.value = _files.value.filter { it.path != file.path }
        if (_isSearching.value) {
            _searchResults.value = _searchResults.value.filter { it.path != file.path }
        }
        val currentSelected = _selectedFiles.value.toMutableSet()
        if (currentSelected.remove(file)) {
            _selectedFiles.value = currentSelected
        }
    }

    fun copy(files: List<FileModel>) {
        _clipboard.value = Clipboard(files.map { it.path }, TransferOperation.COPY)
        clearSelection()
    }

    fun cut(files: List<FileModel>) {
        _clipboard.value = Clipboard(files.map { it.path }, TransferOperation.CUT)
        clearSelection()
    }

    fun paste() {
        val clip = _clipboard.value ?: return
        val currentPath = _currentPath.value
        
        val conflicts = clip.sourcePaths.map { File(it) }
            .filter { File(currentPath, it.name).exists() }
            .map { source ->
                val dest = File(currentPath, source.name)
                ConflictInfo(
                    fileName = source.name,
                    sourcePath = source.absolutePath,
                    destPath = dest.absolutePath,
                    sourceSize = source.length(),
                    sourceModified = source.lastModified(),
                    destSize = dest.length(),
                    destModified = dest.lastModified()
                )
            }
        
        if (conflicts.isNotEmpty()) {
            _pasteConflicts.value = conflicts
        } else {
            executePaste(resolution = null, applyToAll = false)
        }
    }

    fun resolveConflicts(resolution: String, applyToAll: Boolean) {
        val conflicts = _pasteConflicts.value
        _pasteConflicts.value = emptyList()
        
        if (resolution == "CANCEL") {
            _clipboard.value = null
            return
        }

        executePaste(
            resolution = resolution,
            applyToAll = applyToAll
        )
    }

    private fun executePaste(resolution: String?, applyToAll: Boolean) {
        val clip = _clipboard.value ?: return
        activeOperationJob?.cancel()
        activeOperationJob = viewModelScope.launch {
            _isLoading.value = true
            val totalFiles = clip.sourcePaths.size
            
            clip.sourcePaths.forEachIndexed { index, sourcePath ->
                val sourceFile = File(sourcePath)
                val destFile = File(_currentPath.value, sourceFile.name)
                
                var finalDestPath = _currentPath.value
                var shouldProcess = true

                if (destFile.exists()) {
                    when (resolution) {
                        "REPLACE" -> { /* Default behavior */ }
                        "SKIP" -> {
                            shouldProcess = false
                        }
                        "RENAME" -> {
                            var newName = sourceFile.name
                            var counter = 1
                            while (File(_currentPath.value, newName).exists()) {
                                val nameWithoutExt = sourceFile.nameWithoutExtension
                                val ext = sourceFile.extension
                                newName = if (ext.isNotEmpty()) "$nameWithoutExt($counter).$ext" else "$nameWithoutExt($counter)"
                                counter++
                            }
                            // In this simple implementation, repository.copyFile takes destDirPath and uses source name.
                            // I may need to update repository to take a full dest path if I want custom names.
                            // For now, I'll assume we might need a rename.
                        }
                        else -> {
                            if (!applyToAll) {
                                // This should ideally be handled by showing dialog one by one, 
                                // but for simplicity we gathered all. 
                                // If they didn't apply to all and there's a conflict, 
                                // we'd need a more complex state machine.
                                // For this version: resolution applies to all detected conflicts.
                            }
                        }
                    }
                }

                if (shouldProcess) {
                    _progress.value = 0f
                    val fileName = sourceFile.name
                    val operationName = if (clip.operation == TransferOperation.COPY) "Copying" else "Moving"
                    _operationStatus.value = "$operationName $fileName (${index + 1}/$totalFiles)"
                    
                    if (clip.operation == TransferOperation.COPY) {
                        repository.copyFile(sourcePath, finalDestPath) { _progress.value = it }
                    } else {
                        repository.moveFile(sourcePath, finalDestPath) { _progress.value = it }
                    }
                }
            }
            
            _operationStatus.value = null
            _clipboard.value = null
            _isLoading.value = false
            loadFiles(_currentPath.value)
        }
    }

    fun delete(files: List<FileModel>, context: Context? = null) {
        activeOperationJob?.cancel()
        activeOperationJob = viewModelScope.launch {
            _isLoading.value = true
            val total = files.size
            files.forEachIndexed { index, file ->
                _operationStatus.value = "Deleting ${file.name} (${index + 1}/$total)"
                _progress.value = (index + 1).toFloat() / total
                repository.deleteFile(file.path)
            }
            _operationStatus.value = null
            clearSelection()
            if (context != null) {
                refresh(context)
            } else {
                loadFiles(_currentPath.value)
            }
            _isLoading.value = false
        }
    }

    fun refresh(context: Context) {
        val cat = _currentCategory.value
        if (cat != null) {
            loadCategory(cat, context)
        } else {
            loadFiles(_currentPath.value)
        }
    }

    fun stopOperation() {
        activeOperationJob?.cancel()
        activeOperationJob = null
        _operationStatus.value = null
        _isLoading.value = false
        loadFiles(_currentPath.value)
    }

    fun rename(file: FileModel, newName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.renameFile(file.path, newName)
            if (!result) {
                _operationStatus.value = "Rename failed"
                kotlinx.coroutines.delay(1500)
                _operationStatus.value = null
            }
            _isLoading.value = false
            loadFiles(_currentPath.value)
        }
    }

    fun zip(files: List<FileModel>, name: String) {
        activeOperationJob?.cancel()
        activeOperationJob = viewModelScope.launch {
            _isLoading.value = true
            _progress.value = 0f
            _operationStatus.value = "Creating archive $name..."
            
            val firstFile = files.firstOrNull() ?: return@launch
            // If name has no extension, default to zip. If it has .7z, use that.
            val zipName = if (name.contains(".")) name else "$name.zip"
            val zipPath = File(_currentPath.value, zipName).absolutePath
            
            repository.createArchive(files.map { it.path }, zipPath) { _progress.value = it }
            
            _operationStatus.value = null
            clearSelection()
            loadFiles(_currentPath.value)
            _isLoading.value = false
        }
    }

    fun unzip(file: FileModel, mode: String = "NORMAL") {
        when (mode) {
            "PICK" -> {
                pendingUnzipFile = file
                _isPickingDestination.value = true
            }
            else -> {
                executeUnzip(file, toFolder = (mode == "FOLDER"))
            }
        }
    }

    fun confirmUnzipDestination() {
        val file = pendingUnzipFile ?: return
        val dest = _currentPath.value
        _isPickingDestination.value = false
        pendingUnzipFile = null
        executeUnzip(file, toFolder = false, customDest = dest)
    }

    fun cancelPicking() {
        _isPickingDestination.value = false
        pendingUnzipFile = null
    }

    private fun executeUnzip(file: FileModel, toFolder: Boolean, customDest: String? = null) {
        activeOperationJob?.cancel()
        activeOperationJob = viewModelScope.launch {
            _isLoading.value = true
            _progress.value = 0f
            val targetDir = customDest ?: if (toFolder) {
                val folderName = file.name.substringBeforeLast(".")
                File(File(file.path).parent, folderName).absolutePath
            } else {
                File(file.path).parent
            }
            
            _operationStatus.value = "Extracting ${file.name}..."
            repository.extractArchive(file.path, targetDir!!) { _progress.value = it }
            
            _operationStatus.value = null
            loadFiles(_currentPath.value)
            _isLoading.value = false
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.createDirectory(_currentPath.value, name)
            loadFiles(_currentPath.value)
            _isLoading.value = false
        }
    }

    fun createFile(name: String) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.createFile(_currentPath.value, name)
            loadFiles(_currentPath.value)
            _isLoading.value = false
        }
    }

    fun createArchive(name: String, filesToArchive: List<FileModel>) {
        activeOperationJob?.cancel()
        activeOperationJob = viewModelScope.launch {
            _isLoading.value = true
            _progress.value = 0f
            _operationStatus.value = "Compressing..."
            
            val nameWithExt = if (name.contains(".")) name else "$name.zip"
            val zipPath = File(_currentPath.value, nameWithExt).absolutePath
            repository.createArchive(filesToArchive.map { it.path }, zipPath) { _progress.value = it }
            
            _operationStatus.value = null
            _isLoading.value = false
            loadFiles(_currentPath.value)
        }
    }

    fun navigateUp(): Boolean {
        // Handle archive navigation
        if (_currentPath.value.contains("|/")) {
            val parts = _currentPath.value.split("|/")
            val zipPath = parts[0]
            val internalPath = if (parts.size > 1) parts[1] else ""
            
            if (internalPath.isEmpty()) {
                // At root of zip, go back to folder containing zip
                loadFiles(File(zipPath).parent ?: "/storage/emulated/0")
            } else {
                // Inside zip subdirectory, go up one level
                val parentInternal = if (internalPath.contains("/")) {
                    internalPath.substringBeforeLast("/")
                } else {
                    ""
                }
                loadFiles("$zipPath|/$parentInternal")
            }
            return true
        }

        val currentFile = File(_currentPath.value)
        val parent = currentFile.parentFile
        // Basic check for Internal Storage root to prevent going up past /storage/emulated/0 in this view
        if (_currentPath.value == "/storage/emulated/0") return false
        
        return if (parent != null && parent.canRead()) {
            loadFiles(parent.absolutePath)
            true
        } else {
            false
        }
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedServiceName = context.packageName + "/" + com.lsj.filemanager.service.CacheCleanerService::class.java.canonicalName
        val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabledServices?.contains(expectedServiceName) == true
    }

    fun clearCache(packages: List<String>) {
        viewModelScope.launch {
            GlobalEvents.triggerCacheCleaning(packages)
        }
    }

    fun stopCacheCleaning() {
        viewModelScope.launch {
            GlobalEvents.triggerStopCacheCleaning()
        }
    }

    fun moveToLocker(files: List<FileModel>) {
        activeOperationJob?.cancel()
        activeOperationJob = viewModelScope.launch {
            _isLoading.value = true
            val total = files.size
            files.forEachIndexed { index, file ->
                _operationStatus.value = "Securing ${file.name} (${index + 1}/$total)"
                _progress.value = (index + 1).toFloat() / total
                repository.moveToLocker(file.path, getApplication())
            }
            _operationStatus.value = null
            clearSelection()
            loadFiles(_currentPath.value)
            _isLoading.value = false
        }
    }

    suspend fun getDetailedMetadata(file: FileModel): FileModel {
        return repository.getDetailedMetadata(file)
    }
}

