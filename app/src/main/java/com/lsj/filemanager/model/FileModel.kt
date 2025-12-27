package com.lsj.filemanager.model

import java.io.File

enum class FileCategory {
    IMAGES, VIDEOS, AUDIO, DOCUMENTS, DOWNLOADS, APPS
}

data class FileModel(
    val name: String,
    val path: String,
    val size: Long,
    val lastModified: Long,
    val isDirectory: Boolean,
    val extension: String = "",
    val mimeType: String = "",
    val itemCount: Int = 0,
    val extraInfo: String = "",
    val packageName: String? = null,
    val isSystemApp: Boolean = false,
    val appIconPackageName: String? = null // Package name for loading app icon
) {
    val isArchive: Boolean
        get() = extension.lowercase() in listOf("zip", "rar", "7z", "tar", "gz")
}

fun File.toFileModel(showHidden: Boolean = false): FileModel {
    val isDir = this.isDirectory
    return FileModel(
        name = this.name,
        path = this.absolutePath,
        size = if (isDir) 0 else this.length(),
        lastModified = this.lastModified(),
        isDirectory = isDir,
        extension = this.extension,
        itemCount = if (isDir) this.listFiles()?.count { showHidden || (!it.isHidden && !it.name.startsWith(".")) } ?: 0 else 0
    )
}

