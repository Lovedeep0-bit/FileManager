package com.lsj.filemanager.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lsj.filemanager.ui.components.FileItem
import com.lsj.filemanager.ui.explorer.ExplorerViewModel

import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.core.content.FileProvider
import com.lsj.filemanager.ui.components.*
import com.lsj.filemanager.model.FileCategory
import com.lsj.filemanager.model.FileModel
import com.lsj.filemanager.ui.explorer.AppTab
import com.lsj.filemanager.data.SettingsRepository
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import android.provider.Settings

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExplorerScreen(
    category: FileCategory? = null,
    onNavigateBack: () -> Unit,
    onMenuClick: () -> Unit,
    onViewFile: (FileModel) -> Unit,
    viewModel: ExplorerViewModel = viewModel()
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val currentPath by viewModel.currentPath.collectAsState()
    val currentCategory by viewModel.currentCategory.collectAsState()
    val currentAppTab by viewModel.currentAppTab.collectAsState()
    val files by viewModel.files.collectAsState()
    
    LaunchedEffect(category) {
        if (category != null) {
            viewModel.loadCategory(category, context)
        } else if (currentCategory == null) {
            viewModel.loadFiles(currentPath)
        }
    }
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedFiles by viewModel.selectedFiles.collectAsState()
    val clipboard by viewModel.clipboard.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val cacheCleaningStatus by viewModel.cacheCleaningStatus.collectAsState()
    val isCacheCleaningRunning by viewModel.isCacheCleaningRunning.collectAsState()
    val operationStatus by viewModel.operationStatus.collectAsState()
    val progress by viewModel.progress.collectAsState()
    
    // Dialog states
    var showRenameDialog by remember { mutableStateOf<FileModel?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var filesToDelete by remember { mutableStateOf<List<FileModel>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var contextMenuFile by remember { mutableStateOf<FileModel?>(null) }
    var showInfoDialog by remember { mutableStateOf<FileModel?>(null) }
    var zipNamingFiles by remember { mutableStateOf<List<FileModel>?>(null) }
    
    val pasteConflicts by viewModel.pasteConflicts.collectAsState()
    val isPickingDestination by viewModel.isPickingDestination.collectAsState()
    
    // Locker PIN state
    var showPinDialog by remember { mutableStateOf(false) }
    var pinDialogAction by remember { mutableStateOf(PinAction.VERIFY) }
    var filesToLock by remember { mutableStateOf<List<FileModel>>(emptyList()) }

    BackHandler {
        if (searchQuery.isNotEmpty()) {
            searchQuery = ""
            viewModel.clearSearch()
        } else if (selectedFiles.isNotEmpty()) {
            viewModel.clearSelection()
        } else if (!viewModel.navigateUp()) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 2.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 4.dp, end = 16.dp)
                    ) {
                        IconButton(onClick = onMenuClick) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                        
                        TextField(
                            value = searchQuery,
                            onValueChange = { 
                                searchQuery = it
                                viewModel.search(it)
                            },
                            modifier = Modifier.weight(1f),
                            placeholder = { 
                                Text(
                                    if (currentCategory == FileCategory.APPS) "Search in Apps" else "Search in Files",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ) 
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            ),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge
                        )
                        
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { 
                                searchQuery = ""
                                viewModel.clearSearch()
                            }) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = selectedFiles.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    actions = {
                        IconButton(onClick = { viewModel.selectAll() }) {
                            val allSelected = selectedFiles.size == (if (isSearching) searchResults.size else files.size)
                            Icon(
                                if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                                contentDescription = if (allSelected) "Deselect All" else "Select All"
                            )
                        }

                        if (currentCategory != FileCategory.APPS) {
                            IconButton(onClick = { viewModel.copy(selectedFiles.toList()) }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                            }
                            IconButton(onClick = { viewModel.cut(selectedFiles.toList()) }) {
                                Icon(Icons.Default.ContentCut, contentDescription = "Move")
                            }
                        }

                        IconButton(onClick = { 
                            val isUninstallCategory = currentCategory == FileCategory.APPS && (currentAppTab == AppTab.INSTALLED || currentAppTab == AppTab.SYSTEM)
                            if (isUninstallCategory) {
                                selectedFiles.forEach { file ->
                                    if (file.packageName != null && !file.isSystemApp) {
                                        val packageUri = android.net.Uri.fromParts("package", file.packageName, null)
                                        val intent = Intent(Intent.ACTION_DELETE, packageUri).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    }
                                }
                                viewModel.clearSelection()
                            } else {
                                filesToDelete = selectedFiles.toList()
                                showDeleteDialog = true 
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = if (currentCategory == FileCategory.APPS && currentAppTab != AppTab.APKS) "Uninstall" else "Delete")
                        }

                        if (currentCategory == FileCategory.APPS && currentAppTab != AppTab.APKS) {
                            IconButton(onClick = {
                                if (!Settings.canDrawOverlays(context)) {
                                    Toast.makeText(context, "Please allow 'Display over other apps' for the overlay", Toast.LENGTH_LONG).show()
                                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))
                                    context.startActivity(intent)
                                } else if (viewModel.isAccessibilityServiceEnabled(context)) {
                                    viewModel.clearCache(selectedFiles.mapNotNull { it.packageName })
                                    viewModel.clearSelection()
                                } else {
                                    Toast.makeText(context, "Please enable accessibility service for automation", Toast.LENGTH_LONG).show()
                                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                }
                            }) {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = "Clear Cache")
                            }
                        }

                        if (currentCategory != FileCategory.APPS) {
                            IconButton(onClick = { 
                                zipNamingFiles = selectedFiles.toList()
                            }) {
                                Icon(Icons.Default.Archive, contentDescription = "Zip")
                            }
                        }
                    },
                    floatingActionButton = {
                        Text(
                            text = "${selectedFiles.size} selected",
                            modifier = Modifier.padding(end = 16.dp),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                )
            }
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (clipboard != null && !isSearching && currentCategory == null) {
                    ExtendedFloatingActionButton(
                        text = { Text("Paste") },
                        icon = { Icon(Icons.Default.ContentPaste, contentDescription = null) },
                        onClick = { viewModel.paste() },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                    Spacer(Modifier.height(16.dp))
                }
                if (!isSearching && currentCategory == null) {
                    var isExpanded by remember { mutableStateOf(false) }
                    var showCreateFolder by remember { mutableStateOf(false) }
                    var showCreateFile by remember { mutableStateOf(false) }
                    
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        if (isExpanded) {
                            SmallFloatingActionButton(
                                onClick = { 
                                    showCreateFile = true
                                    isExpanded = false
                                },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ) {
                                Icon(Icons.Default.InsertDriveFile, contentDescription = "New File")
                            }
                            
                            SmallFloatingActionButton(
                                onClick = { 
                                    showCreateFolder = true 
                                    isExpanded = false
                                },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ) {
                                Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder")
                            }
                        }
                        
                        
                        FloatingActionButton(
                            onClick = { isExpanded = !isExpanded },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Icon(
                                if (isExpanded) Icons.Default.Close else Icons.Default.Add,
                                contentDescription = if (isExpanded) "Close" else "Add"
                            )
                        }
                    }
                    
                    if (showCreateFolder) {
                        InputDialog(
                            title = "New Folder",
                            onConfirm = { 
                                viewModel.createFolder(it)
                                showCreateFolder = false
                            },
                            onDismiss = { showCreateFolder = false }
                        )
                    }
                    
                    if (showCreateFile) {
                        InputDialog(
                            title = "New File",
                            onConfirm = { 
                                viewModel.createFile(it)
                                showCreateFile = false
                            },
                            onDismiss = { showCreateFile = false }
                        )
                    }
                }
            }

            if (isPickingDestination) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 8.dp,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Select Destination",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Row {
                            TextButton(onClick = { viewModel.cancelPicking() }) {
                                Text("CANCEL", color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = { viewModel.confirmUnzipDestination() }) {
                                Text("OK")
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        val displayFiles = if (isSearching) searchResults else files
        
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { 
                        focusManager.clearFocus() 
                        if (searchQuery.isEmpty()) {
                            viewModel.clearSearch(reload = false)
                        }
                    })
                }
        ) {
            if (currentCategory == FileCategory.APPS) {
                PrimaryTabRow(selectedTabIndex = currentAppTab.ordinal) {
                    AppTab.values().forEach { tab ->
                        Tab(
                            selected = currentAppTab == tab,
                            onClick = { 
                                focusManager.clearFocus()
                                viewModel.setAppTab(tab, context) 
                            },
                            text = { 
                                Text(tab.name.lowercase().replaceFirstChar { it.uppercase() }) 
                            }
                        )
                    }
                }
            }

            if (!isSearching && currentCategory == null) {
                Breadcrumbs(
                    currentPath = currentPath,
                    onPathClick = { 
                        focusManager.clearFocus()
                        viewModel.loadFiles(it) 
                    }
                )
            }

    if (isLoading && operationStatus != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { 
                Text(
                    text = if (isCacheCleaningRunning) "Clearing cache..." else (operationStatus ?: ""),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                ) 
            },
            text = {
                Column {
                    if (isCacheCleaningRunning && cacheCleaningStatus != null) {
                        Text(
                            text = cacheCleaningStatus!!,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }
                    
                    LinearProgressIndicator(
                        progress = { if (isCacheCleaningRunning) 0f else progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    
                    if (!isCacheCleaningRunning) {
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            modifier = Modifier.align(Alignment.End).padding(top = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.stopOperation() }) {
                    Text("CANCEL")
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            textContentColor = MaterialTheme.colorScheme.onSurface
        )
    }
        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            if (isLoading && operationStatus == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(
                        items = displayFiles,
                        key = { it.path }
                    ) { file ->
                        FileItem(
                            modifier = Modifier.animateItem(),
                            file = file,
                            isSelected = selectedFiles.contains(file),
                            onClick = {
                                focusManager.clearFocus()
                                if (selectedFiles.isNotEmpty()) {
                                    viewModel.toggleSelection(file)
                                } else {
                                    when {
                                        // Open app info page for installed apps
                                        file.appIconPackageName != null -> {
                                            try {
                                                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                    data = android.net.Uri.parse("package:${file.appIconPackageName}")
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                // Handle error silently
                                            }
                                        }
                                        // Install APK files
                                        file.extension.lowercase() == "apk" && file.appIconPackageName == null -> {
                                            try {
                                                val apkFile = java.io.File(file.path)
                                                val apkUri = FileProvider.getUriForFile(
                                                    context,
                                                    "${context.packageName}.provider",
                                                    apkFile
                                                )
                                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                // Handle error silently
                                            }
                                        }
                                        // Navigate into directories or browse zips
                                        file.isDirectory || file.extension.lowercase() == "zip" -> {
                                            viewModel.loadFiles(file.path)
                                        }
                                        // For other files, try to open them
                                        else -> {
                                            val ext = file.extension.lowercase()
                                            val docExtensions = listOf("pdf", "txt", "log", "json", "xml", "csv", "kt", "java", "py", "html", "css", "js")
                                            
                                            if (ext in docExtensions) {
                                                onViewFile(file)
                                            } else {
                                                try {
                                                    val fileObj = java.io.File(file.path)
                                                    val uri = FileProvider.getUriForFile(
                                                        context,
                                                        "${context.packageName}.provider",
                                                        fileObj
                                                    )
                                                    
                                                    val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
                                                    
                                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                                        setDataAndType(uri, mimeType)
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    
                                                    val chooser = Intent.createChooser(intent, "Open with")
                                                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    context.startActivity(chooser)
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Cannot open file", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                             onLongClick = { contextMenuFile = file },
                             onIconClick = { viewModel.toggleSelection(file) }
                        )
                    }
                    if (displayFiles.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillParentMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (isSearching) "No files found for \"$searchQuery\"" else "No files found",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (contextMenuFile != null) {
        ModalBottomSheet(
            onDismissRequest = { contextMenuFile = null },
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            FileActionSheet(
                file = contextMenuFile!!,
                onAction = { action ->
                    val file = contextMenuFile!!
                    contextMenuFile = null
                    
                    // If the long-pressed file is part of the current selection, apply action to all selected files.
                    // Otherwise, apply only to the long-pressed file.
                    val targetFiles = if (selectedFiles.contains(file)) selectedFiles.toList() else listOf(file)
                    
                    when (action) {
                        FileAction.COPY -> viewModel.copy(targetFiles)
                        FileAction.CUT -> viewModel.cut(targetFiles)
                        FileAction.DELETE -> {
                            val nonSystemApps = targetFiles.filter { it.packageName != null && !it.isSystemApp }
                            if (nonSystemApps.isNotEmpty()) {
                                nonSystemApps.forEach { app ->
                                    val packageUri = android.net.Uri.fromParts("package", app.packageName, null)
                                    val intent = Intent(Intent.ACTION_DELETE, packageUri).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }
                            } else {
                                filesToDelete = targetFiles
                                showDeleteDialog = true
                            }
                        }
                        FileAction.RENAME -> {
                            // Rename usually only makes sense for a single file. 
                            // If multiple selected, maybe we shouldn't allow rename from here or just rename the single one?
                            // Standard behavior: Rename only the specific file clicked, or disable option.
                            // Let's stick to single file for rename to avoid complexity of batch rename UI for now.
                            showRenameDialog = file 
                        }
                        FileAction.COMPRESS -> {
                            zipNamingFiles = targetFiles
                        }
                        FileAction.EXTRACT -> viewModel.unzip(file, mode = "NORMAL") // Unzip usually per file, or we'd need loop
                        FileAction.EXTRACT_TO_FOLDER -> viewModel.unzip(file, mode = "FOLDER")
                        FileAction.EXTRACT_TO_CUSTOM -> viewModel.unzip(file, mode = "PICK")
                        FileAction.OPEN_AS_ARCHIVE -> viewModel.loadFiles(file.path)
                        FileAction.INFORMATION -> showInfoDialog = file
                        FileAction.APP_INFO -> {
                            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = android.net.Uri.parse("package:${file.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                        FileAction.UNINSTALL -> {
                            if (file.packageName != null && !file.isSystemApp) {
                                val packageUri = android.net.Uri.fromParts("package", file.packageName, null)
                                val intent = Intent(Intent.ACTION_DELETE, packageUri).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }
                        }
                        FileAction.MOVE_TO_LOCKER -> {
                            val settingsRepo = SettingsRepository(context)
                            val isLockerEnabled = settingsRepo.isLockerEnabled()
                             
                            pinDialogAction = if (isLockerEnabled) {
                                PinAction.VERIFY
                            } else {
                                PinAction.SETUP
                            }
                            filesToLock = targetFiles
                            showPinDialog = true
                        }
                        FileAction.CLEAR_CACHE -> {
                            val packages = targetFiles.mapNotNull { it.packageName }
                            if (packages.isNotEmpty()) {
                                if (!Settings.canDrawOverlays(context)) {
                                    Toast.makeText(context, "Please allow 'Display over other apps' for the overlay", Toast.LENGTH_LONG).show()
                                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))
                                    context.startActivity(intent)
                                } else if (viewModel.isAccessibilityServiceEnabled(context)) {
                                    viewModel.clearCache(packages)
                                } else {
                                    Toast.makeText(context, "Please enable accessibility service for automation", Toast.LENGTH_LONG).show()
                                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                }
                            }
                        }
                        FileAction.SHARE -> {
                            val uris = ArrayList<android.net.Uri>()
                            targetFiles.forEach { f ->
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.provider",
                                    java.io.File(f.path)
                                )
                                uris.add(uri)
                            }
                            
                            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                                type = "*/*" // Simplified type
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share"))
                        }
                        else -> {}
                    }
                }
            )
        }
    }


        // ModalBottomSheet for single file context menu removed
        
        if (showDeleteDialog && filesToDelete.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { 
                    showDeleteDialog = false
                    filesToDelete = emptyList()
                },
                title = { 
                    Text(
                        "Delete ${if (filesToDelete.size > 1) "${filesToDelete.size} items" else "Item"}?",
                        style = MaterialTheme.typography.titleLarge
                    ) 
                },
                text = { Text("Are you sure you want to delete ${if (filesToDelete.size > 1) "selected items" else "'${filesToDelete.first().name}'"}? This action cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.delete(filesToDelete, context)
                            showDeleteDialog = false
                            filesToDelete = emptyList()
                        }
                    ) {
                        Text(
                            "DELETE", 
                            color = MaterialTheme.colorScheme.error, 
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        showDeleteDialog = false
                        filesToDelete = emptyList()
                    }) {
                        Text("CANCEL")
                    }
                },
                shape = RoundedCornerShape(28.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                textContentColor = MaterialTheme.colorScheme.onSurface
            )
        }

        showRenameDialog?.let { file ->
            RenameDialog(
                initialName = file.name,
                onConfirm = { newName ->
                    viewModel.rename(file, newName)
                    showRenameDialog = null
                },
                onDismiss = { showRenameDialog = null }
            )
        }

        zipNamingFiles?.let { files ->
            InputDialog(
                title = "Create Archive",
                initialValue = if (files.size == 1) files.first().name else "Archive",
                onConfirm = { name ->
                    viewModel.zip(files, name)
                    zipNamingFiles = null
                },
                onDismiss = { zipNamingFiles = null }
            )
        }

        if (pasteConflicts.isNotEmpty()) {
            val conflict = pasteConflicts.first()
            var applyToAll by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { viewModel.resolveConflicts("CANCEL", false) },
                title = { Text("Overwrite file?") },
                text = { 
                    Column {
                        val dateFormat = java.text.SimpleDateFormat("dd/MM/yy h:mm a", java.util.Locale.getDefault())
                        fun formatSize(size: Long) = android.text.format.Formatter.formatFileSize(context, size)

                        Text("File ${conflict.fileName} already exists.", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text("Source file", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text("Size: ${formatSize(conflict.sourceSize)}", style = MaterialTheme.typography.bodySmall)
                        Text("Last modification: ${dateFormat.format(java.util.Date(conflict.sourceModified))}", style = MaterialTheme.typography.bodySmall)
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text("Replace with", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text("Size: ${formatSize(conflict.destSize)}", style = MaterialTheme.typography.bodySmall)
                        Text("Last modification: ${dateFormat.format(java.util.Date(conflict.destModified))}", style = MaterialTheme.typography.bodySmall)
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Checkbox(
                                checked = applyToAll,
                                onCheckedChange = { applyToAll = it }
                            )
                            Text("Apply to all files", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                },
                confirmButton = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.End
                    ) {
                        TextButton(onClick = { viewModel.resolveConflicts("CANCEL", false) }) {
                            Text("CANCEL", style = MaterialTheme.typography.labelLarge)
                        }
                        TextButton(onClick = { viewModel.resolveConflicts("RENAME", applyToAll) }) {
                            Text("RENAME", style = MaterialTheme.typography.labelLarge)
                        }
                        TextButton(onClick = { viewModel.resolveConflicts("SKIP", applyToAll) }) {
                            Text("SKIP", style = MaterialTheme.typography.labelLarge)
                        }
                        TextButton(onClick = { viewModel.resolveConflicts("REPLACE", applyToAll) }) {
                            Text("REPLACE", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                },
                shape = RoundedCornerShape(28.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                textContentColor = MaterialTheme.colorScheme.onSurface
            )
        }

        showInfoDialog?.let { file ->
            InformationDialog(
                file = file,
                onDismiss = { showInfoDialog = null }
            )
        }
        if (showPinDialog) {
            val settingsRepo = remember { SettingsRepository(context) }
            val currentPin = settingsRepo.getLockerCode()
            
            PinDialog(
                action = pinDialogAction,
                currentPin = currentPin,
                onConfirm = { pin ->
                   if (pinDialogAction == PinAction.SETUP) {
                       settingsRepo.setLockerCode(pin)
                   }
                   viewModel.moveToLocker(filesToLock)
                   showPinDialog = false
                   filesToLock = emptyList()
                },
                onDismiss = { 
                    showPinDialog = false 
                    filesToLock = emptyList()
                }
            )
        }
    }
}

enum class PinAction { SETUP, VERIFY }

@Composable
fun PinDialog(
    action: PinAction,
    currentPin: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text(if (action == PinAction.SETUP) "Set Locker PIN" else "Enter PIN") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { 
                        if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                            text = it
                            error = ""
                        }
                    },
                    singleLine = true,
                    label = { Text("4-digit PIN") },
                    isError = error.isNotEmpty()
                )
                if (error.isNotEmpty()) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (text.length != 4) {
                        error = "PIN must be 4 digits"
                        return@TextButton
                    }
                    
                    if (action == PinAction.VERIFY && text != currentPin) {
                        error = "Incorrect PIN"
                        return@TextButton
                    }
                    
                    onConfirm(text)
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
