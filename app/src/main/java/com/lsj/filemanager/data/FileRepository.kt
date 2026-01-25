package com.lsj.filemanager.data

import com.lsj.filemanager.model.FileModel
import com.lsj.filemanager.model.FileCategory
import com.lsj.filemanager.model.toFileModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import android.media.MediaMetadataRetriever
import android.graphics.BitmapFactory
import android.webkit.MimeTypeMap

class FileRepository {

    private val LOCKER_PATH = "/storage/emulated/0/.Locker"
    private val LOCKER_INDEX_FILE = "locker_index.json"

    private fun getLockerIndexFile(context: android.content.Context): File {
        val lockerDir = File(LOCKER_PATH)
        if (!lockerDir.exists()) lockerDir.mkdirs()
        return File(lockerDir, LOCKER_INDEX_FILE)
    }

    private fun saveOriginalPath(context: android.content.Context, lockedFileName: String, originalPath: String) {
        try {
            val indexFile = getLockerIndexFile(context)
            val json = if (indexFile.exists()) org.json.JSONObject(indexFile.readText()) else org.json.JSONObject()
            json.put(lockedFileName, originalPath)
            indexFile.writeText(json.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getOriginalPath(context: android.content.Context, lockedFileName: String): String? {
        return try {
            val indexFile = getLockerIndexFile(context)
            if (!indexFile.exists()) return null
            val json = org.json.JSONObject(indexFile.readText())
            if (json.has(lockedFileName)) json.getString(lockedFileName) else null
        } catch (e: Exception) {
            null
        }
    }

    private fun removeOriginalPath(context: android.content.Context, lockedFileName: String) {
        try {
            val indexFile = getLockerIndexFile(context)
            if (!indexFile.exists()) return
            val json = org.json.JSONObject(indexFile.readText())
            if (json.has(lockedFileName)) {
                json.remove(lockedFileName)
                indexFile.writeText(json.toString())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    init {
        val lockerDir = File(LOCKER_PATH)
        if (!lockerDir.exists()) {
            lockerDir.mkdirs()
        }
        val noMedia = File(lockerDir, ".nomedia")
        if (!noMedia.exists()) {
            try { noMedia.createNewFile() } catch (e: Exception) { e.printStackTrace() }
        }
    }

    suspend fun getLockerFiles(): List<FileModel> = withContext(Dispatchers.IO) {
        val lockerDir = File(LOCKER_PATH)
        if (!lockerDir.exists()) return@withContext emptyList()
        
        lockerDir.listFiles()
            ?.filter { it.isFile && it.name != ".nomedia" && it.name != LOCKER_INDEX_FILE }
            ?.map { file ->
                // Locked files might have .locked extension, we can strip it for display if we want,
                // or just show the name. Let's assume we append .locked when locking.
                val originalName = file.name.removeSuffix(".locked")
                FileModel(
                    name = originalName,
                    path = file.absolutePath,
                    size = file.length(),
                    lastModified = file.lastModified(),
                    isDirectory = false,
                    extension = originalName.substringAfterLast('.', ""),
                    extraInfo = "Locked"
                )
            }?.sortedBy { it.name.lowercase() } ?: emptyList()
    }

    suspend fun moveToLocker(path: String, context: android.content.Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val source = File(path)
            if (!source.exists()) return@withContext false
            
            val destDir = File(LOCKER_PATH)
            if (!destDir.exists()) destDir.mkdirs()
            
            // Generate a unique name if collision occurs
            val destName = source.name + ".locked"
            var dest = File(destDir, destName)
            var counter = 1
            while (dest.exists()) {
                dest = File(destDir, "${source.name}_$counter.locked")
                counter++
            }
            
            if (source.renameTo(dest)) {
                saveOriginalPath(context, dest.name, source.absolutePath)
                return@withContext true
            } else {
                // Fallback copy/delete
                 if (source.copyTo(dest, overwrite = true) != null) {
                    if (source.isDirectory) source.deleteRecursively() else source.delete()
                    saveOriginalPath(context, dest.name, source.absolutePath)
                    return@withContext true
                 }
                 return@withContext false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun unlockFile(path: String, context: android.content.Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val source = File(path)
            if (!source.exists()) return@withContext false
            
            val originalPath = getOriginalPath(context, source.name)
            val targetDir = if (originalPath != null) {
                File(originalPath).parentFile ?: File("/storage/emulated/0/Restored")
            } else {
                File("/storage/emulated/0/Restored")
            }
            if (!targetDir.exists()) targetDir.mkdirs()
            
            // Try to use original name if possible, or current name without extension
            val originalName = if (originalPath != null) File(originalPath).name else source.name.removeSuffix(".locked")
            
            var dest = File(targetDir, originalName)
             var counter = 1
            while (dest.exists()) {
                val nameWithoutExt = originalName.substringBeforeLast('.')
                val ext = originalName.substringAfterLast('.', "")
                val newName = if (ext.isNotEmpty()) "${nameWithoutExt}_$counter.$ext" else "${nameWithoutExt}_$counter"
                dest = File(targetDir, newName)
                counter++
            }
            
            if (source.renameTo(dest)) {
                removeOriginalPath(context, source.name)
                return@withContext true
            } else {
                 source.copyTo(dest, overwrite = true)
                 source.delete()
                 removeOriginalPath(context, source.name)
                 return@withContext true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun resetLocker(): Boolean = withContext(Dispatchers.IO) {
        try {
            val lockerDir = File(LOCKER_PATH)
            if (lockerDir.exists()) {
                // Delete contents but keep directory and .nomedia
                lockerDir.listFiles()?.forEach { 
                    if (it.name != ".nomedia") {
                        if (it.isDirectory) it.deleteRecursively() else it.delete()
                    }
                }
                // Also clear the index file specifically if it wasn't caught above (though it should be)
                // Re-create empty .nomedia just in case
                File(lockerDir, ".nomedia").createNewFile()
                return@withContext true
            }
            return@withContext false
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    suspend fun prepareFileForView(file: FileModel, context: android.content.Context): String? = withContext(Dispatchers.IO) {
        try {
            // Create a temp file in cache directory with the correct extension
            val extension = file.extension
            val tempFile = File.createTempFile("view_", ".$extension", context.cacheDir)
            
            // Copy content
            File(file.path).copyTo(tempFile, overwrite = true)
            
            // Return path
            tempFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun listFiles(path: String, showHidden: Boolean = false): List<FileModel> = withContext(Dispatchers.IO) {
        try {
            val root = File(path)
            if (root.exists() && root.isDirectory) {
                root.listFiles()?.filter { showHidden || (!it.isHidden && !it.name.startsWith(".")) }?.map { file ->
                    val model = file.toFileModel(showHidden)
                    // Optimization: Skip expensive metadata extraction (images/video duration)
                    // This makes loading instant even for thousands of files.
                    // UI (Coil) handles image display, and video duration is "nice to have" but not worth the lag.
                    model

                }?.sortedWith(
                    compareByDescending<FileModel> { it.isDirectory }.thenBy { it.name.lowercase() }
                ) ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getDetailedMetadata(file: FileModel): FileModel = withContext(Dispatchers.IO) {
        try {
            val f = File(file.path)
            if (!f.exists()) return@withContext file

            if (f.isDirectory) {
                var totalSize = 0L
                f.walkTopDown().forEach { child ->
                    if (child.isFile) {
                        totalSize += child.length()
                    }
                    yield()
                }
                return@withContext file.copy(size = totalSize)
            }

            val ext = file.extension.lowercase()
            var duration: Long? = null
            var resolution: String? = null

            when (ext) {
                "mp3", "wav", "m4a", "ogg", "aac", "flac", "mp4", "mkv", "avi", "mov", "flv", "wmv", "webm", "3gp" -> {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(file.path)
                        duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                        if (width != null && height != null) {
                            resolution = "${width}x${height}"
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        retriever.release()
                    }
                }
                "jpg", "jpeg", "png", "webp", "gif", "bmp" -> {
                    val options = BitmapFactory.Options()
                    options.inJustDecodeBounds = true
                    BitmapFactory.decodeFile(file.path, options)
                    if (options.outWidth != -1 && options.outHeight != -1) {
                        resolution = "${options.outWidth}x${options.outHeight}"
                    }
                }
            }

            file.copy(
                duration = duration,
                resolution = resolution,
                mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: ""
            )
        } catch (e: Exception) {
            e.printStackTrace()
            file
        }
    }

    suspend fun createDirectory(parentPath: String, name: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val dir = File(parentPath, name)
            if (!dir.exists()) dir.mkdirs() else false
        } catch (e: Exception) {
            false
        }
    }

    suspend fun createFile(parentPath: String, name: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(parentPath, name)
            if (!file.exists()) file.createNewFile() else false
        } catch (e: Exception) {
            false
        }
    }


    suspend fun deleteFile(path: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(path)
        if (file.isDirectory) {
            file.deleteRecursively()
        } else {
            file.delete()
        }
    }

    suspend fun renameFile(path: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(path)
        val newFile = File(file.parent, newName)
        file.renameTo(newFile)
    }

    suspend fun copyFile(sourcePath: String, destDirPath: String, onProgress: (Float) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        try {
            val source = File(sourcePath)
            val destDir = File(destDirPath)
            if (!destDir.exists()) destDir.mkdirs()
            val dest = File(destDir, source.name)
            
            val totalSize = source.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
            var bytesCopied = 0L

            suspend fun copyRecursive(src: File, dst: File) {
                if (src.isDirectory) {
                    if (!dst.exists()) dst.mkdirs()
                    src.listFiles()?.forEach { child ->
                        copyRecursive(child, File(dst, child.name))
                    }
                } else {
                    src.inputStream().use { input ->
                        dst.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var bytes = input.read(buffer)
                            while (bytes >= 0) {
                                yield()
                                output.write(buffer, 0, bytes)
                                bytesCopied += bytes
                                onProgress(if (totalSize > 0) bytesCopied.toFloat() / totalSize else 0f)
                                bytes = input.read(buffer)
                            }
                        }
                    }
                    // Preserve timestamp?
                    // dst.setLastModified(src.lastModified()) 
                }
            }

            copyRecursive(source, dest)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun moveFile(sourcePath: String, destDirPath: String, onProgress: (Float) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        try {
            val source = File(sourcePath)
            val destDir = File(destDirPath)
            if (!destDir.exists()) destDir.mkdirs()
            val dest = File(destDir, source.name)
            
            // Try atomic rename first (fast, no progress needed)
            if (source.renameTo(dest)) {
                onProgress(1.0f)
                return@withContext true
            }
            
            // Fallback to copy and delete
            if (copyFile(sourcePath, destDirPath, onProgress)) {
                if (source.isDirectory) source.deleteRecursively() else source.delete()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    // Simplified version redundant with createArchive, but kept if needed by other paths (will refactor VM mainly)
    // Actually, let's remove it or redirect it?
    // It was used by 'zipFiles' without progress, but let's just make createArchive the main one.
    // I will remove the old 'zipFiles' method that didn't take progress if it exists, or update it.
    // Checking file... line 330 was 'zipFiles(paths, zipPath)'. Use createArchive instead.
    
    suspend fun createArchiveSimple(paths: List<String>, zipPath: String): Boolean = createArchive(paths, zipPath)

    private fun addFileToZip(zos: org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream, file: File, parentPath: String) {
        val entryName = if (parentPath.isEmpty()) file.name else "$parentPath/${file.name}"
        val entry = org.apache.commons.compress.archivers.zip.ZipArchiveEntry(file, entryName)
        zos.putArchiveEntry(entry)
        if (file.isFile) {
            file.inputStream().use { it.copyTo(zos) }
            zos.closeArchiveEntry()
        } else {
            zos.closeArchiveEntry()
            file.listFiles()?.forEach { child ->
                addFileToZip(zos, child, entryName)
            }
        }
    }

    suspend fun searchFiles(query: String, showHidden: Boolean = false, rootPath: String = "/storage/emulated/0"): List<FileModel> = withContext(Dispatchers.IO) {
        try {
            if (query.length < 2) return@withContext emptyList()
            val root = File(rootPath)
            val results = mutableListOf<FileModel>()
            
            fun walk(file: File) {
                if (results.size >= 100) return
                // Explicitly check for hidden files/dirs (dot prefix or OS attribute)
                val isHidden = file.isHidden || file.name.startsWith(".")
                if (!showHidden && isHidden) return

                if (file.name.contains(query, ignoreCase = true)) {
                    results.add(file.toFileModel(showHidden))
                }
                if (file.isDirectory) {
                    file.listFiles()?.forEach { walk(it) }
                }
            }
            
            walk(root)
            results.sortedBy { it.name.lowercase() }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getFilesByCategory(category: FileCategory, context: android.content.Context, rootPath: String = "/storage/emulated/0"): List<FileModel> = withContext(Dispatchers.IO) {
        if (category == FileCategory.DOWNLOADS) {
            return@withContext listFiles("/storage/emulated/0/Download")
        }

        // Use MediaStore for media files (much faster)
        when (category) {
            FileCategory.IMAGES -> getImagesFromMediaStore(context)
            FileCategory.VIDEOS -> getVideosFromMediaStore(context)
            FileCategory.AUDIO -> getAudioFromMediaStore(context)
            FileCategory.DOCUMENTS -> getDocumentsRecursive(rootPath)
            FileCategory.APPS -> getApksRecursive(rootPath)
            else -> emptyList()
        }
    }

    private suspend fun getImagesFromMediaStore(context: android.content.Context): List<FileModel> = withContext(Dispatchers.IO) {
        val results = mutableListOf<FileModel>()
        val projection = arrayOf(
            android.provider.MediaStore.Images.Media._ID,
            android.provider.MediaStore.Images.Media.DISPLAY_NAME,
            android.provider.MediaStore.Images.Media.DATA,
            android.provider.MediaStore.Images.Media.SIZE,
            android.provider.MediaStore.Images.Media.DATE_MODIFIED
        )
        
        context.contentResolver.query(
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${android.provider.MediaStore.Images.Media.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val nameCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DISPLAY_NAME)
            val pathCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DATA)
            val sizeCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DATE_MODIFIED)
            
            while (cursor.moveToNext()) {
                val file = File(cursor.getString(pathCol))
                if (file.exists()) {
                    results.add(FileModel(
                        name = cursor.getString(nameCol),
                        path = file.absolutePath,
                        size = cursor.getLong(sizeCol),
                        lastModified = cursor.getLong(dateCol) * 1000,
                        isDirectory = false,
                        extension = file.extension
                    ))
                }
            }
        }
        results
    }

    private suspend fun getVideosFromMediaStore(context: android.content.Context): List<FileModel> = withContext(Dispatchers.IO) {
        val results = mutableListOf<FileModel>()
        val projection = arrayOf(
            android.provider.MediaStore.Video.Media._ID,
            android.provider.MediaStore.Video.Media.DISPLAY_NAME,
            android.provider.MediaStore.Video.Media.DATA,
            android.provider.MediaStore.Video.Media.SIZE,
            android.provider.MediaStore.Video.Media.DATE_MODIFIED
        )
        
        context.contentResolver.query(
            android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${android.provider.MediaStore.Video.Media.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val nameCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DISPLAY_NAME)
            val pathCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DATA)
            val sizeCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DATE_MODIFIED)
            
            while (cursor.moveToNext()) {
                val file = File(cursor.getString(pathCol))
                if (file.exists()) {
                    results.add(FileModel(
                        name = cursor.getString(nameCol),
                        path = file.absolutePath,
                        size = cursor.getLong(sizeCol),
                        lastModified = cursor.getLong(dateCol) * 1000,
                        isDirectory = false,
                        extension = file.extension
                    ))
                }
            }
        }
        results
    }

    private suspend fun getAudioFromMediaStore(context: android.content.Context): List<FileModel> = withContext(Dispatchers.IO) {
        val results = mutableListOf<FileModel>()
        val projection = arrayOf(
            android.provider.MediaStore.Audio.Media._ID,
            android.provider.MediaStore.Audio.Media.DISPLAY_NAME,
            android.provider.MediaStore.Audio.Media.DATA,
            android.provider.MediaStore.Audio.Media.SIZE,
            android.provider.MediaStore.Audio.Media.DATE_MODIFIED
        )
        
        context.contentResolver.query(
            android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${android.provider.MediaStore.Audio.Media.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val nameCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DISPLAY_NAME)
            val pathCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATA)
            val sizeCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATE_MODIFIED)
            
            while (cursor.moveToNext()) {
                val file = File(cursor.getString(pathCol))
                if (file.exists()) {
                    results.add(FileModel(
                        name = cursor.getString(nameCol),
                        path = file.absolutePath,
                        size = cursor.getLong(sizeCol),
                        lastModified = cursor.getLong(dateCol) * 1000,
                        isDirectory = false,
                        extension = file.extension
                    ))
                }
            }
        }
        results
    }

    private suspend fun getDocumentsRecursive(rootPath: String): List<FileModel> = withContext(Dispatchers.IO) {
        val extensions = listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt")
        val results = mutableListOf<FileModel>()
        val root = File(rootPath)

        fun walk(file: File) {
            if (file.isDirectory) {
                file.listFiles()?.forEach { walk(it) }
            } else if (file.extension.lowercase() in extensions) {
                results.add(file.toFileModel())
            }
        }

        walk(root)
        results.sortedByDescending { it.lastModified }
    }

    private suspend fun getApksRecursive(rootPath: String): List<FileModel> = withContext(Dispatchers.IO) {
        val results = mutableListOf<FileModel>()
        val root = File(rootPath)

        fun walk(file: File) {
            if (file.isDirectory) {
                file.listFiles()?.forEach { walk(it) }
            } else if (file.extension.lowercase() == "apk") {
                results.add(file.toFileModel())
            }
        }

        walk(root)
        results.sortedByDescending { it.lastModified }
    }

    suspend fun getApps(context: android.content.Context, system: Boolean): List<FileModel> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
        
        apps.filter {
            val isSystem = (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            if (system) isSystem else !isSystem
        }.map { app ->
            val file = File(app.sourceDir)
            FileModel(
                name = pm.getApplicationLabel(app).toString(),
                path = app.sourceDir,
                size = file.length(),
                lastModified = file.lastModified(),
                isDirectory = false,
                extension = "apk",
                packageName = app.packageName,
                isSystemApp = (app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0,
                appIconPackageName = app.packageName // Add package name for icon loading
            )
        }.sortedBy { it.name.lowercase() }
    }

    suspend fun listArchiveContents(archivePath: String, internalPath: String = ""): List<FileModel> = withContext(Dispatchers.IO) {
        val results = mutableListOf<FileModel>()
        try {
            val file = File(archivePath)
            if (!file.exists()) return@withContext emptyList()

            val extension = file.extension.lowercase()
            
            if (extension == "7z") {
                org.apache.commons.compress.archivers.sevenz.SevenZFile(file).use { sevenZFile ->
                    val currentDirEntries = mutableSetOf<String>()
                    var entry = sevenZFile.nextEntry
                    while (entry != null) {
                         val entryName = entry.name.removePrefix("/")
                         
                         // Filter entries belonging to the current internal path
                         // 7z logic similar to Zip logic but streaming entries
                         if (internalPath.isEmpty()) {
                            val firstSlash = entryName.indexOf('/')
                            if (firstSlash == -1) {
                                if (!currentDirEntries.contains(entryName)) {
                                    results.add(FileModel(
                                        name = entryName,
                                        path = "$archivePath|/$entryName",
                                        size = entry.size,
                                        lastModified = entry.lastModifiedDate?.time ?: 0L,
                                        isDirectory = entry.isDirectory,
                                        extension = if (entry.isDirectory) "" else entryName.substringAfterLast('.', ""),
                                        extraInfo = if (entry.isDirectory) "" else "In 7z"
                                    ))
                                    currentDirEntries.add(entryName)
                                }
                            } else {
                                val folderName = entryName.substring(0, firstSlash)
                                if (!currentDirEntries.contains(folderName)) {
                                    results.add(FileModel(
                                        name = folderName,
                                        path = "$archivePath|/$folderName",
                                        size = 0,
                                        lastModified = entry.lastModifiedDate?.time ?: 0L,
                                        isDirectory = true,
                                        extraInfo = "In 7z"
                                    ))
                                    currentDirEntries.add(folderName)
                                }
                            }
                         } else {
                            val prefix = "$internalPath/"
                            if (entryName.startsWith(prefix) && entryName != prefix) {
                                val relativeName = entryName.removePrefix(prefix)
                                val firstSlash = relativeName.indexOf('/')
                                if (firstSlash == -1) {
                                    if (!currentDirEntries.contains(relativeName)) {
                                        results.add(FileModel(
                                            name = relativeName,
                                            path = "$archivePath|/$entryName",
                                            size = entry.size,
                                            lastModified = entry.lastModifiedDate?.time ?: 0L,
                                            isDirectory = entry.isDirectory,
                                            extension = if (entry.isDirectory) "" else relativeName.substringAfterLast('.', ""),
                                            extraInfo = if (entry.isDirectory) "" else "In 7z"
                                        ))
                                        currentDirEntries.add(relativeName)
                                    }
                                } else {
                                    val folderName = relativeName.substring(0, firstSlash)
                                    if (!currentDirEntries.contains(folderName)) {
                                        results.add(FileModel(
                                            name = folderName,
                                            path = "$archivePath|/$internalPath/$folderName",
                                            size = 0,
                                            lastModified = entry.lastModifiedDate?.time ?: 0L,
                                            isDirectory = true,
                                            extraInfo = "In 7z"
                                        ))
                                        currentDirEntries.add(folderName)
                                    }
                                }
                            }
                         }
                        entry = sevenZFile.nextEntry
                    }
                }
            } else if (extension == "zip" || extension == "jar") {
                org.apache.commons.compress.archivers.zip.ZipFile(file).use { zip ->
                    val entries = zip.entries
                    val currentDirEntries = mutableSetOf<String>()

                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val entryName = entry.name.removePrefix("/")
                        
                        if (internalPath.isEmpty()) {
                            val firstSlash = entryName.indexOf('/')
                            if (firstSlash == -1) {
                                if (!currentDirEntries.contains(entryName)) {
                                    results.add(FileModel(
                                        name = entryName,
                                        path = "$archivePath|/$entryName",
                                        size = entry.size,
                                        lastModified = entry.time,
                                        isDirectory = entry.isDirectory,
                                        extension = if (entry.isDirectory) "" else entryName.substringAfterLast('.', ""),
                                        extraInfo = if (entry.isDirectory) "" else "In Zip"
                                    ))
                                    currentDirEntries.add(entryName)
                                }
                            } else {
                                val folderName = entryName.substring(0, firstSlash)
                                if (!currentDirEntries.contains(folderName)) {
                                    results.add(FileModel(
                                        name = folderName,
                                        path = "$archivePath|/$folderName",
                                        size = 0,
                                        lastModified = entry.time,
                                        isDirectory = true,
                                        extraInfo = "In Zip"
                                    ))
                                    currentDirEntries.add(folderName)
                                }
                            }
                        } else {
                            val prefix = "$internalPath/"
                            if (entryName.startsWith(prefix) && entryName != prefix) {
                                val relativeName = entryName.removePrefix(prefix)
                                val firstSlash = relativeName.indexOf('/')
                                 if (firstSlash == -1) {
                                    if (!currentDirEntries.contains(relativeName)) {
                                        results.add(FileModel(
                                            name = relativeName,
                                            path = "$archivePath|/$entryName",
                                            size = entry.size,
                                            lastModified = entry.time,
                                            isDirectory = entry.isDirectory,
                                            extension = if (entry.isDirectory) "" else relativeName.substringAfterLast('.', ""),
                                            extraInfo = if (entry.isDirectory) "" else "In Zip"
                                        ))
                                        currentDirEntries.add(relativeName)
                                    }
                                } else {
                                    val folderName = relativeName.substring(0, firstSlash)
                                     if (!currentDirEntries.contains(folderName)) {
                                        results.add(FileModel(
                                            name = folderName,
                                            path = "$archivePath|/$internalPath/$folderName",
                                            size = 0,
                                            lastModified = entry.time,
                                            isDirectory = true,
                                            extraInfo = "In Zip"
                                        ))
                                        currentDirEntries.add(folderName)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // RAR support is complex to implement purely with Commons Compress without seekable streams for random access like listing.
                // We might need to iterate stream for RAR.
                // For now, returning empty list or "Not supported for browsing" until we add stream-based listing.
            }

            results.sortedWith(compareByDescending<FileModel> { it.isDirectory }.thenBy { it.name.lowercase() })
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun createArchive(paths: List<String>, archivePath: String, onProgress: (Float) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        try {
            val destFile = File(archivePath)
            val extension = destFile.extension.lowercase()
            
            if (extension == "7z") {
                 org.apache.commons.compress.archivers.sevenz.SevenZOutputFile(destFile).use { sevenZOutput ->
                     val filesToZip = paths.map { File(it) }
                     filesToZip.forEach { file ->
                        val rootPath = file.parentFile?.absolutePath ?: ""
                        file.walkTopDown().forEach { child ->
                            val entryName = if (child.absolutePath == file.absolutePath) file.name else child.absolutePath.substring(rootPath.length + 1)
                            val entry = sevenZOutput.createArchiveEntry(child, entryName)
                            sevenZOutput.putArchiveEntry(entry)
                            if (child.isFile) {
                                child.inputStream().use { input ->
                                    val buffer = ByteArray(4096)
                                    var len = input.read(buffer)
                                    while (len > 0) {
                                        sevenZOutput.write(buffer, 0, len)
                                        len = input.read(buffer)
                                    }
                                }
                            }
                            sevenZOutput.closeArchiveEntry()
                        }
                    }
                 }
                 true
            } else {
                // Default Zip
                val filesToZip = paths.map { File(it) }
                val totalSize = filesToZip.sumOf { 
                    if (it.isDirectory) it.walkTopDown().filter { f -> f.isFile }.map { f -> f.length() }.sum() 
                    else it.length() 
                }
                var bytesProcessed = 0L

                java.util.zip.ZipOutputStream(java.io.BufferedOutputStream(java.io.FileOutputStream(destFile))).use { zos ->
                     filesToZip.forEach { file ->
                        val rootPath = file.parentFile?.absolutePath ?: ""
                        
                        file.walkTopDown().forEach { child ->
                            val entryName = child.absolutePath.substring(rootPath.length + 1)
                            if (child.isDirectory) {
                                zos.putNextEntry(java.util.zip.ZipEntry("$entryName/"))
                                zos.closeEntry()
                            } else {
                                zos.putNextEntry(java.util.zip.ZipEntry(entryName))
                                child.inputStream().use { input ->
                                    val buffer = ByteArray(4096)
                                    var len = input.read(buffer)
                                    while (len > 0) {
                                        yield()
                                        zos.write(buffer, 0, len)
                                        bytesProcessed += len
                                        onProgress(if (totalSize > 0) bytesProcessed.toFloat() / totalSize else 0f)
                                        len = input.read(buffer)
                                    }
                                }
                                zos.closeEntry()
                            }
                        }
                     }
                }
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun extractArchive(archivePath: String, destPath: String, onProgress: (Float) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        try {
            val archiveFile = File(archivePath)
            val destDir = File(destPath)
            if (!destDir.exists()) destDir.mkdirs()
            val extension = archiveFile.extension.lowercase()

            if (extension == "7z") {
                org.apache.commons.compress.archivers.sevenz.SevenZFile(archiveFile).use { sevenZFile ->
                    // Count entries for progress? 7z doesn't give total entries upfront easily without iteration.
                    // We'll just do indeterminate or 1.0 at end.
                    var entry = sevenZFile.nextEntry
                    while (entry != null) {
                        yield()
                        val entryFile = File(destDir, entry.name)
                        if (entry.isDirectory) {
                            entryFile.mkdirs()
                        } else {
                            entryFile.parentFile?.mkdirs()
                            // SevenZFile.read() reads current entry
                            val content = ByteArray(entry.size.toInt())
                            var offset = 0
                            while (offset < content.size) {
                                val read = sevenZFile.read(content, offset, content.size - offset)
                                if (read == -1) break
                                offset += read
                            }
                            entryFile.writeBytes(content)
                        }
                        entry = sevenZFile.nextEntry
                    }
                }
                onProgress(1.0f)
                true
            } else {
                // Default to Zip
                org.apache.commons.compress.archivers.zip.ZipFile(archiveFile).use { zip ->
                    val entries = zip.entries
                    val totalEntries = zip.entries.asSequence().count().toFloat()
                    var processedEntries = 0

                    while (entries.hasMoreElements()) {
                        yield()
                        val entry = entries.nextElement()
                        val entryFile = File(destDir, entry.name)
                        
                        if (entry.isDirectory) {
                            entryFile.mkdirs()
                        } else {
                            entryFile.parentFile?.mkdirs()
                            zip.getInputStream(entry).use { input ->
                                entryFile.outputStream().use { output ->
                                    val buffer = ByteArray(8192)
                                    var len = input.read(buffer)
                                    while (len > 0) {
                                        yield()
                                        output.write(buffer, 0, len)
                                        len = input.read(buffer)
                                    }
                                }
                            }
                        }
                        processedEntries++
                        onProgress(processedEntries / totalEntries)
                    }
                }
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}



