package com.lsj.filemanager.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import android.content.Intent
import android.webkit.MimeTypeMap
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.animation.*
import androidx.compose.ui.text.style.TextAlign

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(uriStr: String, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val uri = remember(uriStr) { android.net.Uri.parse(uriStr) }
    
    // Extract metadata
    val metadata = remember(uri) {
        var name = "File"
        var extension = ""
        
        if (uri.scheme == "file") {
            val file = File(uri.path ?: "")
            name = file.name
            extension = file.extension.lowercase()
        } else if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)
                    extension = name.substringAfterLast(".", "").lowercase()
                }
            }
            // Fallback: Infer from MIME if possible (not always available here)
            if (extension.isEmpty()) {
                val mime = context.contentResolver.getType(uri)
                extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: ""
            }
        }
        name to extension
    }
    
    val fileName = metadata.first
    val extension = metadata.second

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        fileName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { shareUri(context, uri, extension) }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(if (extension == "pdf") Color.Gray else if (listOf("jpg", "jpeg", "png", "webp", "gif", "bmp").contains(extension)) Color.Black else MaterialTheme.colorScheme.background)
        ) {
            val textExtensions = listOf("txt", "log", "json", "xml", "csv", "kt", "java", "py", "html", "css", "js", "md", "gradle", "properties", "yaml", "yml", "sql")
            when {
                extension == "pdf" -> PdfViewer(uri)
                listOf("jpg", "jpeg", "png", "webp", "gif", "bmp").contains(extension) -> ImageViewer(uri)
                textExtensions.contains(extension) -> TextViewer(uri, extension)
                else -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Unsupported file type")
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { openWithExternalUri(context, uri, extension) }) {
                            Text("Open with external app")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PdfViewer(uri: android.net.Uri) {
    var pageCount by remember { mutableStateOf(0) }
    val pdfMutex = remember { Mutex() }
    val context = LocalContext.current
    
    val renderer = remember(uri) {
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                PdfRenderer(pfd).also { pageCount = it.pageCount }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    if (renderer == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Failed to load PDF")
        }
        return
    }

    DisposableEffect(renderer) {
        onDispose {
            try {
                renderer.close()
            } catch (e: Exception) {}
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val width = with(LocalDensity.current) { maxWidth.toPx() }.toInt()
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(pageCount) { index ->
                PdfPage(renderer, index, width, pdfMutex)
            }
        }
    }
}

@Composable
fun PdfPage(renderer: PdfRenderer, index: Int, screenWidth: Int, mutex: Mutex) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    LaunchedEffect(index, screenWidth) {
        if (screenWidth <= 0) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    val page = renderer.openPage(index)
                    val scale = screenWidth.toFloat() / page.width
                    val b = Bitmap.createBitmap(
                        screenWidth,
                        (page.height * scale).toInt(),
                        Bitmap.Config.ARGB_8888
                    )
                    page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bitmap = b
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Page $index",
                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                contentScale = ContentScale.FillWidth
            )
        } ?: Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
fun TextViewer(uri: android.net.Uri, extension: String) {
    var content by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val context = LocalContext.current

    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    content = input.bufferedReader().readText()
                }
            } catch (e: Exception) {
                content = "Error reading file: ${e.message}"
            }
            isLoading = false
        }
    }

    val monospaceExtensions = listOf("json", "xml", "kt", "java", "py", "html", "css", "js", "gradle", "properties", "yaml", "yml", "sql")
    val isMonospace = extension in monospaceExtensions

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            SelectionContainer {
                Text(
                    text = content ?: "",
                    style = if (isMonospace) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

private fun shareUri(context: android.content.Context, uri: android.net.Uri, extension: String) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share file"))
    } catch (e: Exception) {
        Toast.makeText(context, "Error sharing file", Toast.LENGTH_SHORT).show()
    }
}

private fun openWithExternalUri(context: android.content.Context, uri: android.net.Uri, extension: String) {
    try {
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open with"))
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot open file", Toast.LENGTH_SHORT).show()
    }
}
@Composable
fun ImageViewer(uri: android.net.Uri) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    if (scale > 1f) {
                        offset += pan
                    } else {
                        offset = Offset.Zero
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                ),
            contentScale = ContentScale.Fit
        )
        
        if (scale > 1f) {
            IconButton(
                onClick = { 
                    scale = 1f
                    offset = Offset.Zero
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Reset", tint = Color.White)
            }
        }
    }
}
