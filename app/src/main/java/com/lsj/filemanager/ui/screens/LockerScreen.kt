package com.lsj.filemanager.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lsj.filemanager.data.FileRepository
import com.lsj.filemanager.data.SettingsRepository
import com.lsj.filemanager.model.FileModel
import com.lsj.filemanager.ui.components.FileItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LockerScreen(
    onNavigateBack: () -> Unit,
    onViewFile: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Ideally we would inject these, but for simplicity we instantiate or use a ViewModel
    // Using simple remembering here for repositories as they are light wrappers (assuming)
    // In a real app, use Hilt or ViewModel factory.
    val settingsRepo = remember { SettingsRepository(context) }
    val fileRepo = remember { FileRepository() }

    var isLocked by remember { mutableStateOf(true) }
    var hasSetPin by remember { mutableStateOf(settingsRepo.isLockerEnabled()) }
    var pinInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    
    // Locker content state
    var lockerFiles by remember { mutableStateOf<List<FileModel>>(emptyList()) }
    var selectedFiles by remember { mutableStateOf<Set<FileModel>>(emptySet()) }
    var isLoading by remember { mutableStateOf(false) }

    // Initial load check
    LaunchedEffect(Unit) {
        if (!hasSetPin) {
            // If no PIN set, we are "unlocked" in the sense that we are in setup mode,
            // but effectively we are just verifying ID.
            // Actually, we stay locked until they set a PIN.
        }
    }

    LaunchedEffect(isLocked) {
        if (!isLocked) {
            isLoading = true
            lockerFiles = fileRepo.getLockerFiles()
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Locker") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!isLocked) {
                        IconButton(onClick = { isLocked = true }) {
                            Icon(Icons.Default.Lock, contentDescription = "Lock")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isLocked) {
                PinEntryView(
                    isSetupMode = !hasSetPin,
                    pinInput = pinInput,
                    error = errorMsg,
                    onPinDigitClick = { digit ->
                        if (pinInput.length < 4) {
                            val newPin = pinInput + digit
                            pinInput = newPin
                            errorMsg = ""
                            
                            if (newPin.length == 4) {
                                if (!hasSetPin) {
                                    // Setting new PIN
                                    settingsRepo.setLockerCode(newPin)
                                    hasSetPin = true
                                    isLocked = false
                                    pinInput = ""
                                } else {
                                    // Verifying PIN
                                    if (newPin == settingsRepo.getLockerCode()) {
                                        isLocked = false
                                        pinInput = ""
                                    } else {
                                        errorMsg = "Incorrect PIN"
                                        pinInput = ""
                                    }
                                }
                            }
                        }
                    },
                    onDelete = {
                        if (pinInput.isNotEmpty()) pinInput = pinInput.dropLast(1)
                        errorMsg = ""
                    }
                )
            } else {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (lockerFiles.isEmpty()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.LockOpen, 
                            contentDescription = null, 
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Locker is empty",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Move files here to secure them.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(
                                items = lockerFiles,
                                key = { it.path }
                            ) { file ->
                                FileItem(
                                    file = file,
                                    isSelected = selectedFiles.contains(file),
                                    onClick = { 
                                        if (selectedFiles.isNotEmpty()) {
                                            if (selectedFiles.contains(file)) {
                                                selectedFiles = selectedFiles - file
                                            } else {
                                                selectedFiles = selectedFiles + file
                                            }
                                        } else {
                                            // View file securely
                                            scope.launch {
                                                val tempPath = fileRepo.prepareFileForView(file, context)
                                                if (tempPath != null) {
                                                    onViewFile(tempPath)
                                                } else {
                                                    // Show error
                                                }
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        if (selectedFiles.contains(file)) {
                                            selectedFiles = selectedFiles - file
                                        } else {
                                            selectedFiles = selectedFiles + file
                                        }
                                    },
                                    onIconClick = { 
                                        if (selectedFiles.contains(file)) {
                                            selectedFiles = selectedFiles - file
                                        } else {
                                            selectedFiles = selectedFiles + file
                                        }
                                    },
                                    modifier = Modifier.animateItem()
                                )
                                HorizontalDivider()
                            }
                        }

                        AnimatedVisibility(
                            visible = selectedFiles.isNotEmpty(),
                            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                            modifier = Modifier.align(Alignment.BottomCenter)
                        ) {
                            BottomAppBar(
                                actions = {
                                    IconButton(onClick = { 
                                        scope.launch {
                                            selectedFiles.forEach { file ->
                                                fileRepo.unlockFile(file.path, context)
                                            }
                                            lockerFiles = fileRepo.getLockerFiles() // Refresh
                                            selectedFiles = emptySet()
                                        }
                                    }) {
                                        Icon(Icons.Default.LockOpen, contentDescription = "Unlock")
                                    }
                                    
                                    Spacer(Modifier.weight(1f))
                                    
                                    Text(
                                        "${selectedFiles.size} selected",
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(end = 16.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PinEntryView(
    isSetupMode: Boolean,
    pinInput: String,
    error: String,
    onPinDigitClick: (String) -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text(
            if (isSetupMode) "Set Locker PIN" else "Enter PIN",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(8.dp))
        
        // PIN Dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(vertical = 24.dp)
        ) {
            repeat(4) { index ->
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            if (index < pinInput.length) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }
        
        if (error.isNotEmpty()) {
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Keypad
        val keys = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "DEL")
        )

        keys.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                row.forEach { key ->
                    if (key.isEmpty()) {
                        Spacer(Modifier.size(72.dp))
                    } else if (key == "DEL") {
                        TextButton(
                            onClick = onDelete,
                            modifier = Modifier.size(72.dp),
                            shape = CircleShape
                        ) {
                            Text("⌫", fontSize = 24.sp)
                        }
                    } else {
                        Button(
                            onClick = { onPinDigitClick(key) },
                            modifier = Modifier.size(72.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            elevation = ButtonDefaults.buttonElevation(2.dp)
                        ) {
                            Text(key, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
