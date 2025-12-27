package com.lsj.filemanager.ui.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.lsj.filemanager.service.CacheCleanerService
import com.lsj.filemanager.util.PermissionManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // State for permission statuses
    var hasStorageAccess by remember { mutableStateOf(PermissionManager.hasAllFilesAccess(context)) }
    var canInstallApps by remember { mutableStateOf(PermissionManager.canRequestPackageInstalls(context)) }
    var isAccessibilityEnabled by remember { mutableStateOf(PermissionManager.isAccessibilityServiceEnabled(context, CacheCleanerService::class.java)) }
    var canDrawOverlays by remember { mutableStateOf(PermissionManager.canDrawOverlays(context)) }

    // Refresh permissions when returning to the app
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasStorageAccess = PermissionManager.hasAllFilesAccess(context)
                canInstallApps = PermissionManager.canRequestPackageInstalls(context)
                isAccessibilityEnabled = PermissionManager.isAccessibilityServiceEnabled(context, CacheCleanerService::class.java)
                canDrawOverlays = PermissionManager.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Permissions") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Required Permissions",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
            )

            PermissionItem(
                icon = Icons.Default.Folder,
                title = "All Files Access",
                description = "Required to manage files on your device storage.",
                isGranted = hasStorageAccess,
                onClick = { PermissionManager.launchManageAllFilesSettings(context) }
            )

            HorizontalDivider()

            PermissionItem(
                icon = Icons.Default.InstallMobile,
                title = "Install Unknown Apps",
                description = "Required to install APK files directly from the app.",
                isGranted = canInstallApps,
                onClick = { PermissionManager.launchManageIconAppsSettings(context) }
            )

            HorizontalDivider()
            
            Text(
                "Feature Permissions",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
            )

            PermissionItem(
                icon = Icons.Default.AccessibilityNew,
                title = "Accessibility Service",
                description = "Required for automated cache cleaning features.",
                isGranted = isAccessibilityEnabled,
                onClick = { PermissionManager.launchAccessibilitySettings(context) }
            )

            HorizontalDivider()

            PermissionItem(
                icon = Icons.Default.Layers,
                title = "Display Over Other Apps",
                description = "Required to show the cache cleaning status overlay.",
                isGranted = canDrawOverlays,
                onClick = { PermissionManager.launchManageOverlaySettings(context) }
            )
        }
    }
}

@Composable
fun PermissionItem(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = {
            if (isGranted) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Granted",
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                Switch(
                    checked = false,
                    onCheckedChange = { onClick() }
                )
            }
        }
    )
}
