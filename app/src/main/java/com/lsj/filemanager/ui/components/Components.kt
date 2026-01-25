package com.lsj.filemanager.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lsj.filemanager.model.FileModel
import java.text.SimpleDateFormat
import java.util.*

import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.decode.VideoFrameDecoder

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItem(
    file: FileModel,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onIconClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val date = SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(file.lastModified))
    
    val isThumbnailSupported = file.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp", "gif")

    ListItem(
        modifier = modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        ),
        colors = ListItemDefaults.colors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            else 
                androidx.compose.ui.graphics.Color.Transparent
        ),
        headlineContent = { 
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            val content = if (file.packageName != null) {
                val size = android.text.format.Formatter.formatFileSize(context, file.size)
                "$size • ${file.packageName}"
            } else if (file.isDirectory) {
                "${file.itemCount} items"
            } else {
                val size = android.text.format.Formatter.formatFileSize(context, file.size)
                if (file.extraInfo.isNotEmpty()) "$size • ${file.extraInfo}" else size
            }
            Text(text = content, style = MaterialTheme.typography.bodySmall)
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(onClick = onIconClick),
                contentAlignment = Alignment.Center
            ) {
                when {
                // Show app icon if it's an installed app
                file.appIconPackageName != null -> {
                    val appIcon = try {
                        context.packageManager.getApplicationIcon(file.appIconPackageName!!)
                    } catch (e: Exception) {
                        null
                    }
                    
                    if (appIcon != null) {
                        val bitmap = appIcon.toBitmap()
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.graphics.painter.BitmapPainter(
                                bitmap.asImageBitmap()  
                            ),
                            contentDescription = file.name,
                            modifier = Modifier.size(40.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Android,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
                // Show icon for APK files (uninstalled)
                file.extension.lowercase() == "apk" -> {
                    val apkIcon = try {
                        val pm = context.packageManager
                        val info = pm.getPackageArchiveInfo(file.path, 0)
                        info?.applicationInfo?.sourceDir = file.path
                        info?.applicationInfo?.publicSourceDir = file.path
                        info?.applicationInfo?.loadIcon(pm)
                    } catch (e: Exception) {
                        null
                    }

                    if (apkIcon != null) {
                        val bitmap = apkIcon.toBitmap()
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.graphics.painter.BitmapPainter(
                                bitmap.asImageBitmap()
                            ),
                            contentDescription = file.name,
                            modifier = Modifier.size(40.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Android,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
                // Show thumbnail for media files
                isThumbnailSupported -> {
                    val defaultIcon = when {
                        file.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp", "gif") -> Icons.Default.Image
                        else -> Icons.Default.Movie
                    }
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = defaultIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(file.path)
                                .decoderFactory(VideoFrameDecoder.Factory())
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                }
                // Show appropriate icon for other files
                else -> {
                    Icon(
                        imageVector = when {
                            file.isDirectory -> Icons.Default.Folder
                            file.extension.lowercase() == "pdf" -> Icons.Default.Description
                            file.extension.lowercase() in listOf("mp3", "wav", "m4a", "ogg") -> Icons.Default.AudioFile
                            file.extension.lowercase() in listOf("mp4", "mkv", "avi", "mov", "flv", "wmv", "webm") -> Icons.Default.Movie
                            else -> Icons.Default.InsertDriveFile
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }
                }
            }
        },
        trailingContent = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

enum class FileAction {
    OPEN_AS_ARCHIVE, COMPRESS, EXTRACT, EXTRACT_TO_FOLDER, EXTRACT_TO_CUSTOM, INFORMATION, COPY, CUT, DELETE, RENAME, SHARE, APP_INFO, UNINSTALL, CLEAR_CACHE, MOVE_TO_LOCKER
}

@Composable
fun FileActionSheet(
    file: FileModel,
    onAction: (FileAction) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
    ) {
        Text(
            text = file.name,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
        HorizontalDivider()
        
        val isArchive = file.extension.lowercase() in listOf("zip", "7z", "rar")
        val isApp = file.packageName != null
        val actions = mutableListOf<Pair<FileAction, Pair<ImageVector, String>>>()
        
        if (isApp) {
            actions.add(FileAction.APP_INFO to (Icons.Default.Settings to "Show app info"))
            if (!file.isSystemApp) {
                actions.add(FileAction.UNINSTALL to (Icons.Default.Delete to "Uninstall"))
            }
            actions.add(FileAction.CLEAR_CACHE to (Icons.Default.AutoFixHigh to "Clear Cache"))
        } else {
            if (isArchive) {
                actions.add(FileAction.OPEN_AS_ARCHIVE to (Icons.Default.Visibility to "View contents"))
                actions.add(FileAction.EXTRACT to (Icons.Default.Unarchive to "Extract here"))
                actions.add(FileAction.EXTRACT_TO_FOLDER to (Icons.Default.CreateNewFolder to "Extract to folder"))
                actions.add(FileAction.EXTRACT_TO_CUSTOM to (Icons.Default.DriveFileMove to "Extract to..."))
            }
            
            actions.addAll(listOf(
                FileAction.COMPRESS to (Icons.Default.Archive to "Compress..."),
                FileAction.MOVE_TO_LOCKER to (Icons.Default.Lock to "Move to Locker"),
                FileAction.INFORMATION to (Icons.Default.Info to "Information"),
                FileAction.COPY to (Icons.Default.ContentCopy to "Copy"),
                FileAction.CUT to (Icons.Default.ContentCut to "Cut"),
                FileAction.DELETE to (Icons.Default.Delete to "Delete"),
                FileAction.RENAME to (Icons.Default.Edit to "Rename"),
                FileAction.SHARE to (Icons.Default.Share to "Share")
            ))
        }
        
        actions.forEach { (action, pair) ->
            val (icon, label) = pair
            ListItem(
                modifier = Modifier.clickable { onAction(action) },
                headlineContent = { Text(label) },
                leadingContent = { Icon(icon, contentDescription = null) }
            )
        }
    }
}

@Composable
fun RenameDialog(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                "Rename",
                style = MaterialTheme.typography.titleLarge
            ) 
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                shape = RoundedCornerShape(12.dp)
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text) }) {
                Text("OK", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun InputDialog(
    title: String,
    initialValue: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                title,
                style = MaterialTheme.typography.titleLarge
            ) 
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                shape = RoundedCornerShape(12.dp)
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text) }) {
                Text("OK", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun InformationDialog(
    file: FileModel,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dateFormat = java.text.SimpleDateFormat("dd/MM/yy h:mm a", java.util.Locale.getDefault())
    fun formatSize(size: Long) = android.text.format.Formatter.formatFileSize(context, size)
    fun formatDuration(durationMs: Long): String {
        val seconds = (durationMs / 1000) % 60
        val minutes = (durationMs / (1000 * 60)) % 60
        val hours = (durationMs / (1000 * 60 * 60))
        return if (hours > 0) String.format("%02d:%02d:%02d", hours, minutes, seconds)
               else String.format("%02d:%02d", minutes, seconds)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                "Information",
                style = MaterialTheme.typography.titleLarge
            ) 
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoRow(label = "Name", value = file.name)
                InfoRow(label = "Path", value = file.path)
                InfoRow(label = "Size", value = formatSize(file.size))
                if (file.isDirectory) {
                    InfoRow(label = "Items", value = "${file.itemCount} items")
                }
                InfoRow(label = "Last modified", value = dateFormat.format(java.util.Date(file.lastModified)))
                
                if (file.duration != null) {
                    InfoRow(label = "Duration", value = formatDuration(file.duration))
                }
                
                if (file.resolution != null) {
                    InfoRow(label = "Resolution", value = file.resolution)
                }

                if (file.extraInfo.isNotEmpty() && file.duration == null && file.resolution == null) {
                    val label = when (file.extension.lowercase()) {
                        "apk" -> "Version"
                        else -> "Details"
                    }
                    InfoRow(label = label, value = file.extraInfo)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK", fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun StorageCard(
    usedSpace: String,
    totalSpace: String,
    percentage: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(

        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Internal Storage",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            LinearProgressIndicator(
                progress = { percentage },
                modifier = Modifier.fillMaxWidth(),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$usedSpace used",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "$totalSpace total",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun CategoryItem(
    name: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = name,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Breadcrumbs(
    currentPath: String,
    onPathClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val segments = remember(currentPath) {
        val list = mutableListOf<Pair<String, String>>()
        // Simplification for Internal Storage root
        val internalRoot = "/storage/emulated/0"
        
        val pathParts = currentPath.split("|")
        val realPath = pathParts[0]
        val archivePath = if (pathParts.size > 1) pathParts[1] else ""
        
        list.add("Internal shared storage" to internalRoot)
        
        // real path segments
        if (realPath.startsWith(internalRoot) && realPath.length > internalRoot.length) {
            val relative = realPath.substring(internalRoot.length)
            val parts = relative.split("/").filter { it.isNotEmpty() }
            var rollingPath = internalRoot
            parts.forEach { part ->
                rollingPath += "/$part"
                list.add(part to rollingPath)
            }
        }
        
        // archive path segments
        if (archivePath.isNotEmpty()) {
            val parts = archivePath.split("/").filter { it.isNotEmpty() }
            var rollingPath = realPath + "|"
            parts.forEach { part ->
                rollingPath += "/$part"
                list.add(part to rollingPath)
            }
        }
        list
    }

    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        segments.forEachIndexed { index, pair ->
            Text(
                text = pair.first,
                style = MaterialTheme.typography.bodyMedium,
                color = if (index == segments.lastIndex) 
                    MaterialTheme.colorScheme.onSurface 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.combinedClickable(
                    onClick = { onPathClick(pair.second) },
                    onLongClick = {
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(pair.second))
                        android.widget.Toast.makeText(context, "Path copied", android.widget.Toast.LENGTH_SHORT).show()
                    }
                )
            )
            if (index < segments.lastIndex) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    TopAppBar(
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search files...") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                singleLine = true
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                }
            }
        }
    )
}
