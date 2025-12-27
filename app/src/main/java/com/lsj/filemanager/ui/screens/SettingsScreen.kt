package com.lsj.filemanager.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lsj.filemanager.data.AppTheme
import com.lsj.filemanager.data.SettingsRepository
import com.lsj.filemanager.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToLockerSettings: () -> Unit
) {
    val currentTheme by viewModel.currentTheme.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val settingsRepo = remember { SettingsRepository(context) }
    var resultMessage by remember { mutableStateOf("") }
    
    // Locker state
    var showPinDialog by remember { mutableStateOf(false) }
    var isChangingPin by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            var expanded by remember { mutableStateOf(false) }

            Box {
                ListItem(
                    headlineContent = { Text("Theme") },
                    supportingContent = { 
                        Text(
                            when(currentTheme) {
                                AppTheme.DARK -> "Default (Dark)"
                                AppTheme.LIGHT -> "Light"
                                AppTheme.OLED -> "OLED (Black & White)"
                            }
                        )
                    },
                    modifier = Modifier.clickable { expanded = true }
                )

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Default (Dark)") },
                        onClick = {
                            viewModel.setTheme(AppTheme.DARK)
                            expanded = false
                        },
                        leadingIcon = {
                            if (currentTheme == AppTheme.DARK) {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Light") },
                        onClick = {
                            viewModel.setTheme(AppTheme.LIGHT)
                            expanded = false
                        },
                        leadingIcon = {
                            if (currentTheme == AppTheme.LIGHT) {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("OLED (Black & White)") },
                        onClick = {
                            viewModel.setTheme(AppTheme.OLED)
                            expanded = false
                        },
                        leadingIcon = {
                            if (currentTheme == AppTheme.OLED) {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        }
                    )
                }
            }

            HorizontalDivider()

            val showHiddenFiles by viewModel.showHiddenFiles.collectAsState()
            
            ListItem(
                headlineContent = { Text("Show hidden files") },
                supportingContent = { Text("Requires restart") },
                trailingContent = {
                    Switch(
                        checked = showHiddenFiles,
                        onCheckedChange = { viewModel.setShowHiddenFiles(it) }
                    )
                },
                modifier = Modifier.clickable { viewModel.setShowHiddenFiles(!showHiddenFiles) }
            )

            HorizontalDivider()

            ListItem(
                headlineContent = { Text("Manage permissions") },
                supportingContent = { Text("View and control app access") },
                leadingContent = { 
                    Icon(Icons.Default.Security, contentDescription = null) 
                },
                modifier = Modifier.clickable { onNavigateToPermissions() }
            )

            HorizontalDivider()

            ListItem(
                headlineContent = { Text("Locker Settings") },
                supportingContent = { Text("Manage Locker PIN and features") },
                leadingContent = { Icon(Icons.Default.Lock, contentDescription = null) },
                modifier = Modifier.clickable { 
                    onNavigateToLockerSettings()
                }
            )

            HorizontalDivider()

            ListItem(
                headlineContent = { Text("GitHub Repository") },
                supportingContent = { Text("View source code and contribute") },
                leadingContent = { Icon(Icons.Default.Code, contentDescription = null) },
                modifier = Modifier.clickable { 
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Lovedeep0-bit/FileManager"))
                    context.startActivity(intent)
                }
            )
        }
    }
}

@Composable
fun ChangePinDialog(
    isChange: Boolean,
    currentPin: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var step by remember { mutableStateOf(if (isChange) 0 else 1) } // 0: Verify Old, 1: Enter New
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        title = { 
            Text(
                when (step) {
                    0 -> "Enter Current PIN"
                    1 -> "Enter New PIN"
                    else -> ""
                }
            ) 
        },
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

                    if (step == 0) {
                        if (text == currentPin) {
                            step = 1
                            text = ""
                            error = ""
                        } else {
                            error = "Incorrect PIN"
                        }
                    } else {
                        onConfirm(text)
                    }
                }
            ) {
                Text(if (step == 0) "Next" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
