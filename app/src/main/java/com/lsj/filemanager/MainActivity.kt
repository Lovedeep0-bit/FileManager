package com.lsj.filemanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lsj.filemanager.ui.screens.ExplorerScreen
import com.lsj.filemanager.ui.screens.DocumentViewerScreen
import com.lsj.filemanager.ui.theme.FileManagerTheme
import com.lsj.filemanager.model.FileCategory
import com.lsj.filemanager.util.PermissionManager
import kotlinx.coroutines.launch

import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.lsj.filemanager.ui.MainViewModel

import androidx.navigation.compose.currentBackStackEntryAsState
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.lifecycleScope
import com.lsj.filemanager.util.GlobalEvents
import com.lsj.filemanager.ui.explorer.ExplorerViewModel

class MainActivity : ComponentActivity() {

    private val packageRemovedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_PACKAGE_REMOVED) {
                val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                if (!isReplacing) {
                    lifecycleScope.launch {
                        GlobalEvents.triggerRefreshApps()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
                val viewModel: MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                val currentTheme by viewModel.currentTheme.collectAsState()
                
                FileManagerTheme(appTheme = currentTheme) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val navController = rememberNavController()
                        
                        val initialRoute = remember {
                            if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
                                "viewer?uri=${android.net.Uri.encode(intent.data.toString())}&external=true"
                            } else {
                                "explorer"
                            }
                        }

                        val context = LocalContext.current
                        val versionName = remember {
                            try {
                                context.packageManager.getPackageInfo(context.packageName, 0).versionName
                            } catch (e: Exception) {
                                "1.1"
                            }
                        }
                        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                        val scope = rememberCoroutineScope()
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentRoute = navBackStackEntry?.destination?.route
                        val currentCategoryArg = navBackStackEntry?.arguments?.getString("category")
                        
                        var showPermissionDialog by remember { 
                            mutableStateOf(!PermissionManager.hasAllFilesAccess(context)) 
                        }
        
                        if (showPermissionDialog) {
                            AlertDialog(
                                onDismissRequest = { /* Don't dismiss on click outside */ },
                                title = { Text("App needs access to manage all files.") },
                                text = { Text("Please allow the access in the upcoming system setting.") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        showPermissionDialog = false
                                        PermissionManager.launchManageAllFilesSettings(context)
                                    }) {
                                        Text("OK")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { 
                                        showPermissionDialog = false
                                    }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }
        
        
                        ModalNavigationDrawer(
                        drawerState = drawerState,
                        drawerContent = {
                            ModalDrawerSheet(
                                drawerContainerColor = MaterialTheme.colorScheme.surface
                            ) {
                                Column(
                                    modifier = Modifier.verticalScroll(rememberScrollState())
                                ) {
                                    Spacer(Modifier.height(12.dp))
                                    NavigationDrawerItem(
                                        icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                                        label = { Text("Internal Storage") },
                                        selected = currentRoute == "explorer" && currentCategoryArg == null,
                                        onClick = {
                                            scope.launch { drawerState.close() }
                                            navController.navigate("explorer") {
                                                popUpTo("explorer") { inclusive = true }
                                            }
                                        },
                                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                    )
                                    
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                    Text(
                                        "Categories",
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    
                                    val categories = listOf(
                                        "Downloads" to Icons.Default.Download,
                                        "Images" to Icons.Default.Image,
                                        "Videos" to Icons.Default.Videocam,
                                        "Audio" to Icons.Default.MusicNote,
                                        "Documents" to Icons.Default.Description,
                                        "Apps" to Icons.Default.Apps
                                    )
                                    
                                    categories.forEach { (name, icon) ->
                                        val isSelected = currentCategoryArg == name.uppercase()
                                        NavigationDrawerItem(
                                            icon = { Icon(icon, contentDescription = null) },
                                            label = { Text(name) },
                                            selected = isSelected,
                                            onClick = {
                                                scope.launch { drawerState.close() }
                                                navController.navigate("explorer?category=${name.uppercase()}")
                                            },
                                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                        )
                                    }
        
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                    NavigationDrawerItem(
                                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                        label = { Text("Settings") },
                                        selected = currentRoute == "settings",
                                        onClick = { 
                                            scope.launch { drawerState.close() }
                                            navController.navigate("settings")
                                        },
                                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                    )
 
                                    NavigationDrawerItem(
                                        icon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                        label = { Text("Locker") },
                                        selected = currentRoute == "locker",
                                        onClick = { 
                                            scope.launch { drawerState.close() }
                                            navController.navigate("locker")
                                        },
                                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                    )
 
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        text = "v$versionName",
                                        modifier = Modifier
                                            .padding(16.dp)
                                            .align(Alignment.CenterHorizontally),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    ) {
                        NavHost(
                            navController = navController, 
                            startDestination = initialRoute,
                            enterTransition = { slideInHorizontally(initialOffsetX = { 1000 }) + fadeIn() },
                            exitTransition = { slideOutHorizontally(targetOffsetX = { -1000 }) + fadeOut() },
                            popEnterTransition = { slideInHorizontally(initialOffsetX = { -1000 }) + fadeIn() },
                            popExitTransition = { slideOutHorizontally(targetOffsetX = { 1000 }) + fadeOut() }
                        ) {
                            composable(
                                "viewer?uri={uri}&external={external}",
                                arguments = listOf(
                                    navArgument("uri") { type = NavType.StringType },
                                    navArgument("external") { 
                                        type = NavType.BoolType
                                        defaultValue = false 
                                    }
                                )
                            ) { backStackEntry ->
                                val uriStr = backStackEntry.arguments?.getString("uri") ?: return@composable
                                val isExternal = backStackEntry.arguments?.getBoolean("external") ?: false
                                DocumentViewerScreen(
                                    uriStr = android.net.Uri.decode(uriStr),
                                    onNavigateBack = { 
                                        if (isExternal) finish() else navController.popBackStack() 
                                    }
                                )
                            }
                            composable(
                                route = "explorer?category={category}",
                                arguments = listOf(
                                    navArgument("category") { 
                                        type = NavType.StringType
                                        nullable = true
                                        defaultValue = null
                                    }
                                )
                            ) { backStackEntry ->
                                val categoryStr = backStackEntry.arguments?.getString("category")
                                val category = try {
                                    categoryStr?.let { FileCategory.valueOf(it) }
                                } catch (e: Exception) { null }
    
                                ExplorerScreen(
                                    category = category,
                                    onNavigateBack = {
                                        if (!navController.popBackStack()) {
                                            finish()
                                        }
                                    },
                                    onMenuClick = {
                                        scope.launch { drawerState.open() }
                                    },
                                    onViewFile = { file ->
                                        val uri = android.net.Uri.fromFile(java.io.File(file.path)).toString()
                                        navController.navigate("viewer?uri=${android.net.Uri.encode(uri)}")
                                    }
                                )
                            }
                            // Keep simple route for legacy/internal storage direct access
                            composable("explorer") {
                                ExplorerScreen(
                                    category = null,
                                    onNavigateBack = { finish() },
                                    onMenuClick = { scope.launch { drawerState.open() } },
                                    onViewFile = { file ->
                                        val uri = android.net.Uri.fromFile(java.io.File(file.path)).toString()
                                        navController.navigate("viewer?uri=${android.net.Uri.encode(uri)}")
                                    }
                                )
                            }
                            
                            composable("settings") {
                                com.lsj.filemanager.ui.screens.SettingsScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToPermissions = { navController.navigate("permissions_screen") },
                                onNavigateToLockerSettings = { navController.navigate("locker_settings") }
                            )
                            }

                            composable("permissions_screen") {
                                com.lsj.filemanager.ui.screens.PermissionsScreen(
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }

                            composable("locker") {
                                com.lsj.filemanager.ui.screens.LockerScreen(
                                    onNavigateBack = { navController.popBackStack() },
                                    onViewFile = { path ->
                                        val uri = android.net.Uri.fromFile(java.io.File(path)).toString()
                                        navController.navigate("viewer?uri=${android.net.Uri.encode(uri)}")
                                    }
                                )
                            }
                            
                            composable("locker_settings") {
                                com.lsj.filemanager.ui.screens.LockerSettingsScreen(
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }

                val filter = IntentFilter(Intent.ACTION_PACKAGE_REMOVED).apply {
                    addDataScheme("package")
                }
                registerReceiver(packageRemovedReceiver, filter)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(packageRemovedReceiver)
        } catch (e: Exception) {
            // Avoid crash if already unregistered
        }
    }
}


