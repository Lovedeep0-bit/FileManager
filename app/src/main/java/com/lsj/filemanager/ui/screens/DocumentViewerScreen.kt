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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(path: String, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val file = remember(path) { File(path) }
    val extension = file.extension.lowercase()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        file.name,
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
                    IconButton(onClick = { shareFile(context, file) }) {
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
                .background(if (extension == "pdf") Color.Gray else MaterialTheme.colorScheme.background)
        ) {
            when (extension) {
                "pdf" -> PdfViewer(file)
                "jpg", "jpeg", "png", "webp", "gif", "bmp" -> ImageViewer(file)
                "txt", "log", "json", "xml", "csv", "kt", "java", "py", "html", "css", "js" -> TextViewer(file)
                else -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Unsupported file type")
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { openWithExternal(context, file) }) {
                            Text("Open with external app")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PdfViewer(file: File) {
    var pageCount by remember { mutableStateOf(0) }
    val pdfMutex = remember { Mutex() }
    
    val renderer = remember(file) {
        try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(pfd).also { pageCount = it.pageCount }
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
fun TextViewer(file: File) {
    var content by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            try {
                content = file.readText()
            } catch (e: Exception) {
                content = "Error reading file: ${e.message}"
            }
            isLoading = false
        }
    }

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
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

private fun shareFile(context: android.content.Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share file"))
    } catch (e: Exception) {
        Toast.makeText(context, "Error sharing file", Toast.LENGTH_SHORT).show()
    }
}

private fun openWithExternal(context: android.content.Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
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
fun ImageViewer(file: File) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = file,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}
