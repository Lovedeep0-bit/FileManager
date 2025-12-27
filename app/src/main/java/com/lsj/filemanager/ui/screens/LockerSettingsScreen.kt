package com.lsj.filemanager.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lsj.filemanager.data.FileRepository
import com.lsj.filemanager.data.SettingsRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockerSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepo = remember { SettingsRepository(context) }
    val fileRepo = remember { FileRepository() }
    
    var showChangePinDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Locker Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            ListItem(
                headlineContent = { Text("Change PIN") },
                supportingContent = { Text("Update your 4-digit PIN") },
                leadingContent = { Icon(Icons.Default.LockReset, contentDescription = null) },
                modifier = Modifier.clickable { showChangePinDialog = true }
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Reset Locker") },
                supportingContent = { Text("Internal storage files only. Deletes all locked files and clears PIN.") },
                leadingContent = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable { showResetDialog = true }
            )
        }

        if (showChangePinDialog) {
            ChangePinDialog(
                isChange = true,
                currentPin = settingsRepo.getLockerCode(),
                onConfirm = { newPin ->
                    settingsRepo.setLockerCode(newPin)
                    resultMessage = "PIN updated successfully"
                    showChangePinDialog = false
                },
                onDismiss = { showChangePinDialog = false }
            )
        }

        if (showResetDialog) {
            // Re-use ChangePinDialog logic for authentication, but different action
            // Actually, we need a simple "Verify PIN" dialog first, then a confirmation.
            // Let's use ChangePinDialog with a specific flow or a new VerifyPinDialog.
            // For simplicity, let's use a VerifyPinDialog.
            VerifyPinDialog(
                currentPin = settingsRepo.getLockerCode(),
                onVerified = {
                    scope.launch {
                        if (fileRepo.resetLocker()) {
                            settingsRepo.setLockerCode(null) // Clear PIN
                            resultMessage = "Locker reset. All files deleted and PIN cleared."
                        } else {
                            resultMessage = "Failed to reset locker."
                        }
                        showResetDialog = false
                    }
                },
                onDismiss = { showResetDialog = false }
            )
        }
        
        if (resultMessage.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { resultMessage = "" },
                containerColor = MaterialTheme.colorScheme.surface,
                textContentColor = MaterialTheme.colorScheme.onSurface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                title = { Text("Locker") },
                text = { Text(resultMessage) },
                confirmButton = { TextButton(onClick = { resultMessage = "" }) { Text("OK") } }
            )
        }
    }
}

@Composable
fun VerifyPinDialog(
    currentPin: String?,
    onVerified: () -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var showConfirmation by remember { mutableStateOf(false) }

    if (showConfirmation) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = MaterialTheme.colorScheme.surface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text("Reset Locker?") },
            text = { Text("This will permanently delete all files in the Locker and remove your PIN. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = onVerified,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("RESET")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("CANCEL") }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = MaterialTheme.colorScheme.surface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text("Enter PIN to Reset") },
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
                        if (text == currentPin) {
                            showConfirmation = true
                        } else {
                            error = "Incorrect PIN"
                        }
                    }
                ) {
                    Text("Next")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        )
    }
}
